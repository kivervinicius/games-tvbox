package com.kiver.fireretro;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Migrates favorite identity from physical paths to stable content ids.
 * A favorite keyed by {@code sha256:...} (or cloudId) survives storage
 * remaps; path-only entries keep working until a cloudId is known.
 */
public final class FavoriteIdentity {
    private FavoriteIdentity() { }

    /**
     * Stable key for one entry. cloudId values already in
     * {@code sha256:...} form are used verbatim.
     */
    public static String keyFor(String path, String cloudId) {
        if (cloudId != null && !cloudId.trim().isEmpty()) {
            String id = cloudId.trim();
            return id.startsWith("sha256:") ? id : "sha256:" + id;
        }
        return "path:" + (path != null ? path : "");
    }

    public static boolean isStable(String favoriteKey) {
        return favoriteKey != null && favoriteKey.startsWith("sha256:");
    }

    /**
     * Migrates a legacy path set. Every path with a known cloudId becomes
     * stable; the rest keep their path key untouched (no loss).
     */
    public static Set<String> migrate(Set<String> legacyPaths, Map<String, String> cloudIdByPath) {
        Set<String> migrated = new LinkedHashSet<>();
        if (legacyPaths == null) {
            return migrated;
        }
        for (String path : legacyPaths) {
            if (path == null) {
                continue;
            }
            String cloudId = cloudIdByPath != null ? cloudIdByPath.get(path) : null;
            migrated.add(keyFor(path, cloudId));
        }
        return migrated;
    }
}
