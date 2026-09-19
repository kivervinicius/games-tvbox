package com.kiver.fireretro;

import java.io.File;

public final class UsbStorageStrategy extends BaseRomStorageStrategy {
    public UsbStorageStrategy(File mountRoot) {
        super(mountRoot != null ? new File(mountRoot, "roms") : null, StorageType.USB_DRIVE);
    }
}
