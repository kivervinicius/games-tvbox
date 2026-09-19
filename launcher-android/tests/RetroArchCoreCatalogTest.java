package com.kiver.fireretro;

import java.util.HashSet;
import java.util.Set;

public final class RetroArchCoreCatalogTest {
    public static void main(String[] args) {
        testKnownCores();
        testParseCoreId();
        testProviderSelectBest();
        testProviderAvailability();
        testCoreIdResolution();
        System.out.println("PASS: RetroArchCoreCatalog tests passed");
    }

    private static void testKnownCores() {
        assertTrue(RetroArchCoreCatalog.isKnownCoreId("snes9x"), "snes9x is known");
        assertTrue(RetroArchCoreCatalog.isKnownCoreId("PCSX_REARMED"), "Lookup is case-insensitive");
        assertTrue(!RetroArchCoreCatalog.isKnownCoreId("not_a_core"), "Unknown id rejected");
        assertEquals("snes9x_libretro_android.so", RetroArchCoreCatalog.coreFileNameFor("snes9x"), "File name translation");
        assertEquals(null, RetroArchCoreCatalog.coreFileNameFor("not_a_core"), "Unknown id has no file");
    }

    private static void testParseCoreId() {
        assertEquals("snes9x", RetroArchCoreCatalog.parseCoreId("/data/user/0/com.retroarch.a64/cores/snes9x_libretro_android.so"), "Absolute path parses");
        assertEquals("mgba", RetroArchCoreCatalog.parseCoreId("mgba_libretro_android.so"), "File name parses");
        assertEquals("custom", RetroArchCoreCatalog.parseCoreId("/cores/custom.so"), "Unknown .so falls back to base name");
        assertEquals("", RetroArchCoreCatalog.parseCoreId(null), "Null parses to empty");
    }

    private static void testProviderSelectBest() {
        Set<String> installed = new HashSet<>();
        installed.add("com.retroarch");
        installed.add("com.retroarch.ra32");
        assertEquals("com.retroarch", RetroArchProvider.selectBest("arm64-v8a", installed).getPackageName(), "Standard package preferred");
        Set<String> split = new HashSet<>();
        split.add("com.retroarch.a64");
        assertEquals("com.retroarch.a64", RetroArchProvider.selectBest("arm64-v8a", split).getPackageName(), "ABI-matched split selected");
        assertEquals("com.retroarch.ra32", RetroArchProvider.selectBest("armeabi-v7a", new HashSet<String>()).getPackageName(), "ABI fallback preserved");
        assertEquals("com.retroarch.ra32", new RetroArchProvider().getPackageName(), "Legacy default ctor still ra32");
    }

    private static void testProviderAvailability() {
        RetroArchProvider provider = new RetroArchProvider(RetroArchProvider.PACKAGE_RA64);
        Set<String> installed = new HashSet<>();
        installed.add(RetroArchProvider.PACKAGE_RA64);
        assertTrue(provider.isAvailable(installed), "Installed package is available");
        assertTrue(!provider.isAvailable(new HashSet<String>()), "Missing package is unavailable");
    }

    private static void testCoreIdResolution() {
        RetroArchProvider provider = new RetroArchProvider(RetroArchProvider.PACKAGE_RA64);
        String resolved = provider.resolveCorePath("snes", "snes9x");
        assertTrue(resolved.endsWith("snes9x_libretro_android.so"), "coreId translates to file: " + resolved);
        assertEquals("/abs/path/core.so", provider.resolveCorePath("snes", "/abs/path/core.so"), "Absolute passthrough preserved");
        assertTrue(provider.resolveCorePath("snes", "custom_libretro_android.so").endsWith("custom_libretro_android.so"), "File name passthrough preserved");
        assertEquals("mgba", provider.resolveCoreId("/data/cores/mgba_libretro_android.so"), "Provider exposes coreId");
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
