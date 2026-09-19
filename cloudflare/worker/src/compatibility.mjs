export const CompatibilityStatus = Object.freeze({
  SUPPORTED: 'SUPPORTED',
  PARTIALLY_SUPPORTED: 'PARTIALLY_SUPPORTED',
  RUNTIME_MISSING: 'RUNTIME_MISSING',
  INCOMPATIBLE_ABI: 'INCOMPATIBLE_ABI',
  INCOMPATIBLE_API: 'INCOMPATIBLE_API',
  INSUFFICIENT_STORAGE: 'INSUFFICIENT_STORAGE',
  INPUT_UNAVAILABLE: 'INPUT_UNAVAILABLE',
  PACKAGE_UNAVAILABLE: 'PACKAGE_UNAVAILABLE',
  UNKNOWN: 'UNKNOWN'
});

export const PLATFORMS = Object.freeze([
  { id: 'nes', displayName: 'NES', extensions: ['.nes', '.zip', '.7z'], preferredFormat: '.nes', defaultCore: 'fceumm_libretro_android.so' },
  { id: 'snes', displayName: 'SNES', extensions: ['.sfc', '.smc', '.zip', '.7z'], preferredFormat: '.sfc', defaultCore: 'snes9x2010_libretro_android.so' },
  { id: 'megadrive', displayName: 'Mega Drive', extensions: ['.md', '.bin', '.gen', '.smd'], preferredFormat: '.md', defaultCore: 'genesis_plus_gx_libretro_android.so' },
  { id: 'gba', displayName: 'Game Boy Advance', extensions: ['.gba', '.zip'], preferredFormat: '.gba', defaultCore: 'mgba_libretro_android.so' },
  { id: 'playstation', displayName: 'PlayStation', extensions: ['.chd', '.cue', '.bin', '.iso', '.pbp'], preferredFormat: '.chd', defaultCore: 'pcsx_rearmed_libretro_android.so' },
  { id: 'n64', displayName: 'Nintendo 64', extensions: ['.z64', '.n64', '.v64'], preferredFormat: '.z64', defaultCore: 'mupen64plus_next_libretro_android.so' },
  { id: 'android', displayName: 'Android', extensions: ['.apk'], preferredFormat: '.apk', defaultCore: null }
]);

export function findPlatform(identifierOrExtension) {
  if (!identifierOrExtension) return null;
  const query = identifierOrExtension.trim().toLowerCase();
  return PLATFORMS.find(p => p.id === query || p.displayName.toLowerCase() === query || p.extensions.some(ext => ext.toLowerCase() === query || query.endsWith(ext.toLowerCase()))) || null;
}

export class CompatibilityEngine {
  static evaluate(item, capabilities = {}) {
    if (!item) return { status: CompatibilityStatus.UNKNOWN, reasons: ['No item provided'] };
    if (!capabilities || typeof capabilities !== 'object') {
      return { status: CompatibilityStatus.SUPPORTED, reasons: [] };
    }

    const reasons = [];

    // 1. Storage check
    const itemSize = Number(item.size) || 0;
    const freeBytes = Number(capabilities.freeBytes);
    if (Number.isSafeInteger(freeBytes) && freeBytes > 0 && itemSize > 0) {
      if (freeBytes < itemSize * 1.1) { // 10% safety margin for installation/decompression
        return {
          status: CompatibilityStatus.INSUFFICIENT_STORAGE,
          reasons: [`Available space (${Math.floor(freeBytes / 1024 / 1024)} MB) is insufficient for item size (${Math.floor(itemSize / 1024 / 1024)} MB).`]
        };
      }
    }

    // 2. Android APK Compatibility (Native Games / Apps / Launcher Updates)
    if (['android-app', 'android-game', 'launcher'].includes(item.kind)) {
      // API Level
      const minApi = Number(item.minApi) || 28;
      const deviceApi = Number(capabilities.androidApi);
      if (Number.isInteger(deviceApi) && deviceApi > 0 && deviceApi < minApi) {
        return {
          status: CompatibilityStatus.INCOMPATIBLE_API,
          reasons: [`Device Android API ${deviceApi} is lower than required minimum API ${minApi}.`]
        };
      }

      // ABI check
      const itemAbis = Array.isArray(item.abis) ? item.abis : [];
      const deviceAbis = Array.isArray(capabilities.abis) ? capabilities.abis : capabilities.abi ? [capabilities.abi] : [];
      if (itemAbis.length > 0 && deviceAbis.length > 0) {
        const matchesAbi = itemAbis.some(abi => deviceAbis.includes(abi));
        if (!matchesAbi) {
          return {
            status: CompatibilityStatus.INCOMPATIBLE_ABI,
            reasons: [`Device ABIs (${deviceAbis.join(', ')}) do not match item ABIs (${itemAbis.join(', ')}).`]
          };
        }
      }
    }

    // 3. Input Method Compatibility
    const requiresGamepad = item.requirements?.input === 'gamepad';
    const requiresTouch = item.requirements?.input === 'touch';
    const hasGamepad = Boolean(capabilities.gamepad);
    const hasTouch = Boolean(capabilities.touch);
    const hasDpad = Boolean(capabilities.dpad);

    if (requiresGamepad && !hasGamepad) {
      if (hasDpad || hasTouch) {
        reasons.push('Gamepad recommended for optimal gameplay; operating in fallback input mode.');
        return { status: CompatibilityStatus.PARTIALLY_SUPPORTED, reasons };
      }
      return {
        status: CompatibilityStatus.INPUT_UNAVAILABLE,
        reasons: ['Gamepad is required but unavailable on this device.']
      };
    }

    if (requiresTouch && !hasTouch) {
      return {
        status: CompatibilityStatus.INPUT_UNAVAILABLE,
        reasons: ['Touchscreen is required for this item.']
      };
    }

    // 4. Runtime / Core Requirements
    if (item.kind === 'rom') {
      const corePath = item.core_path || item.corePath;
      if (!corePath) {
        reasons.push('No Libretro core specified for this ROM; runtime verification required.');
        return { status: CompatibilityStatus.RUNTIME_MISSING, reasons };
      }
    }

    if (reasons.length > 0) {
      return { status: CompatibilityStatus.PARTIALLY_SUPPORTED, reasons };
    }

    return { status: CompatibilityStatus.SUPPORTED, reasons: [] };
  }

  /**
   * Filters and decorates a catalog for a specific device based on its capabilities.
   */
  static filterCatalogForDevice(catalog, capabilities) {
    if (!catalog || !Array.isArray(catalog.items)) return catalog;
    const items = catalog.items.map(item => {
      const evaluation = CompatibilityEngine.evaluate(item, capabilities);
      return {
        ...item,
        compatibility: evaluation
      };
    });
    return {
      ...catalog,
      items
    };
  }
}
