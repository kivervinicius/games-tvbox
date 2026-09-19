package com.kiver.fireretro;

import java.io.File;

/**
 * App-managed (scoped) storage strategy. Preferred primary on modern API
 * levels: no legacy permission required, survives uninstall-cleanup rules,
 * and never exposes a raw /sdcard path to the catalog.
 */
public final class AppManagedStorageStrategy extends BaseRomStorageStrategy {
    public AppManagedStorageStrategy(File appExternalDir) {
        super(appExternalDir != null ? new File(appExternalDir, "roms") : null,
                StorageType.APP_EXTERNAL_SCOPED);
    }

    public AppManagedStorageStrategy(File customDir, StorageType type) {
        super(customDir, type != null ? type : StorageType.APP_EXTERNAL_SCOPED);
    }
}
