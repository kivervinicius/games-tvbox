package com.kiver.fireretro;

import java.io.File;

public final class RemovableStorageStrategy extends BaseRomStorageStrategy {
    public RemovableStorageStrategy(File mountRoot) {
        super(mountRoot != null ? new File(mountRoot, "roms") : null, StorageType.REMOVABLE_SD);
    }
}
