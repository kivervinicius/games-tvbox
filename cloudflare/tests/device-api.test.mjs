import test from 'node:test';
import assert from 'node:assert/strict';
import { routeAdminPairings, routeDeviceApi } from '../worker/src/device-api.mjs';
import { errorResponse } from '../worker/src/errors.mjs';
import { routeImporterApi } from '../worker/src/importer-api.mjs';

class MemoryKV {
  values = new Map();
  puts = 0;
  async get(key, type) { const value = this.values.get(key); return value == null ? null : type === 'json' ? JSON.parse(value) : value; }
  async put(key, value) { this.puts += 1; this.values.set(key, String(value)); }
  async delete(key) { this.values.delete(key); }
  async list({ prefix = '' } = {}) { return { keys: [...this.values.keys()].filter((key) => key.startsWith(prefix)).map((name) => ({ name })) }; }
}

function env() { return { DEVICE_KV: new MemoryKV(), PAIRING_KV: new MemoryKV(), CATALOG_KV: new MemoryKV() }; }
function post(url, body, headers = {}) { return new Request(`https://service.example${url}`, { method: 'POST', headers: { 'content-type': 'application/json', ...headers }, body: JSON.stringify(body) }); }
async function safe(route, request, bindings) { try { return await route(request, bindings); } catch (error) { return errorResponse(error); } }

test('TV pairing requires admin approval and creates a device-specific credential', async () => {
  const bindings = env();
  const start = await safe(routeDeviceApi, post('/api/device/pair/start', { deviceId: 'aftss-tv-001', model: 'AFTSS' }), bindings);
  assert.equal(start.status, 201);
  const startBody = await start.json();
  const { pairId, code, deviceId } = startBody;
  assert.equal(new URL(startBody.pairingUrl).origin, 'https://service.example');
  assert.equal(new URL(startBody.pairingUrl).pathname, '/pair/');
  const pending = await safe(routeDeviceApi, post('/api/device/pair/complete', { pairId, code }), bindings);
  assert.equal(pending.status, 202);
  const list = await safe(routeAdminPairings, new Request('https://service.example/api/admin/pairings'), bindings);
  const listedPair = (await list.json()).items[0];
  assert.equal(listedPair.deviceId, deviceId);
  assert.equal(listedPair.model, 'AFTSS');
  const storedPair = await bindings.PAIRING_KV.get(`pair:${pairId}`, 'json');
  assert.equal('code' in storedPair, false);
  const approved = await safe(routeAdminPairings, post(`/api/admin/pairings/${pairId}/approve`, { name: 'Sala' }), bindings);
  assert.equal(approved.status, 200);
  const complete = await safe(routeDeviceApi, post('/api/device/pair/complete', { pairId, code, model: 'AFTSS' }), bindings);
  const result = await complete.json();
  assert.equal(result.status, 'paired');
  assert.ok(result.deviceToken.length >= 40);
  assert.equal((await safe(routeDeviceApi, post('/api/device/pair/complete', { pairId, code }), bindings)).status, 410);
});

test('one-time pairing code can approve only its own pending TV without exposing a device credential', async () => {
  const bindings = env();
  const started = await (await safe(routeDeviceApi, post('/api/device/pair/start', { deviceId: 'aftss-tv-public-approval', model: 'AFTSS' }), bindings)).json();
  const approval = await safe(routeDeviceApi, post('/api/device/pair/approve', { pairId: started.pairId, code: started.code, name: 'Sala' }), bindings);
  assert.equal(approval.status, 200);
  const approvalBody = await approval.json();
  assert.equal(approvalBody.approved, true);
  assert.equal('deviceToken' in approvalBody, false);
  assert.equal((await safe(routeDeviceApi, post('/api/device/pair/approve', { pairId: started.pairId, code: 'wrong' }), bindings)).status, 409);
  const paired = await (await safe(routeDeviceApi, post('/api/device/pair/complete', { pairId: started.pairId, code: started.code, model: 'AFTSS' }), bindings)).json();
  assert.equal(paired.status, 'paired');
});

test('device API denies absent or revoked tokens and preserves published catalogue metadata', async () => {
  const bindings = env();
  assert.equal((await safe(routeDeviceApi, new Request('https://service.example/api/device/catalog'), bindings)).status, 401);
  const started = await (await safe(routeDeviceApi, post('/api/device/pair/start', { deviceId: 'aftss-tv-002' }), bindings)).json();
  await safe(routeAdminPairings, post(`/api/admin/pairings/${started.pairId}/approve`, { name: 'TV teste' }), bindings);
  const paired = await (await safe(routeDeviceApi, post('/api/device/pair/complete', { pairId: started.pairId, code: started.code }), bindings)).json();
  await bindings.CATALOG_KV.put('catalog:active', JSON.stringify({ version: 1, revision: 'r1', items: [{ id: 'item-1', label: 'Jogo', objectKey: 'assets/private.rom', size: 12 }] }));
  const request = new Request('https://service.example/api/device/catalog', { headers: { authorization: `Bearer ${paired.deviceToken}` } });
  const catalog = await safe(routeDeviceApi, request, bindings);
  const body = await catalog.json();
  assert.equal(body.items[0].label, 'Jogo');
  assert.equal('objectKey' in body.items[0], false);
  const revoke = await safe(routeAdminPairings, post(`/api/admin/devices/${paired.deviceId}/revoke`, {}), bindings);
  assert.equal(revoke.status, 200);
  assert.equal((await safe(routeDeviceApi, request, bindings)).status, 401);
});

test('paired TV receives a short-lived signed download only for a listed asset', async () => {
  const bindings = env();
  Object.assign(bindings, { R2_ACCOUNT_ID: 'account-id', R2_BUCKET_NAME: 'private-library', R2_ACCESS_KEY_ID: 'access', R2_SECRET_ACCESS_KEY: 'secret' });
  const started = await (await safe(routeDeviceApi, post('/api/device/pair/start', { deviceId: 'aftss-tv-003' }), bindings)).json();
  await safe(routeAdminPairings, post(`/api/admin/pairings/${started.pairId}/approve`, { name: 'Sala' }), bindings);
  const paired = await (await safe(routeDeviceApi, post('/api/device/pair/complete', { pairId: started.pairId, code: started.code }), bindings)).json();
  const id = '11111111-1111-4111-8111-111111111111';
  await bindings.CATALOG_KV.put('catalog:active', JSON.stringify({ revision: 'r1', items: [{ id, label: 'Teste', objectKey: 'assets/test/game.rom', kind: 'rom', size: 123, sha256: 'a'.repeat(64) }] }));
  const request = new Request(`https://service.example/api/device/download/${id}`, { headers: { authorization: `Bearer ${paired.deviceToken}` } });
  const download = await safe(routeDeviceApi, request, bindings);
  const payload = await download.json();
  assert.equal(download.status, 200);
  assert.match(payload.url, /^https:\/\/account-id\.r2\.cloudflarestorage\.com\//);
  assert.equal(payload.size, 123);
  assert.equal(payload.item.kind, 'rom');
  assert.equal('objectKey' in payload.item, false);
  assert.ok(Date.parse(payload.expiresAt) > Date.now());
  assert.equal('deviceToken' in payload, false);
  const missing = await safe(routeDeviceApi, new Request('https://service.example/api/device/download/22222222-2222-4222-8222-222222222222', { headers: { authorization: `Bearer ${paired.deviceToken}` } }), bindings);
  assert.equal(missing.status, 404);
});

test('catalog reads and download tickets never spend KV writes', async () => {
  const bindings = env();
  Object.assign(bindings, { R2_ACCOUNT_ID: 'account-id', R2_BUCKET_NAME: 'private-library', R2_ACCESS_KEY_ID: 'access', R2_SECRET_ACCESS_KEY: 'secret' });
  const started = await (await safe(routeDeviceApi, post('/api/device/pair/start', { deviceId: 'aftss-tv-read-only' }), bindings)).json();
  await safe(routeAdminPairings, post(`/api/admin/pairings/${started.pairId}/approve`, { name: 'Sala' }), bindings);
  const paired = await (await safe(routeDeviceApi, post('/api/device/pair/complete', { pairId: started.pairId, code: started.code }), bindings)).json();
  const id = '33333333-3333-4333-8333-333333333333';
  await bindings.CATALOG_KV.put('catalog:active', JSON.stringify({ revision: 'r-read-only', items: [{ id, label: 'Teste', objectKey: 'assets/test/game.rom', kind: 'rom', size: 123, sha256: 'a'.repeat(64) }] }));
  bindings.DEVICE_KV.puts = 0;
  const headers = { authorization: `Bearer ${paired.deviceToken}` };

  const catalog = await safe(routeDeviceApi, new Request('https://service.example/api/device/catalog', { headers }), bindings);
  assert.equal(catalog.status, 200);
  assert.equal(catalog.headers.get('etag'), '"r-read-only"');
  const unchanged = await safe(routeDeviceApi, new Request('https://service.example/api/device/catalog', { headers: { ...headers, 'if-none-match': '"r-read-only"' } }), bindings);
  assert.equal(unchanged.status, 304);
  assert.equal((await safe(routeDeviceApi, new Request(`https://service.example/api/device/download/${id}`, { headers }), bindings)).status, 200);
  assert.equal(bindings.DEVICE_KV.puts, 0);
});

test('identical device state is written once inside the six hour window', async () => {
  const bindings = env();
  const started = await (await safe(routeDeviceApi, post('/api/device/pair/start', { deviceId: 'aftss-tv-state-budget' }), bindings)).json();
  await safe(routeAdminPairings, post(`/api/admin/pairings/${started.pairId}/approve`, { name: 'Sala' }), bindings);
  const paired = await (await safe(routeDeviceApi, post('/api/device/pair/complete', { pairId: started.pairId, code: started.code }), bindings)).json();
  bindings.DEVICE_KV.puts = 0;
  const headers = { authorization: `Bearer ${paired.deviceToken}` };
  const state = { appVersion: '0.6.1', androidApi: 28, abi: 'armeabi-v7a', freeBytes: 123456, syncRevision: 'r1' };

  assert.equal((await safe(routeDeviceApi, post('/api/device/state', state, headers), bindings)).status, 200);
  assert.equal((await safe(routeDeviceApi, post('/api/device/state', state, headers), bindings)).status, 200);
  assert.equal(bindings.DEVICE_KV.puts, 1);
});

test('pairing rejects incorrect code and unknown routes stay closed', async () => {
  const bindings = env();
  const started = await (await safe(routeDeviceApi, post('/api/device/pair/start', {}), bindings)).json();
  assert.equal((await safe(routeDeviceApi, post('/api/device/pair/complete', { pairId: started.pairId, code: 'wrong' }), bindings)).status, 403);
  assert.equal((await safe(routeDeviceApi, new Request('https://service.example/api/device/unknown'), bindings)).status, 401);
});

test('pairing creation is throttled and one TV cannot create overlapping codes', async () => {
  const bindings = env();
  const ip = { 'cf-connecting-ip': '203.0.113.20' };
  const first = await safe(routeDeviceApi, post('/api/device/pair/start', { deviceId: 'aftss-tv-rate' }, ip), bindings);
  assert.equal(first.status, 201);
  assert.equal((await safe(routeDeviceApi, post('/api/device/pair/start', { deviceId: 'aftss-tv-rate' }, ip), bindings)).status, 409);
  for (let index = 1; index < 4; index++) {
    const result = await safe(routeDeviceApi, post('/api/device/pair/start', { deviceId: `aftss-other-${index}` }, ip), bindings);
    assert.equal(result.status, 201);
  }
  assert.equal((await safe(routeDeviceApi, post('/api/device/pair/start', { deviceId: 'aftss-extra-tv' }, ip), bindings)).status, 429);
});

test('paired importer receives a revocable publisher credential without R2 secrets', async () => {
  const bindings = env();
  bindings.RESERVATION_KV = new MemoryKV();
  bindings.PRIVATE_ASSETS = { async list() { return { objects: [], truncated: false }; } };
  bindings.MAX_PRIVATE_BYTES = '8000000000';
  const started = await (await safe(routeDeviceApi, post('/api/device/pair/start', { deviceId: 'windows-importer-001', model: 'Windows 11', clientType: 'importer' }), bindings)).json();
  await safe(routeAdminPairings, post(`/api/admin/pairings/${started.pairId}/approve`, { name: 'Notebook' }), bindings);
  const paired = await (await safe(routeDeviceApi, post('/api/device/pair/complete', { pairId: started.pairId, code: started.code, model: 'Windows 11' }), bindings)).json();
  assert.equal(paired.clientType, 'importer');
  assert.equal('r2AccessKey' in paired, false);
  const status = await safe(routeImporterApi, new Request('https://service.example/api/importer/status', { headers: { authorization: `Bearer ${paired.deviceToken}` } }), bindings);
  assert.equal(status.status, 200);
  const tvStarted = await (await safe(routeDeviceApi, post('/api/device/pair/start', { deviceId: 'aftss-not-importer' }), bindings)).json();
  await safe(routeAdminPairings, post(`/api/admin/pairings/${tvStarted.pairId}/approve`, { name: 'TV' }), bindings);
  const tv = await (await safe(routeDeviceApi, post('/api/device/pair/complete', { pairId: tvStarted.pairId, code: tvStarted.code }), bindings)).json();
  assert.equal((await safe(routeImporterApi, new Request('https://service.example/api/importer/status', { headers: { authorization: `Bearer ${tv.deviceToken}` } }), bindings)).status, 403);
});
