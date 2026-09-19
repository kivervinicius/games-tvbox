package com.kiver.fireretro;
public final class ThemeProfileTest {
    public static void main(String[] args) {
        ThemeProfile profile = new ThemeProfile("x", "X", "bg", 1, 2, 3, 4, 5, 6, 120, 1, 100, "", "");
        if (profile.overlay != 90 || profile.textSize != 24 || profile.cardRadius != 32) throw new AssertionError();
        if (!"Jogos Retro".equals(profile.title) || !"Clássicos prontos para jogar".equals(profile.subtitle)) throw new AssertionError();
        if (ThemeProfile.arcade().accentColor != 0xFF38D9FF) throw new AssertionError();
        System.out.println("PASS: theme profile");
    }
}
