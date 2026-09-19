import test from 'node:test';
import assert from 'node:assert/strict';
import { adminApi } from '../worker/src/admin-api.mjs';

class MemoryKV {
  values = new Map();
  listCalls = 0;
  async get(key, type) { const value = this.values.get(key); return value == null ? null : type === 'json' ? JSON.parse(value) : value; }
  async put(key, value) { this.values.set(key, String(value)); }
  async delete(key) { this.values.delete(key); }
  async list({ prefix = '' } = {}) { this.listCalls += 1; return { keys: [...this.values.keys()].filter((key) => key.startsWith(prefix)).map((name) => ({ name })) }; }
}

function bindings(object) {
  return { CATALOG_KV: new MemoryKV(), RESERVATION_KV: new MemoryKV(), PRIVATE_ASSETS: { list: async () => ({ objects: [], truncated: false }), head: async () => object }, R2_ACCOUNT_ID: 'account', R2_BUCKET_NAME: 'private', R2_ACCESS_KEY_ID: 'key', R2_SECRET_ACCESS_KEY: 'secret' };
}
function post(path, body = {}) { return new Request(`https://service.example${path}`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) }); }

test('finalize compares the actual R2 SHA-256 checksum and rejects mismatch', async () => {
  const hex = 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad';
  const bytes = Uint8Array.from(hex.match(/../g), (part) => parseInt(part, 16));
  const item = { id: '11111111-1111-4111-8111-111111111111', filename: 'game.rom', size: 3, sha256: hex, objectKey: 'assets/id/game.rom', status: 'reserved', expiresAt: Date.now() + 60_000 };
  const env = bindings({ size: 3, checksums: { sha256: bytes.buffer } });
  await env.RESERVATION_KV.put(`upload:${item.id}`, JSON.stringify(item));
  const result = await adminApi(post(`/api/admin/uploads/${item.id}/finalize`), env);
  assert.equal((await result.json()).verified, true);

  const badEnv = bindings({ size: 3, checksums: { sha256: new Uint8Array(32).buffer } });
  await badEnv.RESERVATION_KV.put(`upload:${item.id}`, JSON.stringify(item));
  await assert.rejects(adminApi(post(`/api/admin/uploads/${item.id}/finalize`), badEnv), { code: 'hash_mismatch' });
});

test('publication is unavailable until all files are verified', async () => {
  const env = bindings(null);
  await assert.rejects(adminApi(post('/api/admin/publications', { uploads: [{ uploadId: 'missing', item: {} }] }), env), { code: 'upload_not_verified' });
  assert.equal(await env.CATALOG_KV.get('catalog:active'), null);
});

test('an expired upload reservation cannot be finalized', async () => {
  const env = bindings({ size: 3, checksums: { sha256: new Uint8Array(32).buffer } });
  const id = '11111111-1111-4111-8111-111111111111';
  await env.RESERVATION_KV.put(`upload:${id}`, JSON.stringify({ id, objectKey: 'assets/expired.rom', size: 3, sha256: 'a'.repeat(64), expiresAt: Date.now() - 1000 }));
  await assert.rejects(adminApi(post(`/api/admin/uploads/${id}/finalize`), env), { code: 'upload_expired' });
});

test('launcher publication enforces the trusted signer and a monotonically increasing version', async () => {
  const env = bindings(null);
  const signer = 'a'.repeat(64), uploadId = '22222222-2222-4222-8222-222222222222';
  env.EXPECTED_SIGNER_SHA256 = signer;
  await env.CATALOG_KV.put('catalog:active', JSON.stringify({ revision: 'old', items: [{ id: 'old-apk', kind: 'launcher', versionCode: 8 }] }));
  await env.RESERVATION_KV.put(`upload:${uploadId}`, JSON.stringify({ id: uploadId, filename: 'launcher.apk', size: 10, sha256: 'b'.repeat(64), objectKey: `assets/${uploadId}/launcher.apk`, status: 'verified' }));
  const item = { kind: 'launcher', category: 'update', label: 'Jogos Retro 0.5.11', packageName: 'com.kiver.fireretro', versionCode: 9, certificateSha256: signer };
  await assert.rejects(adminApi(post('/api/admin/publications', { uploads: [{ uploadId, item: { ...item, certificateSha256: 'c'.repeat(64) } }] }), env), { code: 'signer_mismatch' });
  await assert.rejects(adminApi(post('/api/admin/publications', { uploads: [{ uploadId, item: { ...item, versionCode: 8 } }] }), env), { code: 'invalid_version' });
  const response = await adminApi(post('/api/admin/publications', { uploads: [{ uploadId, item }] }), env);
  assert.equal((await response.json()).itemCount, 2);
});

test('ROM publication requires a safe local path and an explicit installed core', async () => {
  const env = bindings(null);
  const uploadId = '33333333-3333-4333-8333-333333333333';
  await env.RESERVATION_KV.put(`upload:${uploadId}`, JSON.stringify({ id: uploadId, filename: 'game.nes', size: 10, sha256: 'd'.repeat(64), objectKey: `assets/${uploadId}/game.nes`, status: 'verified' }));
  const item = { kind: 'rom', category: 'game', label: 'Test Game', localPath: '/sdcard/roms/nes/game.nes', corePath: '/data/user/0/com.retroarch.ra32/cores/fceumm_libretro_android.so' };
  await assert.rejects(adminApi(post('/api/admin/publications', { uploads: [{ uploadId, item: { ...item, localPath: '/sdcard/roms/../Android/data/file' } }] }), env), { code: 'invalid_local_path' });
  await assert.rejects(adminApi(post('/api/admin/publications', { uploads: [{ uploadId, item: { ...item, corePath: '/data/../system/core.so' } }] }), env), { code: 'invalid_core_path' });
  const response = await adminApi(post('/api/admin/publications', { uploads: [{ uploadId, item }] }), env);
  const catalog = await env.CATALOG_KV.get('catalog:active', 'json');
  assert.equal((await response.json()).itemCount, 1);
  assert.equal(catalog.items[0].path, item.localPath);
  assert.equal(catalog.items[0].core_path, item.corePath);
});

test('admin status lists upload reservations only once', async () => {
  const env = bindings(null);
  await env.RESERVATION_KV.put('upload:one', JSON.stringify({ id: 'one', size: 12, expiresAt: Date.now() + 60_000 }));
  const response = await adminApi(new Request('https://service.example/api/admin/status'), env);
  assert.equal(response.status, 200);
  assert.equal(env.RESERVATION_KV.listCalls, 1);
});
