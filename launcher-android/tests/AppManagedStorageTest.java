package com.kiver.fireretro;

import java.io.File;

public final class AppManagedStorageTest {
    public static void main(String[] args) throws Exception {
        testPrimaryOrder();
        testExistingInstallsKeepResolving();
        testNullDirFallsBack();
        System.out.println("PASS: AppManagedStorage tests passed");
    }

    private static void testPrimaryOrder() {
        File appDir = new File(System.getProperty("java.io.tmpdir"), "appmanaged-" + System.nanoTime());
        if (!appDir.mkdirs()) throw new AssertionError("Cannot create temp app dir");
        try {
            RomStorageResolver resolver = RomStorageResolver.withAppManaged(appDir, null);
            assertEquals(StorageType.APP_EXTERNAL_SCOPED, resolver.resolvePrimaryWritableStorage().getStorageType(), "App-managed is primary when writable parent exists");
            assertTrue(resolver.getStrategies().size() >= 2, "Legacy remains as fallback");
            assertEquals(StorageType.LEGACY_EXTERNAL, resolver.getStrategies().get(resolver.getStrategies().size() - 1).getStorageType(), "Legacy is last");
        } finally {
            appDir.delete();
        }
    }

    private static void testExistingInstallsKeepResolving() throws Exception {
        File appDir = new File(System.getProperty("java.io.tmpdir"), "appmanaged-roms-" + System.nanoTime());
        File roms = new File(appDir, "roms");
        if (!roms.mkdirs()) throw new AssertionError("Cannot create temp roms dir");
        File game = new File(roms, "zelda.smc");
        if (!game.createNewFile()) throw new AssertionError("Cannot create temp rom");
        RomStorageResolver resolver = RomStorageResolver.withAppManaged(appDir, null);
        File resolved = resolver.resolveRomFile("/sdcard/roms/zelda.smc");
        assertEquals(game.getAbsolutePath(), resolved.getAbsolutePath(), "Existing install resolves via app-managed root");
        game.delete();
        roms.delete();
        appDir.delete();
    }

    private static void testNullDirFallsBack() {
        RomStorageResolver resolver = RomStorageResolver.withAppManaged(null, null);
        assertTrue(resolver.resolvePrimaryWritableStorage() != null, "Null app dir still resolves a primary");
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
