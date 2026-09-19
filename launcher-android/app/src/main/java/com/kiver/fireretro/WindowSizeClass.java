package com.kiver.fireretro;

/**
 * Window size classes. Layout decisions key off the window (not the physical
 * resolution): width/height qualifiers plus density, font scale, form factor
 * and input capabilities from {@link DeviceCapabilities}.
 */
public final class WindowSizeClass {
    public static final int COMPACT = 0;
    public static final int MEDIUM = 1;
    public static final int EXPANDED = 2;

    private WindowSizeClass() { }

    /** Width breakpoints: phones < 600dp, foldables/tablets < 840dp, else wide. */
    public static int widthClass(int smallestWidthDp) {
        if (smallestWidthDp < 600) {
            return COMPACT;
        }
        if (smallestWidthDp < 840) {
            return MEDIUM;
        }
        return EXPANDED;
    }

    public static int heightClass(int heightDp) {
        if (heightDp < 480) {
            return COMPACT;
        }
        if (heightDp < 900) {
            return MEDIUM;
        }
        return EXPANDED;
    }

    /** Wide tablets/landscape windows qualify for rail + grid + detail pane. */
    public static boolean supportsDetailPane(int widthDp) {
        return widthClass(widthDp) == EXPANDED;
    }
}
