import test from 'node:test';
import assert from 'node:assert/strict';
import { CompatibilityEngine, CompatibilityStatus, findPlatform } from '../worker/src/compatibility.mjs';

test('findPlatform identifies systems by id, name and file extension', () => {
  assert.equal(findPlatform('snes')?.displayName, 'SNES');
  assert.equal(findPlatform('PlayStation')?.id, 'playstation');
  assert.equal(findPlatform('.chd')?.id, 'playstation');
  assert.equal(findPlatform('sonic.md')?.id, 'megadrive');
  assert.equal(findPlatform('mario.sfc')?.id, 'snes');
  assert.equal(findPlatform('zelda.nes')?.id, 'nes');
});

test('CompatibilityEngine detects insufficient storage on low-memory device', () => {
  const item = { kind: 'rom', size: 500 * 1024 * 1024, core_path: '/data/core.so' };
  const fireStickLowStorage = { freeBytes: 100 * 1024 * 1024, abis: ['armeabi-v7a'] };
  const res = CompatibilityEngine.evaluate(item, fireStickLowStorage);
  assert.equal(res.status, CompatibilityStatus.INSUFFICIENT_STORAGE);
});

test('CompatibilityEngine detects incompatible ABI for native APKs', () => {
  const arm64Apk = { kind: 'android-game', minApi: 28, abis: ['arm64-v8a'], size: 1000 };
  const arm32Device = { androidApi: 28, abis: ['armeabi-v7a'], freeBytes: 10_000_000 };
  const arm64Device = { androidApi: 31, abis: ['arm64-v8a', 'armeabi-v7a'], freeBytes: 10_000_000 };

  const resArm32 = CompatibilityEngine.evaluate(arm64Apk, arm32Device);
  assert.equal(resArm32.status, CompatibilityStatus.INCOMPATIBLE_ABI);

  const resArm64 = CompatibilityEngine.evaluate(arm64Apk, arm64Device);
  assert.equal(resArm64.status, CompatibilityStatus.SUPPORTED);
});

test('CompatibilityEngine detects outdated Android API level', () => {
  const modernApp = { kind: 'android-app', minApi: 30, abis: ['arm64-v8a'] };
  const oldTv = { androidApi: 28, abis: ['arm64-v8a'], freeBytes: 10_000_000 };
  const res = CompatibilityEngine.evaluate(modernApp, oldTv);
  assert.equal(res.status, CompatibilityStatus.INCOMPATIBLE_API);
});

test('CompatibilityEngine handles input fallback between gamepad, dpad and touch', () => {
  const gamepadGame = { kind: 'rom', core_path: '/data/core.so', requirements: { input: 'gamepad' } };
  
  // Device with touch only (e.g. tablet without controller connected) -> PARTIALLY_SUPPORTED
  const touchOnlyTablet = { touch: true, gamepad: false, dpad: false };
  const resTablet = CompatibilityEngine.evaluate(gamepadGame, touchOnlyTablet);
  assert.equal(resTablet.status, CompatibilityStatus.PARTIALLY_SUPPORTED);

  // Dedicated Gamer device with gamepad and touch -> SUPPORTED
  const gamerDevice = { touch: true, gamepad: true, dpad: true, abis: ['arm64-v8a'] };
  const resGamer = CompatibilityEngine.evaluate(gamepadGame, gamerDevice);
  assert.equal(resGamer.status, CompatibilityStatus.SUPPORTED);
});

test('filterCatalogForDevice decorates items with compatibility results', () => {
  const catalog = {
    revision: 'rev-1',
    items: [
      { id: '1', kind: 'rom', label: 'Game 1', size: 100, core_path: '/core.so' },
      { id: '2', kind: 'android-app', label: 'App 64', minApi: 31, abis: ['arm64-v8a'], size: 100 }
    ]
  };
  const device = { androidApi: 28, abis: ['armeabi-v7a'], freeBytes: 1_000_000 };
  const decorated = CompatibilityEngine.filterCatalogForDevice(catalog, device);
  assert.equal(decorated.items[0].compatibility.status, CompatibilityStatus.SUPPORTED);
  assert.equal(decorated.items[1].compatibility.status, CompatibilityStatus.INCOMPATIBLE_API);
});
