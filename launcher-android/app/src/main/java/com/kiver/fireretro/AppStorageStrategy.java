package com.kiver.fireretro;

import java.io.File;

public final class AppStorageStrategy extends BaseRomStorageStrategy {
    public AppStorageStrategy(File appFilesDir) {
        super(appFilesDir != null ? new File(appFilesDir, "roms") : null, StorageType.APP_EXTERNAL_SCOPED);
    }

    public AppStorageStrategy(File customDir, StorageType type) {
        super(customDir, type != null ? type : StorageType.APP_EXTERNAL_SCOPED);
    }
}
