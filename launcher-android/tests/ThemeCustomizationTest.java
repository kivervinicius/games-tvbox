package com.kiver.fireretro;

public final class ThemeCustomizationTest {
    public static void main(String[] args) {
        ThemeCustomization defaults = ThemeCustomization.defaults("Kalel e Kath");
        if (!"Jogos do Kalel e Kath".equals(defaults.title)) throw new AssertionError("family title missing");
        ThemeCustomization safe = new ThemeCustomization("", "", "invalid", 999, -4, 999, 1);
        if (!"Jogos Retro".equals(safe.title) || safe.textSize != 76 || safe.shadow != 0 || safe.overlay != 90 || safe.cardDensity != 3 || !"#FFFFFF".equals(safe.textColor)) throw new AssertionError("theme values not clamped");
        System.out.println("PASS: theme customization defaults and validation");
    }
}
