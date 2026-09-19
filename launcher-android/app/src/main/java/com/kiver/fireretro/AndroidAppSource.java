package com.kiver.fireretro;

import java.net.URI;

final class AndroidAppSource {
    private AndroidAppSource() { }
    static boolean isAllowed(String source) {
        try { URI uri = URI.create(source == null ? "" : source.trim()); return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null; }
        catch (Exception ignored) { return false; }
    }
    static String label(String type) { return "store".equalsIgnoreCase(type) ? "Loja oficial" : "APK disponível"; }
}
