package com.kiver.fireretro;

import java.io.File;

/** Resolves removable Android TV storage without changing the app's private settings area. */
final class StoragePaths {
    private static final String LOGICAL_ROM_PREFIX = "/sdcard/roms/";
    private StoragePaths() { }

    static File removableRoot() {
        File storage = new File("/storage");
        File[] children = storage.listFiles();
        if (children != null) for (File child : children) {
            String name = child.getName();
            if (child.isDirectory() && child.canWrite() && !"emulated".equals(name) && !"self".equals(name)) return child;
        }
        return null;
    }
    static File romRoot() {
        return RomStorageResolver.getDefault().resolvePrimaryWritableStorage().getRomDirectory();
    }
    static String remapRomPath(String path) {
        return RomStorageResolver.getDefault().remapRomPath(path);
    }
}
