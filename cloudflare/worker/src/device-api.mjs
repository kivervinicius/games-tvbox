import { ApiError, jsonResponse, readJson } from './errors.mjs';
import { presignR2 } from './r2-signing.mjs';

const PAIR_TTL = 10 * 60;
const TOKEN_TTL = 60 * 60 * 24 * 365;
const DEVICE_STATE_HEARTBEAT_MS = 6 * 60 * 60 * 1000;
const iso = () => new Date().toISOString();

export const STANDARD_SCOPES = {
  TV: ['catalog.read', 'asset.download', 'device.state.write'],
  GAMER: ['catalog.read', 'asset.download', 'device.state.write', 'apps.install', 'updates.read'],
  TABLET: ['catalog.read', 'asset.download', 'device.state.write', 'apps.install'],
  PHONE: ['catalog.read', 'asset.download', 'device.state.write', 'apps.install'],
  IMPORTER: ['catalog.read', 'asset.upload', 'catalog.publish', 'metadata.publish'],
  ADMIN: ['*']
};

export function defaultScopesForProfile(profileId, fallbackType = 'tv') {
  const norm = String(profileId || fallbackType || '').toUpperCase();
  if (norm === 'IMPORTER' || norm.includes('IMPORTER')) return [...STANDARD_SCOPES.IMPORTER];
  if (norm.includes('GAMER')) return [...STANDARD_SCOPES.GAMER];
  if (norm.includes('TABLET')) return [...STANDARD_SCOPES.TABLET];
  if (norm.includes('PHONE')) return [...STANDARD_SCOPES.PHONE];
  return [...STANDARD_SCOPES.TV];
}

export function hasScope(device, scope) {
  if (!device) return false;
  const scopes = Array.isArray(device.scopes) ? device.scopes : null;
  if (scopes) {
    return scopes.includes('*') || scopes.includes(scope);
  }
  // Backward compatibility for existing paired devices without explicit scopes array
  if (device.clientType === 'importer') {
    return STANDARD_SCOPES.IMPORTER.includes(scope);
  }
  return STANDARD_SCOPES.TV.includes(scope);
}

export function assertScope(device, scope) {
  if (!hasScope(device, scope)) {
    throw new ApiError(403, 'insufficient_scope', `This device credential lacks the required '${scope}' scope.`);
  }
}

function randomToken(bytes = 32) {
  const data = crypto.getRandomValues(new Uint8Array(bytes));
  return btoa(String.fromCharCode(...data)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

async function sha256(value) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, '0')).join('');
}

async function getJson(kv, key) { return (await kv.get(key, 'json')) || null; }

function normalizedDeviceState(body) {
  return {
    appVersion: String(body.appVersion || body.launcherVersion || '').slice(0, 40),
    androidApi: Number(body.androidApi) || null,
    abi: String(body.abi || '').slice(0, 40),
    freeBytes: Math.max(0, Number(body.freeBytes) || 0),
    syncRevision: String(body.syncRevision || body.catalogRevision || '').slice(0, 80)
  };
}

function sameDeviceState(left, right) {
  if (!left || !right) return false;
  return left.appVersion === right.appVersion && left.androidApi === right.androidApi && left.abi === right.abi && left.freeBytes === right.freeBytes && left.syncRevision === right.syncRevision;
}

async function requireDevice(request, env) {
  const token = (request.headers.get('authorization') || '').replace(/^Bearer\s+/i, '').trim();
  if (!token) throw new ApiError(401, 'device_auth_required', 'Pair this TV before accessing the private library.');
  const tokenHash = await sha256(token);
  const deviceId = await env.DEVICE_KV.get(`token:${tokenHash}`);
  if (!deviceId) throw new ApiError(401, 'device_revoked', 'This TV credential is invalid or has been revoked.');
  const device = await getJson(env.DEVICE_KV, `device:${deviceId}`);
  if (!device || device.revokedAt) throw new ApiError(401, 'device_revoked', 'This TV has been revoked. Pair it again to continue.');
  return device;
}

async function startPairing(request, env) {
  const body = await readJson(request);
  const deviceId = String(body.deviceId || crypto.randomUUID()).slice(0, 80);
  if (!/^[A-Za-z0-9._:-]{8,80}$/.test(deviceId)) throw new ApiError(400, 'invalid_device_id', 'The device identifier is invalid.');
  const minute = Math.floor(Date.now() / 60_000);
  const ip = request.headers.get('cf-connecting-ip') || 'unknown';
  const rateKey = `rate:${await sha256(ip)}:${minute}`;
  const rate = await getJson(env.PAIRING_KV, rateKey) || { count: 0 };
  if (rate.count >= 5) throw new ApiError(429, 'pairing_rate_limited', 'Too many pairing attempts. Wait one minute and try again.');
  rate.count += 1;
  await env.PAIRING_KV.put(rateKey, JSON.stringify(rate), { expirationTtl: 120 });
  const activeKey = `active:${deviceId}`;
  const activePairId = await env.PAIRING_KV.get(activeKey);
  if (activePairId) {
    const active = await getJson(env.PAIRING_KV, `pair:${activePairId}`);
    if (active && active.expiresAt > Date.now()) throw new ApiError(409, 'pairing_in_progress', 'This device already has an active pairing request.');
    await env.PAIRING_KV.delete(activeKey);
  }
  const code = randomToken(9);
  const pairId = crypto.randomUUID();
  const expiresAt = Date.now() + PAIR_TTL * 1000;
  // Security P0.1: requestedType is an informational hint from client, NOT authorization!
  const requestedType = String(body.requestedType || body.clientType || 'tv').slice(0, 40);
  const pairing = {
    id: pairId,
    deviceId,
    requestedType,
    platform: String(body.platform || 'android').slice(0, 40),
    model: String(body.model || '').slice(0, 100),
    capabilities: body.capabilities && typeof body.capabilities === 'object' ? body.capabilities : null,
    codeHash: await sha256(code),
    status: 'pending',
    createdAt: iso(),
    expiresAt
  };
  await env.PAIRING_KV.put(`pair:${pairId}`, JSON.stringify(pairing), { expirationTtl: PAIR_TTL });
  await env.PAIRING_KV.put(activeKey, pairId, { expirationTtl: PAIR_TTL });
  const adminOrigin = new URL(request.url).origin;
  return jsonResponse({
    pairId,
    deviceId,
    code,
    expiresAt,
    approvalRequired: true,
    pairingUrl: `${adminOrigin}/admin/?pair=${encodeURIComponent(pairId)}`
  }, 201);
}

async function completePairing(request, env) {
  const body = await readJson(request);
  const pairId = String(body.pairId || '');
  const pairing = await getJson(env.PAIRING_KV, `pair:${pairId}`);
  if (!pairing || pairing.expiresAt <= Date.now()) throw new ApiError(410, 'pairing_expired', 'The pairing code expired. Start pairing again on the TV.');
  if (await sha256(String(body.code || '')) !== pairing.codeHash) throw new ApiError(403, 'pairing_code_invalid', 'The pairing code does not match.');
  if (pairing.status === 'pending') return jsonResponse({ status: 'waiting_for_approval', expiresAt: pairing.expiresAt }, 202);
  if (pairing.status !== 'approved' || !pairing.token) throw new ApiError(409, 'pairing_unavailable', 'This pairing request is no longer available.');
  const token = pairing.token;
  const tokenHash = await sha256(token);
  const profileId = pairing.profileId || (pairing.clientType === 'importer' ? 'importer' : 'FIRE_TV');
  const clientType = profileId === 'importer' ? 'importer' : 'tv';
  const scopes = Array.isArray(pairing.scopes) && pairing.scopes.length > 0
    ? pairing.scopes
    : defaultScopesForProfile(profileId, clientType);

  const device = {
    id: pairing.deviceId,
    clientType,
    profileId,
    scopes,
    name: pairing.deviceName || (clientType === 'importer' ? 'Importador Windows' : 'Android TV'),
    model: String(body.model || pairing.model || '').slice(0, 100),
    platform: pairing.platform || 'android',
    capabilities: pairing.capabilities || null,
    approvedAt: pairing.approvedAt || iso(),
    lastSeenAt: iso(),
    revokedAt: null
  };
  await env.DEVICE_KV.put(`token:${tokenHash}`, device.id, { expirationTtl: TOKEN_TTL });
  await env.DEVICE_KV.put(`device:${device.id}`, JSON.stringify(device));
  await env.PAIRING_KV.delete(`pair:${pairId}`);
  await env.PAIRING_KV.delete(`active:${pairing.deviceId}`);
  return jsonResponse({
    status: 'paired',
    clientType: device.clientType,
    profileId: device.profileId,
    scopes: device.scopes,
    deviceId: device.id,
    deviceToken: token,
    device
  });
}

async function adminPairings(request, env) {
  const url = new URL(request.url);
  if (request.method === 'GET' && url.pathname === '/api/admin/pairings') {
    const page = await env.PAIRING_KV.list({ prefix: 'pair:', limit: 1000 });
    const items = [];
    for (const key of page.keys || []) {
      const item = await getJson(env.PAIRING_KV, key.name);
      if (item && item.expiresAt > Date.now()) {
        items.push({
          id: item.id,
          deviceId: item.deviceId,
          requestedType: item.requestedType || item.clientType || 'tv',
          platform: item.platform || 'android',
          model: item.model || '',
          capabilities: item.capabilities || null,
          status: item.status,
          createdAt: item.createdAt,
          expiresAt: item.expiresAt
        });
      }
    }
    return jsonResponse({ items });
  }
  const approve = url.pathname.match(/^\/api\/admin\/pairings\/([0-9a-f-]{36})\/approve$/i);
  if (request.method === 'POST' && approve) {
    const pairing = await getJson(env.PAIRING_KV, `pair:${approve[1]}`);
    if (!pairing || pairing.expiresAt <= Date.now()) throw new ApiError(404, 'pairing_not_found', 'The pairing request expired or no longer exists.');
    if (pairing.status !== 'pending') throw new ApiError(409, 'pairing_already_approved', 'This pairing request has already been handled.');
    const body = await readJson(request);
    
    // Security P0.1: Admin determines profileId and scopes!
    const profileId = String(body.profileId || (body.clientType === 'importer' ? 'importer' : pairing.requestedType === 'importer' ? 'importer' : 'FIRE_TV')).trim();
    const scopes = Array.isArray(body.scopes) && body.scopes.length > 0
      ? body.scopes.map(s => String(s).trim())
      : defaultScopesForProfile(profileId, body.clientType || pairing.requestedType);

    pairing.deviceName = String(body.name || (profileId === 'importer' ? 'Importador' : 'Android TV')).slice(0, 80);
    pairing.profileId = profileId;
    pairing.clientType = profileId === 'importer' ? 'importer' : 'tv';
    pairing.scopes = scopes;
    pairing.token = randomToken();
    pairing.status = 'approved';
    pairing.approvedAt = iso();
    await env.PAIRING_KV.put(`pair:${pairing.id}`, JSON.stringify(pairing), { expirationTtl: Math.max(1, Math.ceil((pairing.expiresAt - Date.now()) / 1000)) });
    return jsonResponse({ approved: true, deviceId: pairing.deviceId, profileId: pairing.profileId, scopes: pairing.scopes });
  }
  const revoke = url.pathname.match(/^\/api\/admin\/devices\/([A-Za-z0-9._:-]{8,80})\/revoke$/);
  if (request.method === 'POST' && revoke) {
    const device = await getJson(env.DEVICE_KV, `device:${revoke[1]}`);
    if (!device) throw new ApiError(404, 'device_not_found', 'The device was not found.');
    device.revokedAt = iso();
    await env.DEVICE_KV.put(`device:${device.id}`, JSON.stringify(device));
    const page = await env.DEVICE_KV.list({ prefix: 'token:', limit: 1000 });
    for (const key of page.keys || []) if (await env.DEVICE_KV.get(key.name) === device.id) await env.DEVICE_KV.delete(key.name);
    return jsonResponse({ revoked: true, deviceId: device.id });
  }
  const themeAssignment = url.pathname.match(/^\/api\/admin\/devices\/([A-Za-z0-9._:-]{8,80})\/theme$/);
  if (request.method === 'POST' && themeAssignment) {
    const device = await getJson(env.DEVICE_KV, `device:${themeAssignment[1]}`);
    if (!device || device.revokedAt) throw new ApiError(404, 'device_not_found', 'An active device with that identifier was not found.');
    const body = await readJson(request);
    const themeId = String(body.themeId || '').trim();
    if (themeId) {
      const catalog = await getJson(env.CATALOG_KV, 'catalog:active') || { items: [] };
      const theme = (catalog.items || []).find((item) => item.kind === 'theme' && (item.themeId === themeId || item.themeProfile?.id === themeId || item.id === themeId));
      if (!theme) throw new ApiError(404, 'theme_not_found', 'That theme is not in the current private catalog.');
      device.themeId = theme.themeId || theme.themeProfile?.id || theme.id;
    } else delete device.themeId;
    device.themeUpdatedAt = iso();
    await env.DEVICE_KV.put(`device:${device.id}`, JSON.stringify(device));
    return jsonResponse({ deviceId: device.id, themeId: device.themeId || null, updatedAt: device.themeUpdatedAt });
  }
  if (request.method === 'GET' && url.pathname === '/api/admin/devices') {
    const page = await env.DEVICE_KV.list({ prefix: 'device:', limit: 1000 });
    const items = [];
    for (const key of page.keys || []) { const device = await getJson(env.DEVICE_KV, key.name); if (device) items.push(device); }
    return jsonResponse({ items });
  }
  return null;
}

async function deviceApi(request, env) {
  const url = new URL(request.url);
  if (request.method === 'POST' && url.pathname === '/api/device/pair/start') return startPairing(request, env);
  if (request.method === 'POST' && url.pathname === '/api/device/pair/complete') return completePairing(request, env);
  // Security P0.1: Public approval route is permanently eliminated.
  if (url.pathname === '/api/device/pair/approve') {
    throw new ApiError(404, 'not_found', 'Public pairing approval is disabled. An authenticated admin must approve pairings.');
  }
  const device = await requireDevice(request, env);
  if (request.method === 'GET' && url.pathname === '/api/device/catalog') {
    assertScope(device, 'catalog.read');
    const catalog = await getJson(env.CATALOG_KV, 'catalog:active') || { version: 1, revision: null, updatedAt: null, items: [] };
    const etag = `"${catalog.revision || 'empty'}"`;
    if (request.headers.get('if-none-match') === etag) return new Response(null, { status: 304, headers: { etag, 'cache-control': 'private, max-age=60' } });
    return jsonResponse({ ...catalog, assignedThemeId: device.themeId || null, items: (catalog.items || []).map(({ objectKey, ...item }) => item) }, 200, { etag });
  }
  if (request.method === 'POST' && url.pathname === '/api/device/state') {
    assertScope(device, 'device.state.write');
    const body = await readJson(request, 32 * 1024);
    const state = normalizedDeviceState(body);
    const lastWrite = Date.parse(device.lastStateWriteAt || device.lastSeenAt || 0) || 0;
    const shouldWrite = !sameDeviceState(device.state, state) || Date.now() - lastWrite >= DEVICE_STATE_HEARTBEAT_MS;
    if (shouldWrite) {
      device.lastSeenAt = iso();
      device.lastStateWriteAt = device.lastSeenAt;
      device.state = state;
      await env.DEVICE_KV.put(`device:${device.id}`, JSON.stringify(device));
    }
    return jsonResponse({ accepted: true, written: shouldWrite, receivedAt: shouldWrite ? device.lastSeenAt : device.lastStateWriteAt || null });
  }
  const download = url.pathname.match(/^\/api\/device\/download\/([A-Za-z0-9_.:-]{8,100})$/);
  if (request.method === 'GET' && download) {
    assertScope(device, 'asset.download');
    const catalog = await getJson(env.CATALOG_KV, 'catalog:active') || { items: [] };
    const item = (catalog.items || []).find((entry) => entry.id === download[1]);
    if (!item?.objectKey) throw new ApiError(404, 'asset_not_found', 'This library file is not available.');
    const signed = await presignR2({ method: 'GET', accountId: env.R2_ACCOUNT_ID, bucket: env.R2_BUCKET_NAME, key: item.objectKey, accessKeyId: env.R2_ACCESS_KEY_ID, secretAccessKey: env.R2_SECRET_ACCESS_KEY, expiresSeconds: 300 });
    const { objectKey, ...publicItem } = item;
    return jsonResponse({ url: signed.url, expiresAt: signed.expiresAt, size: item.size, sha256: item.sha256, label: item.label, item: publicItem });
  }
  throw new ApiError(404, 'not_found', 'This device endpoint does not exist.');
}

export async function routeAdminPairings(request, env) { return adminPairings(request, env); }
export async function routeDeviceApi(request, env) { return deviceApi(request, env); }
