import test from 'node:test';
import assert from 'node:assert/strict';
import { TransactionCoordinator } from '../worker/src/coordinator.mjs';

test('TransactionCoordinator tracks storage quota atomically without bucket scan', async () => {
  const coordinator = new TransactionCoordinator(new Map());
  const initial = await coordinator.getStorageStats();
  assert.equal(initial.usedBytes, 0);
  assert.equal(initial.reservedBytes, 0);

  // Reserve upload
  await coordinator.reserveStorage(5000);
  let stats = await coordinator.getStorageStats();
  assert.equal(stats.reservedBytes, 5000);
  assert.equal(stats.usedBytes, 0);

  // Commit upload
  await coordinator.commitStorage(5000);
  stats = await coordinator.getStorageStats();
  assert.equal(stats.reservedBytes, 0);
  assert.equal(stats.usedBytes, 5000);
  assert.equal(stats.objectCount, 1);

  // Release reservation on failure/expiry
  await coordinator.reserveStorage(2000);
  await coordinator.releaseReservation(2000);
  stats = await coordinator.getStorageStats();
  assert.equal(stats.reservedBytes, 0);
  assert.equal(stats.usedBytes, 5000);
});

test('TransactionCoordinator registers content-addressed blobs idempotently', async () => {
  const coordinator = new TransactionCoordinator(new Map());
  const sha = '1234567890abcdef'.repeat(4);
  const first = await coordinator.registerBlob(sha, 4096, 'application/octet-stream', `blobs/sha256/12/${sha}`);
  assert.equal(first.alreadyExisted, false);
  assert.equal(first.contentId, `sha256:${sha}`);

  const second = await coordinator.registerBlob(sha, 4096, 'application/octet-stream', `blobs/sha256/12/${sha}`);
  assert.equal(second.alreadyExisted, true);
  assert.equal(second.contentId, `sha256:${sha}`);
});

test('TransactionCoordinator logs audit events safely without exposing secrets', async () => {
  const coordinator = new TransactionCoordinator(new Map());
  await coordinator.recordAudit({
    actorType: 'admin',
    actorId: 'kivervinicius@gmail.com',
    action: 'pairing.approve',
    targetType: 'device',
    targetId: 'device-001',
    metadata: {
      profileId: 'ANDROID_GAMER',
      token: 'SHOULD_BE_STRIPPED',
      secretKey: 'SHOULD_BE_STRIPPED',
      deviceName: 'Gamer Phone'
    }
  });

  const events = await coordinator.getAuditLog();
  assert.equal(events.length, 1);
  assert.equal(events[0].action, 'pairing.approve');
  assert.equal(events[0].actorId, 'kivervinicius@gmail.com');
  assert.equal(events[0].metadata.deviceName, 'Gamer Phone');
  assert.equal('token' in events[0].metadata, false);
  assert.equal('secretKey' in events[0].metadata, false);
});
