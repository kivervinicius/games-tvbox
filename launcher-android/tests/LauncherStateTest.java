package com.kiver.fireretro;


public final class LauncherStateTest {
    public static void main(String[] args) {
        assertEquals(0, LauncherState.restoreIndex(-1, 99), "negative saved index");
        assertEquals(14, LauncherState.restoreIndex(14, 99), "valid saved index");
        assertEquals(0, LauncherState.restoreIndex(99, 99), "index beyond library");
        assertEquals("CONTROLE CONECTADO", LauncherState.controllerStatus(true), "connected label");
        assertEquals("LIGUE O CONTROLE", LauncherState.controllerStatus(false), "disconnected label");
        assertEquals("TODOS", LauncherState.normalizePlatform(null), "empty platform defaults to all");
        assertEquals("SNES", LauncherState.normalizePlatform("SNES"), "known platform remains selected");
        assertEquals("TODOS", LauncherState.normalizePlatform("Arcade"), "unknown platform defaults to all");
        assertEquals("APPS", LauncherState.normalizePlatform("APPS"), "Android apps category remains selectable");
        assertTrue(LauncherState.matchesFilter("Super Mario Kart", "SNES", "SNES", "mario"), "search matches inside selected platform");
        assertTrue(!LauncherState.matchesFilter("Sonic", "Mega Drive", "SNES", ""), "platform filter excludes other consoles");
        assertTrue(!LauncherState.matchesFilter("Super Mario Kart", "SNES", "SNES", "zelda"), "search excludes unrelated game");
        assertTrue(LauncherState.matchesFavorite(true, "Super Mario Kart", "mario"), "favorite search matches its game");
        assertTrue(!LauncherState.matchesFavorite(false, "Super Mario Kart", "mario"), "non-favorites stay out of favorites");
    }

    private static void assertEquals(Object expected, Object actual, String scenario) {
        if (!expected.equals(actual)) {
            throw new AssertionError(scenario + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertTrue(boolean value, String scenario) {
        if (!value) throw new AssertionError(scenario);
    }

}
