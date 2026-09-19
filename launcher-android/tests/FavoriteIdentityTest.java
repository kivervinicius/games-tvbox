package com.kiver.fireretro;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class FavoriteIdentityTest {
    public static void main(String[] args) {
        testKeyForms();
        testMigration();
        testNoLoss();
        System.out.println("PASS: FavoriteIdentity tests passed");
    }

    private static void testKeyForms() {
        assertEquals("sha256:abc123", FavoriteIdentity.keyFor("/sdcard/roms/a.smc", "abc123"), "Raw id gets scheme");
        assertEquals("sha256:abc123", FavoriteIdentity.keyFor("/usb/roms/a.smc", "sha256:abc123"), "Scheme kept verbatim");
        assertEquals("path:/sdcard/roms/b.smc", FavoriteIdentity.keyFor("/sdcard/roms/b.smc", null), "Path fallback");
        assertEquals("path:/sdcard/roms/b.smc", FavoriteIdentity.keyFor("/sdcard/roms/b.smc", "  "), "Blank id falls back");
        assertTrue(FavoriteIdentity.isStable("sha256:abc123"), "Scheme key is stable");
        assertTrue(!FavoriteIdentity.isStable("path:/sdcard/roms/b.smc"), "Path key is legacy");
    }

    private static void testMigration() {
        Set<String> legacy = new LinkedHashSet<>();
        legacy.add("/sdcard/roms/a.smc");
        legacy.add("/sdcard/roms/b.smc");
        Map<String, String> ids = new HashMap<>();
        ids.put("/sdcard/roms/a.smc", "deadbeef");
        Set<String> migrated = FavoriteIdentity.migrate(legacy, ids);
        assertTrue(migrated.contains("sha256:deadbeef"), "Known id becomes stable");
        assertTrue(migrated.contains("path:/sdcard/roms/b.smc"), "Unknown id keeps path key");
        assertEquals(2, migrated.size(), "Migration preserves cardinality");
    }

    private static void testNoLoss() {
        assertTrue(FavoriteIdentity.migrate(null, null).isEmpty(), "Null migrates to empty, never throws");
        Set<String> legacy = new LinkedHashSet<>();
        legacy.add(null);
        legacy.add("/sdcard/roms/c.smc");
        assertEquals(1, FavoriteIdentity.migrate(legacy, null).size(), "Null entries skipped, real ones kept");
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
