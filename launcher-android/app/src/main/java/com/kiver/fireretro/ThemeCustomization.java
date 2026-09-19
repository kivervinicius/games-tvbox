package com.kiver.fireretro;

/** Device-local appearance values edited from the TV. */
final class ThemeCustomization {
    final String title, subtitle, textColor;
    final int textSize, shadow, overlay, cardDensity;

    ThemeCustomization(String title, String subtitle, String textColor, int textSize, int shadow, int overlay, int cardDensity) {
        this.title = cleanText(title, "Jogos Retro");
        this.subtitle = cleanText(subtitle, "Clássicos prontos para jogar");
        this.textColor = cleanColor(textColor, "#FFFFFF");
        this.textSize = clamp(textSize, 28, 76);
        this.shadow = clamp(shadow, 0, 100);
        this.overlay = clamp(overlay, 0, 90);
        this.cardDensity = clamp(cardDensity, 3, 5);
    }

    static ThemeCustomization defaults(String themeName) {
        String title = "Kalel e Kath".equals(themeName) ? "Jogos do Kalel e Kath" : "Jogos Retro";
        return new ThemeCustomization(title, "Clássicos para jogar juntos", "#FFFFFF", 38, 70, 70, 4);
    }

    private static String cleanText(String value, String fallback) {
        if (value == null) return fallback;
        String text = value.trim();
        return text.isEmpty() ? fallback : text.substring(0, Math.min(80, text.length()));
    }
    private static String cleanColor(String value, String fallback) {
        if (value == null || !value.matches("#[0-9a-fA-F]{6}")) return fallback;
        return value.toUpperCase();
    }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
