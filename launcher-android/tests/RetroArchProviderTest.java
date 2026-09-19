package com.kiver.fireretro;

public final class RetroArchProviderTest {
    public static void main(String[] args) {
        test32BitDefault();
        test64BitAbi();
        testCoreResolution();
        System.out.println("PASS: RetroArchProvider tests passed");
    }

    private static void test32BitDefault() {
        RetroArchProvider provider = new RetroArchProvider();
        assertEquals(RetroArchProvider.PACKAGE_RA32, provider.getPackageName(), "Default package is ra32");
        assertEquals(RetroArchProvider.ACTIVITY_RETRO_ACTIVITY_FUTURE, provider.getActivityName(), "Default activity is RetroActivityFuture");
        assertEquals("/sdcard/Android/data/com.retroarch.ra32/files/retroarch.cfg", provider.getDefaultConfigPath(), "Default config path");
        assertEquals("/data/data/com.retroarch.ra32/cores/", provider.getDefaultCoresDirectory(), "Default cores dir");
    }

    private static void test64BitAbi() {
        RetroArchProvider provider = RetroArchProvider.forAbi("arm64-v8a");
        assertEquals(RetroArchProvider.PACKAGE_RA64, provider.getPackageName(), "64-bit ABI selects ra64");
        assertEquals("/sdcard/Android/data/com.retroarch.a64/files/retroarch.cfg", provider.getDefaultConfigPath(), "64-bit config path");
        assertEquals("/data/data/com.retroarch.a64/cores/", provider.getDefaultCoresDirectory(), "64-bit cores dir");
    }

    private static void testCoreResolution() {
        RetroArchProvider provider = new RetroArchProvider();
        assertEquals("/data/data/com.retroarch.ra32/cores/snes9x_libretro_android.so",
                provider.resolveCorePath("snes", "snes9x_libretro_android.so"), "Relative core resolves to cores dir");
        assertEquals("/custom/path/core.so",
                provider.resolveCorePath("snes", "/custom/path/core.so"), "Absolute core path preserved");
    }

    private static void assertEquals(Object expected, Object actual, String scenario) {
        if (expected == null && actual == null) return;
        if (expected == null || !expected.equals(actual)) {
            throw new AssertionError(scenario + ": expected <" + expected + ">, got <" + actual + ">");
        }
    }
}
