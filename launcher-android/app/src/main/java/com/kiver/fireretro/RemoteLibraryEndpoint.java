package com.kiver.fireretro;

import java.net.URI;

final class RemoteLibraryEndpoint {
    private RemoteLibraryEndpoint() { }
    static String manifestUrl(String configured) {
        if (configured == null) return "";
        String value = configured.trim();
        try {
            URI uri = URI.create(value);
            if (!"raw.githubusercontent.com".equalsIgnoreCase(uri.getHost())) return value;
            String[] parts = uri.getPath().split("/");
            if (parts.length < 5) return value;
            StringBuilder path = new StringBuilder("https://api.github.com/repos/").append(parts[1]).append('/').append(parts[2]).append("/contents");
            for (int i = 4; i < parts.length; i++) path.append('/').append(parts[i]);
            String branch = parts[3];
            return path.append("?ref=").append(branch).toString();
        } catch (Exception ignored) { return value; }
    }
    static String authorizationScheme(String token) { return token == null || token.trim().isEmpty() ? "" : "Bearer"; }
}
