package com.kiver.fireretro;

/**
 * Pure-Java handle for a Storage Access Framework tree grant.
 *
 * <p>Holds the raw {@code content://.../tree/...} URI as an opaque string so
 * domain code never depends on {@code android.net.Uri}. The Android layer
 * persists the grant via the SAF take-persistable-permission APIs and passes
 * the URI string back; catalogs reference the logical {@code saf://} path.
 */
public final class SafLocation {
    private final String treeUri;
    private final String volumeId;
    private final String rootDocumentId;
    private final String displayName;

    private SafLocation(String treeUri, String volumeId, String rootDocumentId, String displayName) {
        this.treeUri = treeUri;
        this.volumeId = volumeId;
        this.rootDocumentId = rootDocumentId;
        this.displayName = displayName;
    }

    /**
     * Parses a tree URI string. Never throws: unparseable input yields an
     * invalid location instead of crashing library scans.
     */
    public static SafLocation parse(String treeUri, String displayName) {
        if (!isTreeUri(treeUri)) {
            return new SafLocation(treeUri != null ? treeUri : "", "", "", fallbackName(displayName));
        }
        String id = treeUri.substring(treeUri.indexOf("/tree/") + 6);
        int query = id.indexOf('?');
        if (query >= 0) {
            id = id.substring(0, query);
        }
        id = decode(id);
        String volume = "primary";
        int colon = id.indexOf(':');
        if (colon > 0) {
            volume = id.substring(0, colon);
            if (volume.trim().isEmpty()) {
                volume = "primary";
            }
        }
        return new SafLocation(treeUri, volume, id, fallbackName(displayName));
    }

    public static boolean isTreeUri(String value) {
        return value != null && value.startsWith("content://") && value.contains("/tree/");
    }

    public boolean isValid() {
        return isTreeUri(treeUri) && !volumeId.isEmpty() && !rootDocumentId.isEmpty();
    }

    /** Stable logical path for catalogs: {@code saf://volume/relative}. */
    public String toLogicalPath(String relativePath) {
        String relative = relativePath != null ? relativePath : "";
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        return "saf://" + volumeId + "/" + relative;
    }

    public String getTreeUri() { return treeUri; }
    public String getVolumeId() { return volumeId; }
    public String getRootDocumentId() { return rootDocumentId; }
    public String getDisplayName() { return displayName; }

    private static String fallbackName(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            return "Armazenamento externo";
        }
        return displayName.trim();
    }

    private static String decode(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%' && i + 2 < value.length()) {
                try {
                    out.append((char) Integer.parseInt(value.substring(i + 1, i + 3), 16));
                    i += 2;
                    continue;
                } catch (NumberFormatException ignored) { }
            }
            out.append(c);
        }
        return out.toString();
    }
}
