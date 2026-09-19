package com.kiver.fireretro;

/** Complete visual profile. Layout and focus rules remain independent from the palette. */
final class ThemeProfile {
    final String id, name, background, title, subtitle;
    final int backgroundColor, surfaceColor, cardColor, textColor, accentColor, focusColor, overlay, textSize, cardRadius;

    ThemeProfile(String id, String name, String background, int backgroundColor, int surfaceColor, int cardColor, int textColor, int accentColor, int focusColor, int overlay, int textSize, int cardRadius, String title, String subtitle) {
        this.id = id == null ? "arcade-moderno" : id;
        this.name = name == null ? this.id : name;
        this.background = background == null ? "" : background;
        this.backgroundColor = backgroundColor;
        this.surfaceColor = surfaceColor;
        this.cardColor = cardColor;
        this.textColor = textColor;
        this.accentColor = accentColor;
        this.focusColor = focusColor;
        this.overlay = clamp(overlay, 0, 90);
        this.textSize = clamp(textSize, 24, 72);
        this.cardRadius = clamp(cardRadius, 4, 32);
        this.title = title == null || title.trim().isEmpty() ? "Jogos Retro" : title.trim();
        this.subtitle = subtitle == null || subtitle.trim().isEmpty() ? "Clássicos prontos para jogar" : subtitle.trim();
    }
    static ThemeProfile arcade() { return new ThemeProfile("arcade-moderno", "Arcade moderno", "background_arcade", 0xFF07183A, 0xFF102750, 0xFF132F59, 0xFFFFFFFF, 0xFF38D9FF, 0xFF38D9FF, 68, 38, 16, "Jogos Retro", "Clássicos prontos para jogar"); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
