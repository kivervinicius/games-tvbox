import { ApiError } from './errors.mjs';

const iso = () => new Date().toISOString();

/**
 * Transactional Coordinator for Games TV Box / Jogos Retro Cloud.
 * Provides atomic read-modify-write state coordination for devices, pairings,
 * content-addressed blobs, catalog revisions, quota tracking, and audit events.
 */
export class TransactionCoordinator {
  constructor(storage = new Map()) {
    this.storage = storage;
  }

  // --- Audit Logging ---
  async recordAudit({ requestId = null, actorType = 'system', actorId = 'system', action, targetType, targetId, result = 'success', revision = null, metadata = {} }) {
    const eventId = crypto.randomUUID();
    const event = {
      eventId,
      timestamp: iso(),
      requestId,
      actorType,
      actorId,
      action,
      targetType,
      targetId,
      result,
      revision,
      metadata: this._sanitizeMetadata(metadata)
    };
    const events = (await this.getJson('audit:events')) || [];
    events.unshift(event);
    if (events.length > 500) events.pop();
    await this.putJson('audit:events', events);
    return event;
  }

  async getAuditLog(limit = 100) {
    const events = (await this.getJson('audit:events')) || [];
    return events.slice(0, Math.min(limit, 500));
  }

  _sanitizeMetadata(metadata) {
    if (!metadata || typeof metadata !== 'object') return {};
    const safe = {};
    for (const [k, v] of Object.entries(metadata)) {
      if (/token|secret|password|key|auth/i.test(k)) continue;
      if (typeof v === 'string' && v.includes('r2.cloudflarestorage.com')) continue;
      safe[k] = v;
    }
    return safe;
  }

  // --- Quota & Storage Counter ---
  async getStorageStats(bucket = null) {
    let stats = await this.getJson('storage:stats');
    if (!stats) {
      stats = { usedBytes: 0, reservedBytes: 0, objectCount: 0, lastReconciledAt: null };
      await this.putJson('storage:stats', stats);
    }
    return stats;
  }

  async reserveStorage(bytes) {
    const stats = await this.getStorageStats();
    stats.reservedBytes = Math.max(0, stats.reservedBytes + bytes);
    await this.putJson('storage:stats', stats);
    return stats;
  }

  async commitStorage(bytes) {
    const stats = await this.getStorageStats();
    stats.reservedBytes = Math.max(0, stats.reservedBytes - bytes);
    stats.usedBytes = Math.max(0, stats.usedBytes + bytes);
    stats.objectCount += 1;
    await this.putJson('storage:stats', stats);
    return stats;
  }

  async releaseReservation(bytes) {
    const stats = await this.getStorageStats();
    stats.reservedBytes = Math.max(0, stats.reservedBytes - bytes);
    await this.putJson('storage:stats', stats);
    return stats;
  }

  async reconcileStorage(bucket) {
    if (!bucket || typeof bucket.list !== 'function') return await this.getStorageStats();
    let totalBytes = 0;
    let count = 0;
    let cursor;
    do {
      const page = await bucket.list({ limit: 1000, ...(cursor ? { cursor } : {}) });
      for (const obj of page.objects || []) {
        totalBytes += obj.size;
        count += 1;
      }
      cursor = page.truncated ? page.cursor : undefined;
    } while (cursor);

    const stats = await this.getStorageStats();
    const previous = { ...stats };
    stats.usedBytes = totalBytes;
    stats.objectCount = count;
    stats.lastReconciledAt = iso();
    await this.putJson('storage:stats', stats);

    await this.recordAudit({
      actorType: 'admin',
      action: 'storage.reconcile',
      targetType: 'storage',
      targetId: 'r2_inventory',
      metadata: { previousUsed: previous.usedBytes, reconciledUsed: totalBytes, objectCount: count }
    });

    return stats;
  }

  // --- Content-Addressed Blob Registry ---
  async registerBlob(sha256Hex, size, contentType, objectKey) {
    const hex = sha256Hex.toLowerCase();
    const contentId = `sha256:${hex}`;
    const existing = await this.getJson(`blob:${hex}`);
    if (existing) {
      return { ...existing, alreadyExisted: true };
    }
    const blob = {
      contentId,
      sha256: hex,
      size,
      contentType,
      objectKey,
      createdAt: iso()
    };
    await this.putJson(`blob:${hex}`, blob);
    return { ...blob, alreadyExisted: false };
  }

  async getBlob(sha256Hex) {
    return await this.getJson(`blob:${sha256Hex.toLowerCase()}`);
  }

  // --- Storage primitives (supports Map, KV, or DO SQLite) ---
  async getJson(key) {
    if (this.storage instanceof Map) {
      const val = this.storage.get(key);
      return val ? JSON.parse(val) : null;
    }
    if (typeof this.storage.get === 'function') {
      return await this.storage.get(key, 'json');
    }
    return null;
  }

  async putJson(key, value, options = {}) {
    const raw = JSON.stringify(value);
    if (this.storage instanceof Map) {
      this.storage.set(key, raw);
      return;
    }
    if (typeof this.storage.put === 'function') {
      await this.storage.put(key, raw, options);
    }
  }

  async deleteKey(key) {
    if (this.storage instanceof Map) {
      this.storage.delete(key);
      return;
    }
    if (typeof this.storage.delete === 'function') {
      await this.storage.delete(key);
    }
  }
}

/**
 * Cloudflare Durable Object implementation of LibraryCoordinator.
 * When deployed on Cloudflare Workers with Durable Object binding COORDINATOR,
 * all stateful and transactional operations execute atomically inside this coordinator.
 */
export class LibraryCoordinator {
  constructor(state, env) {
    this.state = state;
    this.env = env;
    this.coordinator = new TransactionCoordinator(state.storage);
  }

  async fetch(request) {
    const url = new URL(request.url);
    const action = url.pathname.replace(/^\//, '');

    try {
      if (action === 'status') {
        const stats = await this.coordinator.getStorageStats();
        return new Response(JSON.stringify(stats), { headers: { 'content-type': 'application/json' } });
      }
      if (action === 'reserve-storage' && request.method === 'POST') {
        const { bytes } = await request.json();
        const stats = await this.coordinator.reserveStorage(bytes);
        return new Response(JSON.stringify(stats), { headers: { 'content-type': 'application/json' } });
      }
      if (action === 'commit-storage' && request.method === 'POST') {
        const { bytes } = await request.json();
        const stats = await this.coordinator.commitStorage(bytes);
        return new Response(JSON.stringify(stats), { headers: { 'content-type': 'application/json' } });
      }
      if (action === 'release-storage' && request.method === 'POST') {
        const { bytes } = await request.json();
        const stats = await this.coordinator.releaseReservation(bytes);
        return new Response(JSON.stringify(stats), { headers: { 'content-type': 'application/json' } });
      }
      if (action === 'reconcile-storage' && request.method === 'POST') {
        const stats = await this.coordinator.reconcileStorage(this.env.PRIVATE_ASSETS);
        return new Response(JSON.stringify(stats), { headers: { 'content-type': 'application/json' } });
      }
      if (action === 'audit' && request.method === 'GET') {
        const events = await this.coordinator.getAuditLog();
        return new Response(JSON.stringify({ events }), { headers: { 'content-type': 'application/json' } });
      }
      if (action === 'record-audit' && request.method === 'POST') {
        const payload = await request.json();
        const event = await this.coordinator.recordAudit(payload);
        return new Response(JSON.stringify(event), { status: 201, headers: { 'content-type': 'application/json' } });
      }
      return new Response(JSON.stringify({ error: 'not_found' }), { status: 404, headers: { 'content-type': 'application/json' } });
    } catch (err) {
      return new Response(JSON.stringify({ error: err.message }), { status: 500, headers: { 'content-type': 'application/json' } });
    }
  }
}
