import test from 'node:test';
import assert from 'node:assert/strict';
import { errorResponse } from '../worker/src/errors.mjs';

test('Cloudflare KV daily write exhaustion becomes a clear temporary 429', async () => {
  const response = errorResponse(new Error('KV PUT failed: 429 Too Many Requests - daily limit exceeded'));
  assert.equal(response.status, 429);
  const body = await response.json();
  assert.equal(body.error.code, 'kv_daily_limit');
  assert.match(body.error.message, /21:00/);
});
