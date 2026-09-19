package com.kiver.fireretro;

import java.util.LinkedHashMap;
import java.util.Map;

/** Small, device-local theme catalog. Remote themes can use the same fields. */
final class ThemeCatalog {
    static final class Theme {
        final String id, name, background, accent, cardLayout, scale, backgroundColor;
        final ThemeCustomization customization;
        final int overlay;
        Theme(String id, String name, String background, String accent, int overlay, String cardLayout, String scale) {
            this(id, name, background, accent, overlay, cardLayout, scale, null, "#07183A");
        }
        Theme(String id, String name, String background, String accent, int overlay, String cardLayout, String scale, ThemeCustomization customization) {
            this(id, name, background, accent, overlay, cardLayout, scale, customization, "#07183A");
        }
        Theme(String id, String name, String background, String accent, int overlay, String cardLayout, String scale, ThemeCustomization customization, String backgroundColor) {
            this.id = id; this.name = name; this.background = background; this.accent = accent;
            this.overlay = safeOverlay(overlay); this.cardLayout = cardLayout; this.scale = safeScale(scale); this.customization = customization; this.backgroundColor = safeColor(backgroundColor, "#07183A");
        }
    }

    private final Map<String, Theme> themes = new LinkedHashMap<>();
    static ThemeCatalog builtIns() {
        ThemeCatalog result = new ThemeCatalog();
        result.add(new Theme("arcade-moderno", "Arcade moderno", "background_arcade", "#38D9FF", 68, "compact", "contain"));
        result.add(new Theme("kalel-kath", "Kalel e Kath", "background_kalel_kath", "#FFCF4A", 62, "compact", "contain"));
        result.add(new Theme("pixel-space", "Pixel Space", "background_space", "#A88BFF", 72, "compact", "contain"));
        result.add(new Theme("aventura", "Aventura", "background_adventure", "#70E090", 66, "wide", "contain"));
        result.add(new Theme("corrida", "Corrida", "background_racing", "#FF6A70", 70, "wide", "contain"));
        result.add(new Theme("minimalista", "Minimalista", "background_minimal", "#FFFFFF", 80, "compact", "contain"));
        return result;
    }
    private void add(Theme theme) { themes.put(theme.id, theme); }
    boolean contains(String id) { return themes.containsKey(id); }
    Theme get(String id) { return themes.get(id); }
    String[] ids() { return themes.keySet().toArray(new String[themes.size()]); }
    void addRemote(String id, String name, String background, String accent, int overlay, String cardLayout, String scale) {
        addRemote(id, name, background, accent, overlay, cardLayout, scale, null);
    }
    void addRemote(String id, String name, String background, String accent, int overlay, String cardLayout, String scale, ThemeCustomization customization) {
        addRemote(id, name, background, accent, overlay, cardLayout, scale, customization, "#07183A");
    }
    void addRemote(String id, String name, String background, String accent, int overlay, String cardLayout, String scale, ThemeCustomization customization, String backgroundColor) {
        if (id.isEmpty() || !id.matches("[a-z0-9][a-z0-9-]{1,48}")) return;
        add(new Theme(id, name == null ? id : name, background == null ? "" : background, accent == null ? "#38D9FF" : accent, overlay, cardLayout == null ? "compact" : cardLayout, scale == null ? "contain" : scale, customization, backgroundColor));
    }
    static String safeScale(String scale) { return "cover".equals(scale) ? "cover" : "contain"; }
    static int safeOverlay(int value) { return Math.max(0, Math.min(90, value)); }
    private static String safeColor(String value, String fallback) { return value != null && value.matches("#[0-9a-fA-F]{6}") ? value.toUpperCase() : fallback; }
}
