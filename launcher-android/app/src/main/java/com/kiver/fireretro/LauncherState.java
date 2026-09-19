package com.kiver.fireretro;

final class LauncherState {
    private LauncherState() { }

    static int restoreIndex(int savedIndex, int gameCount) {
        return savedIndex >= 0 && savedIndex < gameCount ? savedIndex : 0;
    }

    static String controllerStatus(boolean connected) {
        return connected ? "CONTROLE CONECTADO" : "LIGUE O CONTROLE";
    }

    static String normalizePlatform(String platform) {
        if (platform == null) return "TODOS";
        if ("APPS".equals(platform)) return "APPS";
        for (String known : new String[]{"TODOS", "NES", "SNES", "Mega Drive", "GBA", "PlayStation"}) {
            if (known.equals(platform)) return platform;
        }
        return "TODOS";
    }

    static boolean matchesFilter(String label, String platform, String selectedPlatform, String query) {
        String selected = normalizePlatform(selectedPlatform);
        if (!"TODOS".equals(selected) && !selected.equals(platform)) return false;
        String safeQuery = query == null ? "" : query.trim().toLowerCase();
        return safeQuery.isEmpty() || (label != null && label.toLowerCase().contains(safeQuery));
    }

    static boolean matchesFavorite(boolean favorite, String label, String query) {
        if (!favorite) return false;
        String safeQuery = query == null ? "" : query.trim().toLowerCase();
        return safeQuery.isEmpty() || (label != null && label.toLowerCase().contains(safeQuery));
    }

}
