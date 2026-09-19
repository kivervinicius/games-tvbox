import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import { createHash } from 'node:crypto';

async function loadAdminScript() {
  const html = await readFile(new URL('../public/admin/index.html', import.meta.url), 'utf8');
  const script = html.match(/<script>([\s\S]*?)<\/script>/)?.[1];
  assert.ok(script, 'Admin panel inline script exists');
  const elements = new Map();
  const element = () => ({ className: '', textContent: '', innerHTML: '', value: '', files: [], style: {}, dataset: {}, addEventListener() {}, classList: { add() {}, remove() {}, toggle() {} }, setAttribute() {} });
  const context = {
    document: { getElementById(id) { if (!elements.has(id)) elements.set(id, element()); return elements.get(id); }, querySelectorAll() { return []; }, querySelector() { return element(); } },
    location: { search: '', href: 'https://admin.example/admin/', assign() {} },
    fetch: async () => ({ ok: false, status: 401, json: async () => ({ error: { message: 'Sign in' } }) }),
    setInterval() {}, confirm() { return true; }, XMLHttpRequest: class {}, CSS: { escape(value) { return value; } },
    console, URLSearchParams, TextEncoder, BigInt, Uint32Array, Uint8Array, ArrayBuffer
  };
  vm.createContext(context);
  vm.runInContext(script, context, { timeout: 2000 });
  await new Promise((resolve) => setTimeout(resolve, 0));
  context.__elements = elements;
  return context;
}

test('admin streamed SHA-256 matches standard digest for empty, short and multi-block files', async () => {
  const context = await loadAdminScript();
  const hash = vm.runInContext('sha256File', context);
  for (const bytes of [Buffer.alloc(0), Buffer.from('abc'), Buffer.alloc(4097, 0x5a)]) {
    const blob = new Blob([bytes]);
    assert.equal(await hash(blob), createHash('sha256').update(bytes).digest('hex'));
  }
});

test('admin text escaping prevents injected markup from entering device and catalog rows', async () => {
  const context = await loadAdminScript();
  const escape = vm.runInContext('escapeHtml', context);
  assert.equal(escape('<img src=x onerror=alert(1)>'), '&lt;img src=x onerror=alert(1)&gt;');
  vm.runInContext(`renderCatalogManagement({items:[{kind:'android-app',label:'<bad>',category:'app',packageName:'com.test.app',sourceType:'store'}]},{items:[{id:'tv-1',name:'<script>',revokedAt:null}]})`, context);
  assert.ok(context.__elements.get('androidCatalogRows').innerHTML.includes('&lt;bad&gt;'));
  assert.ok(context.__elements.get('themeAssignments').innerHTML.includes('&lt;script&gt;'));
});

test('admin platform field is a controlled combo with supported launcher platforms', async () => {
  const html = await readFile(new URL('../public/admin/index.html', import.meta.url), 'utf8');
  assert.match(html, /<select id="platform"[\s\S]*<\/select>/);
  assert.doesNotMatch(html, /<input[^>]+id="platform"/);
  for (const platform of ['NES', 'SNES', 'Mega Drive', 'GBA', 'PlayStation', 'Android']) {
    assert.match(html, new RegExp(`<option value="${platform.replace(/[+]/g, '\\+')}">`));
  }
});

test('admin refresh is manual and uses a visibility-aware ten minute interval', async () => {
  const html = await readFile(new URL('../public/admin/index.html', import.meta.url), 'utf8');
  assert.match(html, /id="refreshButton"/);
  assert.match(html, /600000/);
  assert.match(html, /document\.hidden/);
  assert.doesNotMatch(html, /setInterval\([^\n]+20000/);
});
