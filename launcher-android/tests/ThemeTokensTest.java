package com.kiver.fireretro;

public final class ThemeTokensTest {
    public static void main(String[] args) {
        assertTrue(ThemeTokens.isMonotonicSpacing(), "Spacing scale is monotonic");
        assertTrue(ThemeTokens.isValidFocusScale(1.06f), "Mid focus scale valid");
        assertTrue(!ThemeTokens.isValidFocusScale(1.2f), "Oversized focus rejected");
        assertTrue(!ThemeTokens.isValidFocusScale(1.0f), "Color-only scale rejected");
        assertEquals(6, ThemeTokens.touchTargetPadding(42), "42dp visual needs 6dp padding");
        assertEquals(0, ThemeTokens.touchTargetPadding(48), "48dp visual needs none");
        assertTrue(ThemeTokens.TV_TITLE_SP > ThemeTokens.GAMER_TITLE_SP, "TV type reads at distance");
        assertTrue(ThemeTokens.MIN_TOUCH_TARGET_DP == 48, "Touch floor is 48dp");
        System.out.println("PASS: ThemeTokens tests passed");
    }

    private static void assertEquals(Object expected, Object actual, String scenario) {
        if (expected == null && actual == null) return;
        if (expected == null || !expected.equals(actual)) {
            throw new AssertionError(scenario + ": expected <" + expected + ">, got <" + actual + ">");
        }
    }

    private static void assertTrue(boolean val, String scenario) {
        if (!val) throw new AssertionError(scenario);
    }
}
