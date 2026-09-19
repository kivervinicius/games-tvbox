package com.kiver.fireretro;

/** Maps gallery tabs to their full-screen TV pages. */
final class SettingsNavigation {
    enum Screen { HOME, CONTROLS, APPEARANCE, LIBRARY }
    enum Editor { SEARCH, REMOTE_SETTINGS, APPEARANCE_TEXT, CONTROLLER_TEST }

    private SettingsNavigation() { }

    static Screen destination(String tab) {
        if ("CONTROLES".equals(tab)) return Screen.CONTROLS;
        if ("APARÊNCIA".equals(tab)) return Screen.APPEARANCE;
        if ("BIBLIOTECA".equals(tab)) return Screen.LIBRARY;
        return Screen.HOME;
    }

    static Screen backDestination(Editor editor) {
        if (editor == Editor.REMOTE_SETTINGS) return Screen.LIBRARY;
        if (editor == Editor.APPEARANCE_TEXT) return Screen.APPEARANCE;
        if (editor == Editor.CONTROLLER_TEST) return Screen.CONTROLS;
        return Screen.HOME;
    }
}
