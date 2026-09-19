package com.kiver.fireretro;

import java.net.URI;

/** Validates the single HTTPS origin used by the private Cloudflare API. */
final class CloudApiEndpoint {
    // Fixed public origin; private data remains protected by device credentials and R2 tickets.
    static final String DEFAULT_ORIGIN = "https://jogos-retro-cloud.kivervinicius.workers.dev";
    private CloudApiEndpoint() { }

    static String configuredOrigin() { return normalize(DEFAULT_ORIGIN); }

    static String normalize(String configured) {
        if (configured == null) return "";
        String value = configured.trim();
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) return "";
            String path = uri.getPath();
            if (path != null && !path.isEmpty() && !"/".equals(path)) return "";
            return "https://" + uri.getHost().toLowerCase() + (uri.getPort() > 0 && uri.getPort() != 443 ? ":" + uri.getPort() : "");
        } catch (Exception ignored) { return ""; }
    }

    static String url(String configured, String route) {
        String origin = normalize(configured);
        if (origin.isEmpty() || route == null || !route.startsWith("/api/")) return "";
        return origin + route;
    }
}
