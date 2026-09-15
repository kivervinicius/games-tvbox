package com.kiver.fireretro;

public final class ThemeStateTest {
    public static void main(String[] args) {
        assertEquals(0, ThemeState.nextSlide(3, 4), "advances after final slide");
        assertEquals(3, ThemeState.previousSlide(0, 4), "returns before first slide");
        assertEquals(3, ThemeState.normalizeIndex(-1, 4), "normalizes negative index");
        assertEquals(0, ThemeState.nextSlide(9, 0), "empty carousel stays at zero");
        assertEquals(3000, ThemeState.autoAdvanceMilliseconds(1), "minimum auto advance");
        assertEquals(20000, ThemeState.autoAdvanceMilliseconds(99), "maximum auto advance");
        assertEquals(179, ThemeState.overlayAlpha(70), "70 percent overlay");
        assertEquals(230, ThemeState.overlayAlpha(100), "maximum overlay");
    }

    private static void assertEquals(int expected, int actual, String scenario) {
        if (expected != actual) throw new AssertionError(scenario + ": expected " + expected + ", got " + actual);
    }
}
