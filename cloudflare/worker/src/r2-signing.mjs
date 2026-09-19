const encoder = new TextEncoder();

function hex(bytes) { return [...new Uint8Array(bytes)].map((value) => value.toString(16).padStart(2, '0')).join(''); }
function base64(bytes) { let binary = ''; for (const value of new Uint8Array(bytes)) binary += String.fromCharCode(value); return btoa(binary); }
function encode(value) { return encodeURIComponent(value).replace(/[!'()*]/g, (char) => `%${char.charCodeAt(0).toString(16).toUpperCase()}`); }
function encodePath(path) { return path.split('/').map(encode).join('/'); }

async function digest(value) { return crypto.subtle.digest('SHA-256', typeof value === 'string' ? encoder.encode(value) : value); }
async function hmac(key, value) {
  const imported = await crypto.subtle.importKey('raw', key, { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  return new Uint8Array(await crypto.subtle.sign('HMAC', imported, encoder.encode(value)));
}
async function deriveKey(secret, date) {
  const dateKey = await hmac(encoder.encode(`AWS4${secret}`), date);
  const regionKey = await hmac(dateKey, 'auto');
  const serviceKey = await hmac(regionKey, 's3');
  return hmac(serviceKey, 'aws4_request');
}

export async function presignR2({ method, accountId, bucket, key, accessKeyId, secretAccessKey, expiresSeconds, checksumSha256 = '' }, now = new Date()) {
  if (!['GET', 'PUT'].includes(method) || !accountId || !bucket || !key || !accessKeyId || !secretAccessKey) throw new Error('Incomplete R2 signing configuration.');
  const expires = Math.min(3600, Math.max(30, Math.floor(expiresSeconds)));
  const amzDate = now.toISOString().replace(/[:-]|\.\d{3}/g, '');
  const shortDate = amzDate.slice(0, 8);
  const scope = `${shortDate}/auto/s3/aws4_request`;
  const host = `${accountId}.r2.cloudflarestorage.com`;
  const objectPath = `/${encode(bucket)}/${encodePath(key)}`;
  const signedHeaders = checksumSha256 && method === 'PUT' ? 'host;x-amz-checksum-sha256' : 'host';
  const headers = `host:${host}\n${signedHeaders.includes('x-amz-checksum-sha256') ? `x-amz-checksum-sha256:${checksumSha256}\n` : ''}`;
  const params = new URLSearchParams({
    'X-Amz-Algorithm': 'AWS4-HMAC-SHA256',
    'X-Amz-Credential': `${accessKeyId}/${scope}`,
    'X-Amz-Date': amzDate,
    'X-Amz-Expires': String(expires),
    'X-Amz-SignedHeaders': signedHeaders
  });
  const canonicalQuery = [...params.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([name, value]) => `${encode(name)}=${encode(value)}`).join('&');
  const canonicalRequest = `${method}\n${objectPath}\n${canonicalQuery}\n${headers}\n${signedHeaders}\nUNSIGNED-PAYLOAD`;
  const stringToSign = `AWS4-HMAC-SHA256\n${amzDate}\n${scope}\n${hex(await digest(canonicalRequest))}`;
  const signature = hex(await hmac(await deriveKey(secretAccessKey, shortDate), stringToSign));
  params.set('X-Amz-Signature', signature);
  return { url: `https://${host}${objectPath}?${[...params.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([name, value]) => `${encode(name)}=${encode(value)}`).join('&')}`, headers: checksumSha256 && method === 'PUT' ? { 'x-amz-checksum-sha256': checksumSha256 } : {}, expiresAt: new Date(now.getTime() + expires * 1000).toISOString() };
}

export function sha256HexToBase64(value) {
  if (!/^[a-fA-F0-9]{64}$/.test(value)) throw new Error('SHA-256 must be 64 hexadecimal characters.');
  const bytes = new Uint8Array(value.match(/../g).map((part) => Number.parseInt(part, 16)));
  return base64(bytes);
}
