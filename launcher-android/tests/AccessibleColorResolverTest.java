package com.kiver.fireretro;

public final class AccessibleColorResolverTest {
    public static void main(String[] args) {
        testKnownRatios();
        testTextMinimums();
        testBestForeground();
        testSanitizeTheme();
        testFocusVisibility();
        testHighContrastPair();
        System.out.println("PASS: AccessibleColorResolver tests passed");
    }

    private static void testKnownRatios() {
        double bw = AccessibleColorResolver.contrastRatio(0xFFFFFFFF, 0xFF000000);
        assertTrue(bw > 20.9 && bw < 21.1, "Black on white is 21:1, got " + bw);
        assertEquals(1.0, AccessibleColorResolver.contrastRatio(0xFF123456, 0xFF123456), 0.001, "Same color is 1:1");
    }

    private static void testTextMinimums() {
        assertTrue(AccessibleColorResolver.isAccessibleText(0xFFFFFFFF, 0xFF000000), "White on black passes AA");
        assertTrue(!AccessibleColorResolver.isAccessibleText(0xFF777777, 0xFFFFFFFF), "Mid gray on white fails AA");
        assertTrue(AccessibleColorResolver.isAccessibleLargeText(0xFF767676, 0xFFFFFFFF), "Dark gray on white passes large-text AA");
        assertTrue(!AccessibleColorResolver.isAccessibleLargeText(0xFFAAAAAA, 0xFFFFFFFF), "Light gray on white fails even large-text");
    }

    private static void testBestForeground() {
        assertEquals(0xFFFFFFFF, AccessibleColorResolver.bestForeground(0xFF000000, 0xFF000000, 0xFFFFFFFF), "White wins on black");
        assertEquals(0xFF000000, AccessibleColorResolver.bestForeground(0xFFFFFFFF, 0xFF000000, 0xFFFFFFFF), "Black wins on white");
    }

    private static void testSanitizeTheme() {
        int kept = AccessibleColorResolver.sanitizeTheme(0xFF000000, 0xFFFFFFFF, 0xFF000000);
        assertEquals(0xFFFFFFFF, kept, "Accessible preferred color is kept");
        int fixed = AccessibleColorResolver.sanitizeTheme(0xFFFFFFFF, 0xFFEEEEEE, 0xFF000000);
        assertEquals(0xFF000000, fixed, "Inaccessible preferred falls back");
        int lastResort = AccessibleColorResolver.sanitizeTheme(0xFF808080, 0xFF909090, 0xFF707070);
        assertTrue(AccessibleColorResolver.isAccessibleText(lastResort, 0xFF808080) || lastResort == 0xFFFFFFFF || lastResort == 0xFF000000, "Last resort maximizes contrast");
    }

    private static void testFocusVisibility() {
        assertTrue(AccessibleColorResolver.isFocusVisible(0xFFFFFF00, 0xFF000000), "Yellow focus on black is visible");
        assertTrue(!AccessibleColorResolver.isFocusVisible(0xFF222222, 0xFF000000), "Near-black focus on black fails");
    }

    private static void testHighContrastPair() {
        assertTrue(AccessibleColorResolver.isAccessibleText(
                AccessibleColorResolver.HIGH_CONTRAST_FOREGROUND,
                AccessibleColorResolver.HIGH_CONTRAST_BACKGROUND), "HIGH_CONTRAST pair passes AA");
        assertTrue(AccessibleColorResolver.isFocusVisible(
                AccessibleColorResolver.HIGH_CONTRAST_FOCUS,
                AccessibleColorResolver.HIGH_CONTRAST_BACKGROUND), "HIGH_CONTRAST focus is visible");
    }

    private static void assertEquals(int expected, int actual, String scenario) {
        if (expected != actual) throw new AssertionError(scenario + ": expected <" + expected + ">, got <" + actual + ">");
    }

    private static void assertEquals(double expected, double actual, double delta, String scenario) {
        if (Math.abs(expected - actual) > delta) throw new AssertionError(scenario + ": expected <" + expected + ">, got <" + actual + ">");
    }

    private static void assertTrue(boolean val, String scenario) {
        if (!val) throw new AssertionError(scenario);
    }
}
