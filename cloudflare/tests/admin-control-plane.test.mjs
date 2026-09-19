import test from 'node:test';
import assert from 'node:assert/strict';
import { adminApi } from '../worker/src/admin-api.mjs';

function createMockEnv() {
  const store = new Map();
  return {
    DEVICE_KV: {
      async get(key, type) {
        const val = store.get(key);
        if (!val) return null;
        return type === 'json' ? JSON.parse(val) : val;
      },
      async put(key, val) {
        store.set(key, typeof val === 'string' ? val : JSON.stringify(val));
      },
      async delete(key) {
        store.delete(key);
      }
    },
    CATALOG_KV: {
      async get() { return null; },
      async put() {},
    },
    RESERVATION_KV: {
      async list() { return { keys: [] }; },
      async get() { return null; }
    },
    PRIVATE_ASSETS: {
      async list() { return { objects: [], truncated: false }; }
    }
  };
}

test('admin can list and create device groups', async () => {
  const env = createMockEnv();
  const getReq = new Request('https://admin.internal/api/admin/groups', { method: 'GET' });
  const getRes = await adminApi(getReq, env);
  assert.equal(getRes.status, 200);
  const data = await getRes.json();
  assert.ok(Array.isArray(data.groups));
  assert.ok(data.groups.some(g => g.id === 'gamer'));

  const postReq = new Request('https://admin.internal/api/admin/groups', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ id: 'portateis-custom', name: 'Portáteis Custom', profile: 'ANDROID_GAMER' })
  });
  const postRes = await adminApi(postReq, env);
  assert.equal(postRes.status, 201);
  const postData = await postRes.json();
  assert.equal(postData.created, true);
  assert.equal(postData.group.id, 'portateis-custom');
});

test('admin exposes standard release channels (stable, beta, canary)', async () => {
  const env = createMockEnv();
  const req = new Request('https://admin.internal/api/admin/channels', { method: 'GET' });
  const res = await adminApi(req, env);
  assert.equal(res.status, 200);
  const data = await res.json();
  assert.deepEqual(data.channels.map(c => c.id), ['stable', 'beta', 'canary']);
});

test('admin can assign a device to group, channel and theme', async () => {
  const env = createMockEnv();
  const req = new Request('https://admin.internal/api/admin/devices/tv-sala-01/assign', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ groupId: 'sala', channel: 'beta', themeId: 'arcade-moderno' })
  });
  const res = await adminApi(req, env);
  assert.equal(res.status, 200);
  const data = await res.json();
  assert.equal(data.assigned, true);
  assert.equal(data.deviceId, 'tv-sala-01');
  assert.equal(data.groupId, 'sala');
  assert.equal(data.channel, 'beta');
  assert.equal(data.themeId, 'arcade-moderno');
});
