package com.kiver.fireretro;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Logical core catalog. Cloud payloads and favorites reference a
 * {@code coreId} (e.g. {@code snes9x}); the device resolves the physical
 * {@code .so} path. No catalog entry may hardcode a device filesystem path.
 */
public final class RetroArchCoreCatalog {
    private static final Map<String, String> CORE_FILES;
    static {
        Map<String, String> cores = new LinkedHashMap<>();
        cores.put("pcsx_rearmed", "pcsx_rearmed_libretro_android.so");
        cores.put("snes9x", "snes9x_libretro_android.so");
        cores.put("snes9x_next", "snes9x_next_libretro_android.so");
        cores.put("mgba", "mgba_libretro_android.so");
        cores.put("gambatte", "gambatte_libretro_android.so");
        cores.put("nestopia", "nestopia_libretro_android.so");
        cores.put("genesis_plus_gx", "genesis_plus_gx_libretro_android.so");
        cores.put("picodrive", "picodrive_libretro_android.so");
        cores.put("fbneo", "fbneo_libretro_android.so");
        cores.put("mupen64plus_next", "mupen64plus_next_libretro_android.so");
        cores.put("parallel_n64", "parallel_n64_libretro_android.so");
        cores.put("desmume", "desmume_libretro_android.so");
        cores.put("melonds", "melonds_libretro_android.so");
        cores.put("ppsspp", "ppsspp_libretro_android.so");
        cores.put("duckstation", "duckstation_libretro_android.so");
        CORE_FILES = Collections.unmodifiableMap(cores);
    }

    private RetroArchCoreCatalog() { }

    public static boolean isKnownCoreId(String coreId) {
        return coreId != null && CORE_FILES.containsKey(coreId.trim().toLowerCase());
    }

    /** Translates a logical coreId to its .so file name, or null when unknown. */
    public static String coreFileNameFor(String coreId) {
        if (coreId == null) {
            return null;
        }
        return CORE_FILES.get(coreId.trim().toLowerCase());
    }

    /**
     * Extracts the logical coreId from a physical path or file name.
     * {@code /data/.../cores/snes9x_libretro_android.so} -&gt; {@code snes9x}.
     * Unknown names fall back to the file name without extension.
     */
    public static String parseCoreId(String corePath) {
        if (corePath == null || corePath.trim().isEmpty()) {
            return "";
        }
        String name = corePath.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        String lower = name.toLowerCase();
        String suffix = "_libretro_android.so";
        if (lower.endsWith(suffix)) {
            return lower.substring(0, lower.length() - suffix.length());
        }
        if (lower.endsWith(".so")) {
            return lower.substring(0, lower.length() - 3);
        }
        return lower;
    }

    public static Map<String, String> knownCores() {
        return CORE_FILES;
    }
}
