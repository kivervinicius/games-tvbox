import test from 'node:test';
import assert from 'node:assert/strict';
import worker from '../worker/src/index.mjs';
import { assertStorageBudget } from '../worker/src/quota.mjs';
import { verifyAccessJwt } from '../worker/src/access.mjs';

const webcrypto = globalThis.crypto;
const encoder = new TextEncoder();
const toBase64Url = (bytes) => Buffer.from(bytes).toString('base64url');

async function signedAccessJwt() {
  const pair = await webcrypto.subtle.generateKey({
    name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048,
    publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256'
  }, true, ['sign', 'verify']);
  const jwk = await webcrypto.subtle.exportKey('jwk', pair.publicKey);
  jwk.kid = 'test-key';
  const now = Math.floor(Date.now() / 1000);
  const head = toBase64Url(encoder.encode(JSON.stringify({ alg: 'RS256', kid: jwk.kid, typ: 'JWT' })));
  const body = toBase64Url(encoder.encode(JSON.stringify({
    iss: 'https://retro.cloudflareaccess.com', aud: ['admin-app'],
    email: 'owner@example.com', exp: now + 120, nbf: now - 5
  })));
  const signingInput = `${head}.${body}`;
  const signature = await webcrypto.subtle.sign('RSASSA-PKCS1-v1_5', pair.privateKey, encoder.encode(signingInput));
  return { jwt: `${signingInput}.${toBase64Url(signature)}`, jwks: { keys: [jwk] } };
}

test('storage budget admits exact cap and rejects overflow or invalid sizes', () => {
  assert.equal(assertStorageBudget(7_000_000_000, 1_000_000_000, 0, 8_000_000_000), 8_000_000_000);
  assert.throws(() => assertStorageBudget(7_000_000_000, 1_000_000_000, 1, 8_000_000_000), { code: 'storage_limit' });
  assert.throws(() => assertStorageBudget(0, 0, -1, 8_000_000_000), { code: 'invalid_size' });
});

test('admin Access JWT requires a valid signature, audience, issuer and allowed email', async () => {
  const { jwt, jwks } = await signedAccessJwt();
  const env = { ACCESS_TEAM_DOMAIN: 'retro.cloudflareaccess.com', ACCESS_AUD: 'admin-app', ADMIN_EMAIL: 'owner@example.com', ACCESS_JWKS: jwks };
  const request = new Request('https://service.example/api/admin/status', { headers: { 'cf-access-jwt-assertion': jwt } });
  assert.equal((await verifyAccessJwt(request, env)).email, 'owner@example.com');
  await assert.rejects(verifyAccessJwt(new Request(request.url), env), { code: 'admin_auth_required' });
  const parts = jwt.split('.'); parts[2] = `${parts[2].startsWith('A') ? 'B' : 'A'}${parts[2].slice(1)}`;
  await assert.rejects(verifyAccessJwt(new Request(request.url, { headers: { 'cf-access-jwt-assertion': parts.join('.') } }), env), { code: 'admin_auth_invalid' });
  const stranger = await signedAccessJwt();
  const denied = new Request(request.url, { headers: { 'cf-access-jwt-assertion': stranger.jwt } });
  await assert.rejects(verifyAccessJwt(denied, { ...env, ACCESS_JWKS: stranger.jwks, ADMIN_EMAIL: 'other@example.com' }), { code: 'admin_forbidden' });
});

test('worker exposes health and protects administrative routes', async () => {
  const env = { ASSETS: { fetch: async () => new Response('missing', { status: 404 }) } };
  const health = await worker.fetch(new Request('https://service.example/api/health'), env, {});
  assert.equal(health.status, 200);
  assert.equal((await health.json()).ok, true);
  const admin = await worker.fetch(new Request('https://service.example/api/admin/status'), env, {});
  assert.equal(admin.status, 401);
  assert.equal((await admin.json()).error.code, 'admin_auth_required');
});

test('public pairing links redirect to protected admin panel for authorized approval', async () => {
  const env = { ASSETS: { fetch: async () => new Response('missing', { status: 404 }) } };
  const page = await worker.fetch(new Request('https://service.example/pair/?pair=11111111-1111-4111-8111-111111111111'), env, {});
  assert.equal(page.status, 302);
  assert.equal(new URL(page.headers.get('location')).pathname, '/admin/');
  assert.equal(new URL(page.headers.get('location')).searchParams.get('pair'), '11111111-1111-4111-8111-111111111111');
});

test('administrator pairing approval is protected by admin access authentication', async () => {
  const env = { ASSETS: { fetch: async () => new Response('missing', { status: 404 }) } };
  const adminPair = await worker.fetch(new Request('https://service.example/api/admin/pairings/11111111-1111-4111-8111-111111111111/approve', { method: 'POST', body: '{}' }), env, {});
  assert.equal(adminPair.status, 401);
  assert.equal((await adminPair.json()).error.code, 'admin_auth_required');
});


test('request parser rejects malformed and oversized JSON before any write', async () => {
  const { jwt, jwks } = await signedAccessJwt();
  const env = { ACCESS_TEAM_DOMAIN: 'retro.cloudflareaccess.com', ACCESS_AUD: 'admin-app', ADMIN_EMAIL: 'owner@example.com', ACCESS_JWKS: jwks };
  const malformed = await worker.fetch(new Request('https://service.example/api/admin/uploads', { method: 'POST', headers: { 'cf-access-jwt-assertion': jwt }, body: '{' }), env, {});
  assert.equal(malformed.status, 400);
  const oversized = await worker.fetch(new Request('https://service.example/api/admin/uploads', { method: 'POST', headers: { 'cf-access-jwt-assertion': jwt, 'content-length': '200000' }, body: '{}' }), env, {});
  assert.equal(oversized.status, 413);
});
