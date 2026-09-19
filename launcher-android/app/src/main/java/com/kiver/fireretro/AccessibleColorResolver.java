package com.kiver.fireretro;

/**
 * WCAG contrast guard for themes. The theme owns background/surface/accent;
 * this resolver owns accessible foreground, focus visibility and minimums.
 * No theme may override these rules.
 */
public final class AccessibleColorResolver {
    /** Minimum ratio for normal text (WCAG AA). */
    public static final double MIN_TEXT_RATIO = 4.5;
    /** Minimum ratio for large text (WCAG AA). */
    public static final double MIN_LARGE_TEXT_RATIO = 3.0;
    /** Minimum ratio for TV focus outlines. */
    public static final double MIN_FOCUS_RATIO = 3.0;

    public static final int HIGH_CONTRAST_BACKGROUND = 0xFF000000;
    public static final int HIGH_CONTRAST_SURFACE = 0xFF000000;
    public static final int HIGH_CONTRAST_FOREGROUND = 0xFFFFFFFF;
    public static final int HIGH_CONTRAST_FOCUS = 0xFFFFFF00;

    private AccessibleColorResolver() { }

    public static double luminance(int argb) {
        double r = ((argb >> 16) & 0xFF) / 255.0;
        double g = ((argb >> 8) & 0xFF) / 255.0;
        double b = (argb & 0xFF) / 255.0;
        r = r <= 0.03928 ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        g = g <= 0.03928 ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        b = b <= 0.03928 ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    public static double contrastRatio(int foreground, int background) {
        double light = luminance(foreground);
        double dark = luminance(background);
        double lighter = Math.max(light, dark);
        double darker = Math.min(light, dark);
        return (lighter + 0.05) / (darker + 0.05);
    }

    public static boolean isAccessibleText(int foreground, int background) {
        return contrastRatio(foreground, background) >= MIN_TEXT_RATIO;
    }

    public static boolean isAccessibleLargeText(int foreground, int background) {
        return contrastRatio(foreground, background) >= MIN_LARGE_TEXT_RATIO;
    }

    public static boolean isFocusVisible(int focusColor, int background) {
        return contrastRatio(focusColor, background) >= MIN_FOCUS_RATIO;
    }

    /** Picks the candidate with the highest ratio against the background. */
    public static int bestForeground(int background, int... candidates) {
        if (candidates == null || candidates.length == 0) {
            return HIGH_CONTRAST_FOREGROUND;
        }
        int best = candidates[0];
        double bestRatio = contrastRatio(best, background);
        for (int i = 1; i < candidates.length; i++) {
            double ratio = contrastRatio(candidates[i], background);
            if (ratio > bestRatio) {
                best = candidates[i];
                bestRatio = ratio;
            }
        }
        return best;
    }

    /**
     * Returns the preferred foreground when accessible, else the fallback
     * when accessible, else the strongest of black/white. Never returns an
     * inaccessible color pair silently: the last resort always maximizes.
     */
    public static int sanitizeTheme(int background, int preferredForeground, int fallbackForeground) {
        if (isAccessibleText(preferredForeground, background)) {
            return preferredForeground;
        }
        if (isAccessibleText(fallbackForeground, background)) {
            return fallbackForeground;
        }
        return bestForeground(background, 0xFFFFFFFF, 0xFF000000);
    }
}
