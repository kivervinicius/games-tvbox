package com.kiver.fireretro;

/**
 * Design tokens. Brand palette stays in themes; everything structural
 * (spacing, shape, focus, motion, elevation, minimum touch target and the
 * TV/Gamer type scales) is centralized here so shells can't drift.
 */
public final class ThemeTokens {
    public static final int SPACING_XXS_DP = 2;
    public static final int SPACING_XS_DP = 4;
    public static final int SPACING_S_DP = 8;
    public static final int SPACING_M_DP = 12;
    public static final int SPACING_L_DP = 16;
    public static final int SPACING_XL_DP = 24;
    public static final int SPACING_XXL_DP = 32;

    public static final int CORNER_S_DP = 4;
    public static final int CORNER_M_DP = 8;
    public static final int CORNER_L_DP = 16;

    /** TV focus must be unmissable: scale plus outline, never color alone. */
    public static final float FOCUS_SCALE_MIN = 1.05f;
    public static final float FOCUS_SCALE_MAX = 1.08f;

    public static final int MOTION_SHORT_MS = 120;
    public static final int MOTION_MEDIUM_MS = 200;

    public static final int ELEVATION_CARD_DP = 2;
    public static final int ELEVATION_FOCUSED_DP = 8;

    /** SP scales: TV reads at 10-foot distance, Gamer follows Material. */
    public static final int TV_TITLE_SP = 38;
    public static final int TV_BODY_SP = 20;
    public static final int GAMER_TITLE_SP = 22;
    public static final int GAMER_BODY_SP = 14;

    public static final int MIN_TOUCH_TARGET_DP = 48;

    private ThemeTokens() { }

    public static boolean isValidFocusScale(float scale) {
        return scale >= FOCUS_SCALE_MIN && scale <= FOCUS_SCALE_MAX;
    }

    /** Extra padding needed around a visual of visualSizeDp to reach 48dp. */
    public static int touchTargetPadding(int visualSizeDp) {
        return Math.max(0, MIN_TOUCH_TARGET_DP - visualSizeDp);
    }

    public static boolean isMonotonicSpacing() {
        return SPACING_XXS_DP < SPACING_XS_DP && SPACING_XS_DP < SPACING_S_DP
                && SPACING_S_DP < SPACING_M_DP && SPACING_M_DP < SPACING_L_DP
                && SPACING_L_DP < SPACING_XL_DP && SPACING_XL_DP < SPACING_XXL_DP;
    }
}
