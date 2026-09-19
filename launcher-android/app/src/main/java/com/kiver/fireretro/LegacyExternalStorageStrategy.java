package com.kiver.fireretro;

import java.io.File;

public final class LegacyExternalStorageStrategy extends BaseRomStorageStrategy {
    private static final String DEFAULT_LEGACY_DIR = "/sdcard/roms";

    public LegacyExternalStorageStrategy() {
        super(new File(DEFAULT_LEGACY_DIR), StorageType.LEGACY_EXTERNAL);
    }

    public LegacyExternalStorageStrategy(File root) {
        super(root != null ? root : new File(DEFAULT_LEGACY_DIR), StorageType.LEGACY_EXTERNAL);
    }
}
