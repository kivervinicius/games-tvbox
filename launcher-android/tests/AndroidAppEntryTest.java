package com.kiver.fireretro;

public final class AndroidAppEntryTest {
    public static void main(String[] args) {
        AndroidAppEntry app = new AndroidAppEntry("Asphalt 8", "com.gameloft.android.ANMP.GloftA8HM", "games/asphalt8.apk", "apk", "Corrida");
        assertEquals("Asphalt 8", app.title, "title");
        assertEquals("APK disponível", app.sourceLabel(), "apk source label");
        app.setInstalled(true);
        assertEquals("Instalado", app.statusLabel(), "installed label");
        assertEquals("Corrida", app.category, "category");
    }
    private static void assertEquals(String expected, String actual, String message) { if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + ", got " + actual); }
}
