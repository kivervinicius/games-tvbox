export class ApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export function jsonResponse(value, status = 200, headers = {}) {
  const resultHeaders = new Headers(headers);
  resultHeaders.set('content-type', 'application/json; charset=utf-8');
  resultHeaders.set('cache-control', 'no-store');
  return new Response(JSON.stringify(value), { status, headers: resultHeaders });
}

export async function readJson(request, maxBytes = 128 * 1024) {
  const declared = Number(request.headers.get('content-length') || 0);
  if (declared > maxBytes) throw new ApiError(413, 'body_too_large', 'Request body exceeds the allowed size.');
  const raw = await request.text();
  if (new TextEncoder().encode(raw).byteLength > maxBytes) throw new ApiError(413, 'body_too_large', 'Request body exceeds the allowed size.');
  try {
    const value = JSON.parse(raw);
    if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('JSON object required');
    return value;
  } catch {
    throw new ApiError(400, 'invalid_json', 'A JSON object is required.');
  }
}

export function errorResponse(error) {
  const rawMessage = typeof error?.message === 'string' ? error.message : '';
  if (/(KV|key.?value).*(429|daily|limit|quota)|(429|daily).*(KV|key.?value)/i.test(rawMessage)) {
    return jsonResponse({ error: { code: 'kv_daily_limit', message: 'Limite diário temporariamente atingido. Jogos e catálogo em cache continuam disponíveis; tente novamente após 21:00 (horário de Brasília).' } }, 429);
  }
  const status = Number.isInteger(error?.status) ? error.status : 500;
  const code = typeof error?.code === 'string' ? error.code : 'internal_error';
  if (status >= 500) console.error('Worker request failed', { code, message: rawMessage, stack: error?.stack });
  const message = status < 500 && typeof error?.message === 'string' ? error.message : 'The request could not be completed.';
  return jsonResponse({ error: { code, message } }, status);
}
