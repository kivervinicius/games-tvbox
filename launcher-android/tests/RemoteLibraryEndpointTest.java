package com.kiver.fireretro;

public final class RemoteLibraryEndpointTest {
    public static void main(String[] args) {
        assertEquals("https://api.github.com/repos/kivervinicius/games-tvbox-library/contents/library.manifest.json?ref=main",
                RemoteLibraryEndpoint.manifestUrl("https://raw.githubusercontent.com/kivervinicius/games-tvbox-library/main/library.manifest.json"),
                "private raw URL converts to GitHub contents API");
        assertEquals("Bearer", RemoteLibraryEndpoint.authorizationScheme("ghp_example"), "modern token scheme");
        assertEquals("https://example.org/catalog.json", RemoteLibraryEndpoint.manifestUrl("https://example.org/catalog.json"), "custom HTTPS URL preserved");
    }
    private static void assertEquals(String expected, String actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }
}
