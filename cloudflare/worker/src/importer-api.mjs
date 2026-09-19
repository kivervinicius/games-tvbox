import { adminApi } from './admin-api.mjs';
import { ApiError } from './errors.mjs';

async function sha256(value) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, '0')).join('');
}

async function requireImporter(request, env) {
  const token = (request.headers.get('authorization') || '').replace(/^Bearer\s+/i, '').trim();
  if (!token) throw new ApiError(401, 'importer_auth_required', 'Pair this importer before publishing files.');
  const deviceId = await env.DEVICE_KV.get(`token:${await sha256(token)}`);
  if (!deviceId) throw new ApiError(401, 'importer_revoked', 'This importer credential is invalid or revoked.');
  const device = await env.DEVICE_KV.get(`device:${deviceId}`, 'json');
  if (!device || device.revokedAt) throw new ApiError(401, 'importer_revoked', 'This importer has been revoked.');
  if (device.clientType !== 'importer') throw new ApiError(403, 'importer_required', 'A TV credential cannot publish library files.');
  return device;
}

export async function routeImporterApi(request, env) {
  await requireImporter(request, env);
  const url = new URL(request.url);
  url.pathname = url.pathname.replace(/^\/api\/importer\//, '/api/admin/');
  return adminApi(new Request(url, request), env);
}
