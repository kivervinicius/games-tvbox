import { ApiError } from './errors.mjs';

const decoder = new TextDecoder();
const jwksCache = new Map();

function decodeBase64Url(value) {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
  const binary = atob(normalized + '='.repeat((4 - normalized.length % 4) % 4));
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

function decodeJsonSegment(value) {
  try { return JSON.parse(decoder.decode(decodeBase64Url(value))); }
  catch { throw new ApiError(401, 'admin_auth_invalid', 'The Cloudflare Access identity is invalid.'); }
}

async function loadJwks(issuer, env) {
  if (env.ACCESS_JWKS?.keys) return env.ACCESS_JWKS;
  const cached = jwksCache.get(issuer);
  if (cached && cached.expiresAt > Date.now()) return cached.value;
  const response = await fetch(`${issuer}/cdn-cgi/access/certs`, { cf: { cacheTtl: 300, cacheEverything: true } });
  if (!response.ok) throw new ApiError(503, 'access_keys_unavailable', 'Could not verify the administrator identity.');
  const value = await response.json();
  if (!Array.isArray(value.keys)) throw new ApiError(503, 'access_keys_invalid', 'Cloudflare Access returned an invalid signing-key set.');
  jwksCache.set(issuer, { value, expiresAt: Date.now() + 5 * 60_000 });
  return value;
}

export async function verifyAccessJwt(request, env) {
  const token = request.headers.get('cf-access-jwt-assertion');
  if (!token) throw new ApiError(401, 'admin_auth_required', 'Sign in to the private admin panel first.');
  const parts = token.split('.');
  if (parts.length !== 3) throw new ApiError(401, 'admin_auth_invalid', 'The Cloudflare Access identity is invalid.');
  const header = decodeJsonSegment(parts[0]);
  const claims = decodeJsonSegment(parts[1]);
  const team = String(env.ACCESS_TEAM_DOMAIN || '').replace(/^https?:\/\//, '').replace(/\/$/, '');
  const issuer = `https://${team}`;
  const now = Math.floor(Date.now() / 1000);
  const audiences = Array.isArray(claims.aud) ? claims.aud : [claims.aud];
  if (!team || header.alg !== 'RS256' || !header.kid || claims.iss !== issuer || !audiences.includes(env.ACCESS_AUD)
      || !Number.isFinite(claims.exp) || claims.exp <= now || (claims.nbf && claims.nbf > now + 30)) {
    throw new ApiError(401, 'admin_auth_invalid', 'The Cloudflare Access identity is expired or is for another application.');
  }
  const jwks = await loadJwks(issuer, env);
  const jwk = jwks.keys.find((key) => key.kid === header.kid && key.kty === 'RSA' && key.use !== 'enc');
  if (!jwk) throw new ApiError(401, 'admin_auth_invalid', 'The Cloudflare Access signing key was not recognized.');
  let valid = false;
  try {
    const publicKey = await crypto.subtle.importKey('jwk', jwk, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['verify']);
    valid = await crypto.subtle.verify('RSASSA-PKCS1-v1_5', publicKey, decodeBase64Url(parts[2]), new TextEncoder().encode(`${parts[0]}.${parts[1]}`));
  } catch { valid = false; }
  if (!valid) throw new ApiError(401, 'admin_auth_invalid', 'The Cloudflare Access signature did not validate.');
  const email = typeof claims.email === 'string' ? claims.email.trim().toLowerCase() : '';
  if (!email || email !== String(env.ADMIN_EMAIL || '').trim().toLowerCase()) {
    throw new ApiError(403, 'admin_forbidden', 'This account is not allowed to administer the private library.');
  }
  return { email, subject: claims.sub || '' };
}
