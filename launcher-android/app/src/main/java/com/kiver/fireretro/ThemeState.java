package com.kiver.fireretro;

final class ThemeState {
    private ThemeState() { }

    static int nextSlide(int currentIndex, int slideCount) {
        if (slideCount <= 0) return 0;
        return (normalizeIndex(currentIndex, slideCount) + 1) % slideCount;
    }

    static int previousSlide(int currentIndex, int slideCount) {
        if (slideCount <= 0) return 0;
        return (normalizeIndex(currentIndex, slideCount) + slideCount - 1) % slideCount;
    }

    static int normalizeIndex(int index, int slideCount) {
        if (slideCount <= 0) return 0;
        int result = index % slideCount;
        return result < 0 ? result + slideCount : result;
    }

    static int autoAdvanceMilliseconds(int seconds) {
        return Math.max(3, Math.min(20, seconds)) * 1000;
    }

    static int overlayAlpha(int percent) {
        int safePercent = Math.max(0, Math.min(90, percent));
        return Math.round(255f * safePercent / 100f);
    }
}
