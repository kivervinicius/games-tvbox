import { ApiError } from './errors.mjs';

export const DEFAULT_STORAGE_CAP_BYTES = 8_000_000_000;

export function assertStorageBudget(usedBytes, reservedBytes, incomingBytes, capBytes = DEFAULT_STORAGE_CAP_BYTES) {
  for (const value of [usedBytes, reservedBytes, incomingBytes, capBytes]) {
    if (!Number.isSafeInteger(value) || value < 0) throw new ApiError(400, 'invalid_size', 'File sizes and storage limits must be non-negative whole bytes.');
  }
  const committed = usedBytes + reservedBytes;
  const total = committed + incomingBytes;
  if (!Number.isSafeInteger(total) || total > capBytes) throw new ApiError(413, 'storage_limit', 'This upload would exceed the private library storage limit.');
  return total;
}

export async function listPrivateAssets(bucket) {
  const items = [];
  let cursor;
  do {
    const page = await bucket.list({ prefix: 'assets/', limit: 1000, ...(cursor ? { cursor } : {}) });
    for (const object of page.objects || []) items.push({ key: object.key, size: object.size, uploaded: object.uploaded?.toISOString?.() || null });
    cursor = page.truncated ? page.cursor : undefined;
    if (items.length > 20_000) throw new ApiError(503, 'inventory_too_large', 'The asset inventory exceeds the safe free-tier scan limit.');
  } while (cursor);
  return items;
}

export async function calculateStorage(bucket) {
  const objects = await listPrivateAssets(bucket);
  return { usedBytes: objects.reduce((sum, object) => sum + object.size, 0), fileCount: objects.length, largest: [...objects].sort((a, b) => b.size - a.size).slice(0, 10) };
}
