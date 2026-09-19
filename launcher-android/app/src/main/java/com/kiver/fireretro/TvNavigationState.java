package com.kiver.fireretro;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pure navigation state shared by the TV shell and its tests. */
final class TvNavigationState {
    static final String[] SECTIONS = {"jogos", "favoritos", "android", "apps", "controles", "aparencia", "biblioteca"};
    static final String[] PLATFORMS = {"TODOS", "NES", "SNES", "Mega Drive", "GBA", "PlayStation"};
    private final Map<String, String> lastFocusBySection = new LinkedHashMap<>();
    private int section;
    private int platform;

    TvNavigationState() { section = 0; platform = 0; }
    int section() { return section; }
    int platform() { return platform; }
    String sectionId() { return SECTIONS[section]; }
    String platformId() { return PLATFORMS[platform]; }
    void setSection(int value) { section = wrap(value, SECTIONS.length); }
    void setPlatform(int value) { platform = wrap(value, PLATFORMS.length); }
    void moveSection(int delta) { setSection(section + delta); }
    void movePlatform(int delta) { setPlatform(platform + delta); }
    void rememberFocus(String viewId) { if (viewId != null && !viewId.isEmpty()) lastFocusBySection.put(sectionId(), viewId); }
    String lastFocus() { return lastFocusBySection.get(sectionId()); }
    String lastFocus(String sectionId) { return lastFocusBySection.get(sectionId); }
    static int nextSection(int current, int delta) { return wrap(current + delta, SECTIONS.length); }
    static int nextPlatform(int current, int delta) { return wrap(current + delta, PLATFORMS.length); }
    private static int wrap(int value, int size) { int result = value % size; return result < 0 ? result + size : result; }
}
