package com.kiver.fireretro;

public final class SettingsNavigationTest {
    public static void main(String[] args) {
        if (SettingsNavigation.destination("CONTROLES") != SettingsNavigation.Screen.CONTROLS) throw new AssertionError("controls must open its own screen");
        if (SettingsNavigation.destination("APARÊNCIA") != SettingsNavigation.Screen.APPEARANCE) throw new AssertionError("appearance must open its own screen");
        if (SettingsNavigation.destination("BIBLIOTECA") != SettingsNavigation.Screen.LIBRARY) throw new AssertionError("library must open its own screen");
        if (SettingsNavigation.destination("NES") != SettingsNavigation.Screen.HOME) throw new AssertionError("platforms must remain on the gallery");
        if (SettingsNavigation.backDestination(SettingsNavigation.Editor.SEARCH) != SettingsNavigation.Screen.HOME) throw new AssertionError("search editor back must return to gallery");
        if (SettingsNavigation.backDestination(SettingsNavigation.Editor.REMOTE_SETTINGS) != SettingsNavigation.Screen.LIBRARY) throw new AssertionError("remote settings back must return to library");
        if (SettingsNavigation.backDestination(SettingsNavigation.Editor.APPEARANCE_TEXT) != SettingsNavigation.Screen.APPEARANCE) throw new AssertionError("appearance editor back must return to appearance");
        if (SettingsNavigation.backDestination(SettingsNavigation.Editor.CONTROLLER_TEST) != SettingsNavigation.Screen.CONTROLS) throw new AssertionError("controller test back must return to controls");
        System.out.println("PASS: settings tabs route to full screens");
    }
}
