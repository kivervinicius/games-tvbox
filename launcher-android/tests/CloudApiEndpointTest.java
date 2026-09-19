package com.kiver.fireretro;

public final class CloudApiEndpointTest {
    public static void main(String[] args) {
        eq("https://library.example.workers.dev", CloudApiEndpoint.normalize(" HTTPS://Library.Example.Workers.Dev:443/ "), "normalizes HTTPS origin");
        eq("https://library.example.workers.dev:8443", CloudApiEndpoint.normalize("https://Library.Example.Workers.Dev:8443"), "preserves nondefault port");
        eq("", CloudApiEndpoint.normalize("http://library.example.workers.dev"), "rejects HTTP");
        eq("", CloudApiEndpoint.normalize("https://user:pass@library.example.workers.dev"), "rejects user info");
        eq("", CloudApiEndpoint.normalize("https://library.example.workers.dev/admin"), "rejects path prefixes");
        eq("https://library.example.workers.dev/api/device/catalog", CloudApiEndpoint.url("https://library.example.workers.dev", "/api/device/catalog"), "builds API route");
        eq("", CloudApiEndpoint.url("https://library.example.workers.dev", "https://attacker.invalid/api/device/catalog"), "rejects absolute route injection");
    }
    private static void eq(String expected, String actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }
}
