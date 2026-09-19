package com.kiver.fireretro;

import java.io.File;

public interface RomStorageStrategy {
    StorageType getStorageType();
    File getRomDirectory();
    String remapRomPath(String logicalPath);
    long getAvailableBytes();
    long getTotalBytes();
    boolean isWritable();
    boolean isAvailable();
}
