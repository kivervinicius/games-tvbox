import { ApiError, jsonResponse, readJson } from './errors.mjs';
import { calculateStorage, DEFAULT_STORAGE_CAP_BYTES, assertStorageBudget } from './quota.mjs';
import { presignR2, sha256HexToBase64 } from './r2-signing.mjs';

const MAX_SINGLE_PUT_BYTES = 5_000_000_000;
const RESERVATION_TTL_SECONDS = 60 * 60;
const ACTIVE_CATALOG_KEY = 'catalog:active';

function checksumToBase64(value) {
  if (typeof value === 'string') return value;
  if (value instanceof ArrayBuffer) return btoa(String.fromCharCode(...new Uint8Array(value)));
  if (ArrayBuffer.isView(value)) return btoa(String.fromCharCode(...new Uint8Array(value.buffer, value.byteOffset, value.byteLength)));
  return '';
}

function storageCap(env) {
  const configured = Number(env.MAX_PRIVATE_BYTES || DEFAULT_STORAGE_CAP_BYTES);
  return Number.isSafeInteger(configured) && configured > 0 ? Math.min(configured, DEFAULT_STORAGE_CAP_BYTES) : DEFAULT_STORAGE_CAP_BYTES;
}

async function getJson(kv, key, fallback = null) {
  const value = await kv.get(key, 'json');
  return value ?? fallback;
}

async function listReservations(kv) {
  const page = await kv.list({ prefix: 'upload:', limit: 1000 });
  const items = [];
  for (const key of page.keys || []) {
    const value = await getJson(kv, key.name);
    if (value?.status === 'reserved' && value.expiresAt > Date.now()) items.push(value);
  }
  return items;
}

function validateUpload(input) {
  const size = Number(input.size);
  if (!Number.isSafeInteger(size) || size <= 0 || size > MAX_SINGLE_PUT_BYTES) throw new ApiError(400, 'invalid_size', 'Choose a file between 1 byte and 5 GB.');
  if (typeof input.sha256 !== 'string' || !/^[a-fA-F0-9]{64}$/.test(input.sha256)) throw new ApiError(400, 'invalid_hash', 'A SHA-256 hash is required.');
  const filename = String(input.filename || '').trim();
  if (!filename || filename.length > 180 || /[\\/\u0000-\u001f]/.test(filename) || filename === '.' || filename === '..') throw new ApiError(400, 'invalid_filename', 'The selected filename is not allowed.');
  const kind = String(input.kind || 'rom');
  if (!['rom', 'cover', 'theme', 'android-game', 'android-app', 'launcher'].includes(kind)) throw new ApiError(400, 'invalid_kind', 'Choose a supported library item type.');
  return { filename, size, sha256: input.sha256.toLowerCase(), kind, contentType: String(input.contentType || 'application/octet-stream').slice(0, 120) };
}

async function reserveUpload(env, body) {
  const upload = validateUpload(body);
  const hexHash = upload.sha256;
  const contentId = `sha256:${hexHash}`;
  const prefix = hexHash.slice(0, 2);
  const objectKey = `blobs/sha256/${prefix}/${hexHash}`;

  const [inventory, reservations] = await Promise.all([calculateStorage(env.PRIVATE_ASSETS), listReservations(env.RESERVATION_KV)]);
  assertStorageBudget(inventory.usedBytes, reservations.reduce((sum, item) => sum + item.size, 0), upload.size, storageCap(env));
  
  // Check if content blob already exists in R2 (content-addressed deduplication)
  let existingBlob = null;
  try {
    existingBlob = await env.PRIVATE_ASSETS.head(objectKey);
  } catch {}

  const alreadyVerified = Boolean(
    existingBlob &&
    existingBlob.size === upload.size &&
    checksumToBase64(existingBlob.checksums?.sha256) === sha256HexToBase64(upload.sha256)
  );

  const id = crypto.randomUUID();
  const checksum = sha256HexToBase64(upload.sha256);
  const presigned = await presignR2({
    method: 'PUT', accountId: env.R2_ACCOUNT_ID, bucket: env.R2_BUCKET_NAME,
    key: objectKey, accessKeyId: env.R2_ACCESS_KEY_ID, secretAccessKey: env.R2_SECRET_ACCESS_KEY,
    expiresSeconds: 900, checksumSha256: checksum
  });
  const reservation = {
    id,
    contentId,
    ...upload,
    objectKey,
    status: alreadyVerified ? 'verified' : 'reserved',
    alreadyExists: alreadyVerified,
    expiresAt: Date.now() + RESERVATION_TTL_SECONDS * 1000,
    createdAt: new Date().toISOString(),
    ...(alreadyVerified ? { verifiedAt: new Date().toISOString() } : {})
  };
  await env.RESERVATION_KV.put(`upload:${id}`, JSON.stringify(reservation), { expirationTtl: RESERVATION_TTL_SECONDS });
  return {
    uploadId: id,
    contentId,
    objectId: id,
    alreadyExists: alreadyVerified,
    uploadUrl: presigned.url,
    uploadHeaders: presigned.headers,
    expiresAt: presigned.expiresAt,
    reservedBytes: upload.size,
    usedBytes: inventory.usedBytes,
    storageCapBytes: storageCap(env)
  };
}

async function finalizeUpload(env, id) {
  const reservation = await getJson(env.RESERVATION_KV, `upload:${id}`);
  if (!reservation || reservation.expiresAt <= Date.now()) throw new ApiError(404, 'upload_expired', 'This upload reservation has expired.');
  const object = await env.PRIVATE_ASSETS.head(reservation.objectKey);
  if (!object) throw new ApiError(409, 'upload_missing', 'The file has not reached private storage yet.');
  if (object.size !== reservation.size) throw new ApiError(422, 'size_mismatch', 'The uploaded file size did not match the reservation.');
  const storedChecksum = checksumToBase64(object.checksums?.sha256);
  const expectedChecksum = sha256HexToBase64(reservation.sha256);
  if (!storedChecksum || storedChecksum !== expectedChecksum) throw new ApiError(422, 'hash_mismatch', 'R2 did not confirm the expected SHA-256 checksum.');
  reservation.status = 'verified';
  reservation.verifiedAt = new Date().toISOString();
  await env.RESERVATION_KV.put(`upload:${id}`, JSON.stringify(reservation), { expirationTtl: RESERVATION_TTL_SECONDS });
  return { uploadId: id, contentId: reservation.contentId || `sha256:${reservation.sha256}`, verified: true, filename: reservation.filename, size: reservation.size, sha256: reservation.sha256, kind: reservation.kind };
}

function validateManifestItem(item, upload, expectedSigner) {
  if (!item || typeof item !== 'object' || Array.isArray(item)) throw new ApiError(400, 'invalid_item', 'Every published item must be an object.');
  const kind = String(item.kind || upload.kind);
  const category = String(item.category || (kind === 'android-app' ? 'app' : kind === 'theme' ? 'theme' : 'game'));
  if (!['rom', 'cover', 'theme', 'android-game', 'android-app', 'launcher'].includes(kind)) throw new ApiError(400, 'invalid_item', 'The item type is not supported.');
  if (!['game', 'app', 'theme', 'update', 'asset'].includes(category)) throw new ApiError(400, 'invalid_category', 'The item category is not supported.');
  const label = String(item.label || upload.filename).trim();
  if (!label || label.length > 140) throw new ApiError(400, 'invalid_label', 'Provide a short item name.');
  
  const contentId = upload.contentId || `sha256:${upload.sha256}`;
  const result = {
    id: upload.id,
    contentId,
    blobSha256: upload.sha256,
    kind, category, label, objectKey: upload.objectKey,
    size: upload.size, sha256: upload.sha256, version: String(item.version || '1'),
    platform: String(item.platform || '').slice(0, 50), visibility: item.visibility === 'public' ? 'public' : 'private',
    requirements: item.requirements && typeof item.requirements === 'object' ? item.requirements : {},
    publishedAt: new Date().toISOString()
  };
  if (kind === 'android-app' || kind === 'android-game') {
    const packageName = String(item.packageName || '');
    if (!/^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$/.test(packageName)) throw new ApiError(400, 'invalid_package', 'Android apps need a valid package name.');
    result.packageName = packageName;
    result.sourceType = 'private-apk';
    result.abis = Array.isArray(item.abis) ? item.abis.filter((abi) => ['armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'].includes(abi)) : [];
    result.certificateSha256 = String(item.certificateSha256 || '').replace(/[^a-fA-F0-9]/g, '').toLowerCase();
    if (result.certificateSha256.length !== 64) throw new ApiError(400, 'invalid_certificate', 'Add the APK signing certificate SHA-256 fingerprint.');
    result.sourceType = 'private-apk';
    result.minApi = Math.max(28, Number(item.minApi) || 28);
    result.abis = Array.isArray(item.abis) ? item.abis.filter((abi) => ['armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'].includes(abi)) : [];
  }
  if (kind === 'rom') {
    const localPath = String(item.localPath || '');
    const parts = localPath.split('/');
    if (!localPath.startsWith('/sdcard/roms/') || localPath.length > 240 || parts.some((part) => part === '.' || part === '..' || /[^A-Za-z0-9._ ()-]/.test(part))) throw new ApiError(400, 'invalid_local_path', 'ROM paths must stay inside /sdcard/roms and use safe filename characters.');
    const corePath = String(item.corePath || '');
    if (!/^\/data\/user\/0\/[A-Za-z0-9_.]+\/cores\/[A-Za-z0-9_-]+\.so$/.test(corePath)) throw new ApiError(400, 'invalid_core_path', 'Choose an installed RetroArch core path in the Android data folder.');
    result.path = localPath;
    result.core_path = corePath;
    result.coverAssetId = String(item.coverAssetId || '');
    if (result.coverAssetId && !/^[0-9a-f-]{36}$/i.test(result.coverAssetId) && !/^sha256:[a-f0-9]{64}$/i.test(result.coverAssetId)) throw new ApiError(400, 'invalid_cover', 'Select a published cover item ID.');
    result.image = String(item.image || '').trim();
    if (result.image && !/^[A-Za-z0-9._-]{1,100}$/.test(result.image)) throw new ApiError(400, 'invalid_cover', 'Cover image names may contain only letters, numbers, dots, underscores and hyphens.');
    result.year = Math.max(0, Math.min(2100, Number(item.year) || 0));
    result.tags = Array.isArray(item.tags) ? item.tags.slice(0, 20).map((tag) => String(tag).slice(0, 30)) : [];
    result.description = String(item.description || '').slice(0, 500);
  }
  if (kind === 'launcher') {
    if (result.category !== 'update') throw new ApiError(400, 'invalid_category', 'Launcher packages must use the update category.');
    result.packageName = String(item.packageName || 'com.kiver.fireretro');
    if (result.packageName !== 'com.kiver.fireretro') throw new ApiError(400, 'invalid_package', 'The launcher package name must match the installed app.');
    result.versionCode = Number(item.versionCode) || 0;
    if (!Number.isSafeInteger(result.versionCode) || result.versionCode < 1) throw new ApiError(400, 'invalid_version', 'Provide a positive Android version code.');
    result.minApi = Math.max(28, Number(item.minApi) || 28);
    result.abis = Array.isArray(item.abis) ? item.abis.filter((abi) => ['armeabi-v7a', 'arm64-v8a'].includes(abi)) : [];
    result.certificateSha256 = String(item.certificateSha256 || '').replace(/[^a-fA-F0-9]/g, '').toLowerCase();
    if (result.certificateSha256.length !== 64) throw new ApiError(400, 'invalid_certificate', 'Add the release signing certificate SHA-256 fingerprint.');
    result.notes = String(item.notes || '').slice(0, 1200);
    const trustedSigner = String(expectedSigner || '').replace(/[^a-fA-F0-9]/g, '').toLowerCase();
    if (trustedSigner.length !== 64 || result.certificateSha256 !== trustedSigner) throw new ApiError(422, 'signer_mismatch', 'The launcher certificate does not match the configured trusted signer.');
  }
  return result;
}

function contrastRatio(left, right) {
  const luminance = (hexColor) => {
    const hex = hexColor.slice(1);
    const rgb = hex.length === 8 ? hex.slice(0, 6) : hex;
    const channels = [0, 2, 4].map((offset) => parseInt(rgb.slice(offset, offset + 2), 16) / 255).map((value) => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4);
    return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722;
  };
  const first = luminance(left), second = luminance(right);
  return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
}

function validateStoreEntry(entry) {
  const kind = String(entry.kind || ''), category = String(entry.category || '');
  if (!['android-app', 'android-game'].includes(kind) || !['app', 'game'].includes(category)) throw new ApiError(400, 'invalid_item', 'Store entries must be Android apps or games.');
  const id = String(entry.id || '').trim(), label = String(entry.label || '').trim();
  if (!/^[a-z0-9][a-z0-9-]{1,62}$/.test(id) || !label || label.length > 140) throw new ApiError(400, 'invalid_item', 'Provide a simple item ID and a name up to 140 characters.');
  const storeType = String(entry.storeType || ''), packageName = String(entry.packageName || ''), storeUrl = String(entry.storeUrl || '');
  if (!['google', 'amazon'].includes(storeType) || !/^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$/.test(packageName)) throw new ApiError(400, 'invalid_item', 'Select a store and provide a valid Android package name.');
  let parsed;
  try { parsed = new URL(storeUrl); } catch { throw new ApiError(400, 'invalid_store_url', 'The store link must be a valid HTTPS URL.'); }
  if (parsed.protocol !== 'https:' || parsed.username || parsed.password) throw new ApiError(400, 'invalid_store_url', 'The store link must use HTTPS without embedded credentials.');
  if (storeType === 'google' && (parsed.hostname !== 'play.google.com' || parsed.pathname !== '/store/apps/details' || parsed.searchParams.get('id') !== packageName)) throw new ApiError(400, 'invalid_store_url', 'Use the official Google Play listing URL for this package.');
  if (storeType === 'amazon' && (!/^(www\.)?amazon\.(com|com\.br|co\.uk|de|fr|it|es|co\.jp|ca)$/.test(parsed.hostname) || !/^\/(gp\/mas\/dl\/android|dp\/)/.test(parsed.pathname))) throw new ApiError(400, 'invalid_store_url', 'Use an official Amazon Appstore or Amazon product link.');
  return { id, kind, category, label, sourceType: 'store', storeType, packageName, storeUrl: parsed.href, version: String(entry.version || '1'), minApi: Math.max(28, Number(entry.minApi) || 28), abis: Array.isArray(entry.abis) ? entry.abis.filter((abi) => ['armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'].includes(abi)) : [], requirements: entry.requirements && typeof entry.requirements === 'object' ? entry.requirements : {}, visibility: 'private', size: 0, publishedAt: new Date().toISOString() };
}

function validateThemeEntry(entry, currentItems) {
  const id = String(entry.id || '').trim(), label = String(entry.label || '').trim();
  if (!/^[a-z0-9][a-z0-9-]{1,62}$/.test(id) || !label || label.length > 100) throw new ApiError(400, 'invalid_theme', 'Provide a theme ID and a name up to 100 characters.');
  const profile = entry.themeProfile;
  if (!profile || typeof profile !== 'object' || Array.isArray(profile)) throw new ApiError(400, 'invalid_theme', 'Theme settings are missing.');
  const title = String(profile.title || ''), subtitle = String(profile.subtitle || ''), colors = profile.colors || {};
  if (title.length > 80 || subtitle.length > 120) throw new ApiError(400, 'invalid_theme', 'Theme title and subtitle are too long.');
  const color = (value) => typeof value === 'string' && /^#[a-fA-F0-9]{6}$/.test(value);
  if (!color(colors.background) || !color(colors.text) || !color(colors.accent) || contrastRatio(colors.background, colors.text) < 4.5) throw new ApiError(400, 'invalid_theme', 'Theme colors must be valid hex values and text contrast must be at least 4.5:1.');
  const density = Number(profile.density || 4), overlay = Number(profile.overlay || 0), font = String(profile.font || 'system'), layout = String(profile.layout || 'grid');
  const textSize = Number(profile.textSize || 32), shadow = Number(profile.shadow || 0), cardGap = Number(profile.cardGap || 18), cardRadius = Number(profile.cardRadius || 16);
  if (!Number.isInteger(density) || density < 2 || density > 6 || !Number.isInteger(overlay) || overlay < 0 || overlay > 90 || !['system', 'arcade', 'pixel'].includes(font) || !['grid', 'compact-grid', 'wide-hero'].includes(layout) || !Number.isInteger(textSize) || textSize < 18 || textSize > 64 || !Number.isInteger(shadow) || shadow < 0 || shadow > 100 || !Number.isInteger(cardGap) || cardGap < 8 || cardGap > 36 || !Number.isInteger(cardRadius) || cardRadius < 8 || cardRadius > 32) throw new ApiError(400, 'invalid_theme', 'Theme layout, font, text, shadow, spacing or density is outside supported limits.');
  const backgroundAssetId = String(profile.backgroundAssetId || '');
  if (backgroundAssetId && !currentItems.some((item) => (item.id === backgroundAssetId || item.contentId === backgroundAssetId) && ['cover', 'theme'].includes(item.kind))) throw new ApiError(400, 'invalid_theme', 'Choose an existing uploaded image as the theme background.');
  return { id, kind: 'theme', category: 'theme', label, themeId: id, version: String(entry.version || '1'), themeProfile: { id, title, subtitle, colors: { background: colors.background.toLowerCase(), text: colors.text.toLowerCase(), accent: colors.accent.toLowerCase() }, density, overlay, font, layout, textSize, shadow, cardGap, cardRadius, backgroundAssetId }, size: 0, visibility: 'private', publishedAt: new Date().toISOString() };
}

async function commitCatalog(env, items) {
  const revision = crypto.randomUUID();
  const manifest = { version: 1, revision, updatedAt: new Date().toISOString(), items };
  const bytes = new TextEncoder().encode(JSON.stringify(manifest)).byteLength;
  if (bytes > 2 * 1024 * 1024) throw new ApiError(413, 'manifest_too_large', 'The library manifest exceeds its size limit.');
  await env.CATALOG_KV.put(`catalog:revision:${revision}`, JSON.stringify(manifest));
  await env.CATALOG_KV.put(ACTIVE_CATALOG_KEY, JSON.stringify(manifest));
  return manifest;
}

async function createCatalogEntry(env, body) {
  if (!body.item || typeof body.item !== 'object' || Array.isArray(body.item)) throw new ApiError(400, 'invalid_item', 'Provide a catalog item.');
  const current = await getJson(env.CATALOG_KV, ACTIVE_CATALOG_KEY, { version: 1, items: [] });
  const entry = body.item.kind === 'theme' ? validateThemeEntry(body.item, current.items || []) : validateStoreEntry(body.item);
  const items = new Map((current.items || []).map((item) => [item.id, item]));
  entry.publishedAt = new Date().toISOString();
  items.set(entry.id, entry);
  const manifest = await commitCatalog(env, [...items.values()]);
  return jsonResponse({ created: true, id: entry.id, revision: manifest.revision, updatedAt: manifest.updatedAt }, 201);
}

async function publish(env, body) {
  if (!Array.isArray(body.uploads) || body.uploads.length === 0 || body.uploads.length > 100) throw new ApiError(400, 'invalid_publication', 'Select one to 100 verified files to publish.');
  const current = await getJson(env.CATALOG_KV, ACTIVE_CATALOG_KEY, { version: 1, revision: null, items: [], updatedAt: null });
  const next = new Map((current.items || []).map((item) => [item.id, item]));

  for (const entry of body.uploads) {
    const uploadId = String(entry.uploadId || '');
    const reservation = await getJson(env.RESERVATION_KV, `upload:${uploadId}`);
    if (!reservation || reservation.status !== 'verified') throw new ApiError(409, 'upload_not_verified', 'Every file must pass size and SHA-256 verification before publication.');
    const item = validateManifestItem(entry.item, reservation, env.EXPECTED_SIGNER_SHA256);
    if (item.kind === 'launcher') {
      const installed = [...next.values()].filter((existing) => existing.kind === 'launcher').reduce((highest, existing) => Math.max(highest, Number(existing.versionCode) || 0), 0);
      if (item.versionCode <= installed) throw new ApiError(409, 'invalid_version', 'Launcher version code must be higher than the current release.');
    }
    
    // Idempotent Publication: match existing item by contentId or path or packageName
    const existingEntry = [...next.entries()].find(([k, v]) => {
      if (v.contentId && item.contentId && v.contentId === item.contentId) return true;
      if (v.sha256 && item.sha256 && v.sha256 === item.sha256 && v.kind === item.kind) return true;
      if (item.kind === 'rom' && v.kind === 'rom' && v.path === item.path) return true;
      if (['android-app', 'android-game', 'launcher'].includes(item.kind) && v.packageName === item.packageName && v.kind === item.kind) return true;
      return false;
    });

    if (existingEntry) {
      const existingKey = existingEntry[0];
      item.id = existingKey;
      next.set(existingKey, { ...existingEntry[1], ...item, updatedAt: new Date().toISOString() });
    } else {
      next.set(item.id, item);
    }
  }

  const items = [...next.values()];
  const manifest = await commitCatalog(env, items);
  for (const entry of body.uploads) await env.RESERVATION_KV.delete(`upload:${entry.uploadId}`);
  return { revision: manifest.revision, itemCount: items.length, updatedAt: manifest.updatedAt };
}

export async function adminApi(request, env) {
  const url = new URL(request.url);
  if (request.method === 'GET' && url.pathname === '/api/admin/status') {
    const [storage, catalog, reservations] = await Promise.all([
      calculateStorage(env.PRIVATE_ASSETS),
      getJson(env.CATALOG_KV, ACTIVE_CATALOG_KEY, { version: 1, items: [], updatedAt: null }),
      listReservations(env.RESERVATION_KV)
    ]);
    return jsonResponse({
      storage: {
        ...storage,
        reservedBytes: reservations.reduce((sum, item) => sum + item.size, 0),
        capBytes: storageCap(env),
        freeBytes: Math.max(0, storageCap(env) - storage.usedBytes - reservations.reduce((sum, item) => sum + item.size, 0))
      },
      catalog: { revision: catalog.revision || null, itemCount: (catalog.items || []).length, updatedAt: catalog.updatedAt || null },
      pendingUploads: reservations.length
    });
  }
  if (request.method === 'GET' && url.pathname === '/api/admin/catalog') {
    return jsonResponse(await getJson(env.CATALOG_KV, ACTIVE_CATALOG_KEY, { version: 1, items: [], updatedAt: null }));
  }
  if (request.method === 'POST' && url.pathname === '/api/admin/uploads') {
    return jsonResponse(await reserveUpload(env, await readJson(request)), 201);
  }
  const finalizeMatch = url.pathname.match(/^\/api\/admin\/uploads\/([0-9a-f-]{36})\/finalize$/i);
  if (request.method === 'POST' && finalizeMatch) return jsonResponse(await finalizeUpload(env, finalizeMatch[1]));
  if (request.method === 'POST' && url.pathname === '/api/admin/publications') return jsonResponse(await publish(env, await readJson(request)), 201);
  if (request.method === 'POST' && url.pathname === '/api/admin/catalog/items') return createCatalogEntry(env, await readJson(request));
  
  if (request.method === 'POST' && url.pathname === '/api/admin/reconcile-storage') {
    const storage = await calculateStorage(env.PRIVATE_ASSETS);
    return jsonResponse({ reconciled: true, ...storage });
  }
  if (request.method === 'GET' && url.pathname === '/api/admin/audit') {
    return jsonResponse({ items: [] });
  }
  throw new ApiError(404, 'not_found', 'This admin endpoint does not exist.');
}
