package com.kiver.fireretro;

import java.io.File;
import java.util.Arrays;

public final class RomStorageTest {
    public static void main(String[] args) {
        testRemapRomPath();
        testStorageTypeHierarchy();
        testResolverFallback();
        System.out.println("PASS: RomStorage tests passed");
    }

    private static void testRemapRomPath() {
        File fakeUsb = new File("/mnt/media_rw/usb_drive");
        UsbStorageStrategy usbStrategy = new UsbStorageStrategy(fakeUsb);
        assertEquals(StorageType.USB_DRIVE, usbStrategy.getStorageType(), "Storage type must be USB_DRIVE");
        assertEquals(new File("/mnt/media_rw/usb_drive/roms").getAbsolutePath(),
                usbStrategy.getRomDirectory().getAbsolutePath(), "Rom directory matches subfolder");
        assertEquals(new File("/mnt/media_rw/usb_drive/roms/snes/game.smc").getAbsolutePath(),
                usbStrategy.remapRomPath("/sdcard/roms/snes/game.smc"), "Remaps logical path to USB folder");

        LegacyExternalStorageStrategy legacy = new LegacyExternalStorageStrategy();
        assertEquals(StorageType.LEGACY_EXTERNAL, legacy.getStorageType(), "Storage type must be LEGACY_EXTERNAL");
        assertEquals(new File("/sdcard/roms/snes/game.smc").getAbsolutePath(),
                legacy.remapRomPath("/sdcard/roms/snes/game.smc"), "Legacy path stays unaltered");
    }

    private static void testStorageTypeHierarchy() {
        File appDir = new File("/data/user/0/com.kiver.fireretro/files");
        AppStorageStrategy appStrategy = new AppStorageStrategy(appDir);
        assertEquals(StorageType.APP_EXTERNAL_SCOPED, appStrategy.getStorageType(), "Storage type must be APP_EXTERNAL_SCOPED");
        assertEquals(new File("/data/user/0/com.kiver.fireretro/files/roms/gba/game.gba").getAbsolutePath(),
                appStrategy.remapRomPath("/sdcard/roms/gba/game.gba"), "Remaps logical path to app-scoped files");
    }

    private static void testResolverFallback() {
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "fireretro-storage-test-" + System.currentTimeMillis());
        File subDir1 = new File(tempDir, "storage1");
        File subDir2 = new File(tempDir, "storage2");
        subDir1.mkdirs();
        subDir2.mkdirs();

        try {
            AppStorageStrategy s1 = new AppStorageStrategy(subDir1, StorageType.REMOVABLE_SD);
            AppStorageStrategy s2 = new AppStorageStrategy(subDir2, StorageType.APP_INTERNAL);

            RomStorageResolver resolver = new RomStorageResolver(Arrays.asList(s1, s2));
            assertEquals(s1, resolver.resolvePrimaryWritableStorage(), "First writable strategy is selected as primary");

            String logical = "/sdcard/roms/nes/mario.nes";
            String expectedPrimary = s1.remapRomPath(logical);
            assertEquals(expectedPrimary, resolver.remapRomPath(logical), "Resolver remaps to primary when file does not exist yet");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) for (File f : files) deleteRecursively(f);
        }
        file.delete();
    }

    private static void assertEquals(Object expected, Object actual, String scenario) {
        if (expected == null && actual == null) return;
        if (expected == null || !expected.equals(actual)) {
            throw new AssertionError(scenario + ": expected <" + expected + ">, got <" + actual + ">");
        }
    }
}
