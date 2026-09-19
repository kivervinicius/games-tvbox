package com.kiver.fireretro;

public final class ThemeCatalogTest {
    public static void main(String[] args) {
        ThemeCatalog catalog = ThemeCatalog.builtIns();
        assertTrue(catalog.contains("arcade-moderno"), "arcade moderno is available");
        assertTrue(catalog.contains("kalel-kath"), "Kalel e Kath is available");
        ThemeCatalog.Theme kalel = catalog.get("kalel-kath");
        assertEquals("Kalel e Kath", kalel.name, "family theme name");
        assertEquals("cover", ThemeCatalog.safeScale("cover"), "cover scale preserved");
        assertEquals("contain", ThemeCatalog.safeScale("invalid"), "invalid scale defaults to contain");
        assertEquals(70, ThemeCatalog.safeOverlay(70), "overlay preserved");
        assertEquals(90, ThemeCatalog.safeOverlay(120), "overlay capped");
        catalog.addRemote("ocean", "Oceano", "background_ocean", "#123456", 60, "compact", "contain");
        assertTrue(catalog.contains("ocean"), "remote theme is added");
        catalog.addRemote("night-arcade", "Noite Arcade", "night-arcade.img", "#FFAA33", 55, "grid", "contain", new ThemeCustomization("Jogos da Noite", "Coleção da família", "#FFFFFF", 42, 70, 55, 4));
        assertEquals("Jogos da Noite", catalog.get("night-arcade").customization.title, "remote title customization is available");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }
    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
