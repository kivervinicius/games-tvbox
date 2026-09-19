import test from 'node:test';
import assert from 'node:assert/strict';
import { adminApi } from '../worker/src/admin-api.mjs';
import { routeAdminPairings } from '../worker/src/device-api.mjs';

class MemoryKV {
  values = new Map();
  async get(key, type) { const value = this.values.get(key); return value == null ? null : type === 'json' ? JSON.parse(value) : value; }
  async put(key, value) { this.values.set(key, String(value)); }
  async delete(key) { this.values.delete(key); }
  async list({ prefix = '' } = {}) { return { keys: [...this.values.keys()].filter((key) => key.startsWith(prefix)).map((name) => ({ name })) }; }
}

const post = (path, body) => new Request(`https://service.example${path}`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });
function env() { return { CATALOG_KV: new MemoryKV(), DEVICE_KV: new MemoryKV(), RESERVATION_KV: new MemoryKV(), PRIVATE_ASSETS: { list: async () => ({ objects: [], truncated: false }) } }; }

test('admin can add a supported store app to the catalog without uploading an APK', async () => {
  const bindings = env();
  const response = await adminApi(post('/api/admin/catalog/items', { item: { id: 'puzzle-game', kind: 'android-game', category: 'game', label: 'Puzzle Game', sourceType: 'store', storeType: 'google', packageName: 'com.example.puzzle', storeUrl: 'https://play.google.com/store/apps/details?id=com.example.puzzle', minApi: 28, abis: ['arm64-v8a'] } }), bindings);
  const result = await response.json();
  assert.equal(result.created, true);
  const catalog = await bindings.CATALOG_KV.get('catalog:active', 'json');
  assert.equal(catalog.items[0].sourceType, 'store');
  assert.equal(catalog.items[0].storeUrl, 'https://play.google.com/store/apps/details?id=com.example.puzzle');
});

test('admin rejects untrusted store hosts and invalid per-theme color values', async () => {
  const bindings = env();
  await assert.rejects(adminApi(post('/api/admin/catalog/items', { item: { id: 'bad-app', kind: 'android-app', category: 'app', label: 'Unsafe', sourceType: 'store', storeType: 'google', packageName: 'com.example.unsafe', storeUrl: 'https://attacker.example/app' } }), bindings), { code: 'invalid_store_url' });
  await assert.rejects(adminApi(post('/api/admin/catalog/items', { item: { id: 'theme-test', kind: 'theme', category: 'theme', label: 'Theme', themeProfile: { id: 'theme-test', title: 'TV', subtitle: '', colors: { background: 'url(javascript:alert(1))', accent: '#112233', text: '#ffffff' }, density: 4 } } }), bindings), { code: 'invalid_theme' });
  assert.equal(await bindings.CATALOG_KV.get('catalog:active'), null);
});

test('admin stores a readable theme profile with validated layout settings', async () => {
  const bindings = env();
  const profile = { id: 'pixel-family', title: 'Jogos da família', subtitle: 'Escolha e divirta-se', colors: { background: '#101522', accent: '#ffcc55', text: '#ffffff' }, density: 4, overlay: 25, font: 'pixel', layout: 'grid', textSize: 34, shadow: 50, cardGap: 18, cardRadius: 16 };
  const response = await adminApi(post('/api/admin/catalog/items', { item: { id: 'pixel-family', kind: 'theme', label: 'Pixel família', themeProfile: profile } }), bindings);
  assert.equal(response.status, 201);
  const manifest = await bindings.CATALOG_KV.get('catalog:active', 'json');
  assert.equal(manifest.items[0].themeProfile.textSize, 34);
  assert.equal(manifest.items[0].themeProfile.colors.background, '#101522');
});

test('admin can save a per-device theme assignment and device catalog returns it', async () => {
  const bindings = env();
  const theme = { id: 'theme-arcade', kind: 'theme', category: 'theme', label: 'Arcade', themeProfile: { id: 'arcade', title: 'Arcade', subtitle: '', colors: { background: '#101522', accent: '#ffcc55', text: '#ffffff' }, density: 4 } };
  await bindings.CATALOG_KV.put('catalog:active', JSON.stringify({ revision: 'r1', items: [theme] }));
  await bindings.DEVICE_KV.put('device:tv-12345678', JSON.stringify({ id: 'tv-12345678', name: 'Quarto', revokedAt: null }));
  const assignment = await routeAdminPairings(post('/api/admin/devices/tv-12345678/theme', { themeId: 'arcade' }), bindings);
  assert.equal((await assignment.json()).themeId, 'arcade');
  const device = await bindings.DEVICE_KV.get('device:tv-12345678', 'json');
  assert.equal(device.themeId, 'arcade');
});
