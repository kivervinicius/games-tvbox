package com.kiver.fireretro;

public final class AndroidAppSourceTest {
    public static void main(String[] args) {
        assertTrue(AndroidAppSource.isAllowed("https://example.org/app.apk"), "HTTPS APK source allowed");
        assertTrue(!AndroidAppSource.isAllowed("http://example.org/app.apk"), "HTTP source rejected");
        assertTrue(!AndroidAppSource.isAllowed("file:///sdcard/app.apk"), "file URI rejected");
        assertEquals("APK disponível", AndroidAppSource.label("apk"), "APK label");
        assertEquals("Loja oficial", AndroidAppSource.label("store"), "store label");
    }
    private static void assertTrue(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void assertEquals(String expected, String actual, String message) { if (!expected.equals(actual)) throw new AssertionError(message); }
}
