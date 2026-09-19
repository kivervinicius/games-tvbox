package com.kiver.fireretro;

import java.io.File;

public abstract class BaseRomStorageStrategy implements RomStorageStrategy {
    public static final String LOGICAL_ROM_PREFIX = "/sdcard/roms/";
    protected final File rootDir;
    protected final StorageType storageType;

    public BaseRomStorageStrategy(File rootDir, StorageType storageType) {
        this.rootDir = rootDir;
        this.storageType = storageType;
    }

    @Override
    public StorageType getStorageType() {
        return storageType;
    }

    @Override
    public File getRomDirectory() {
        return rootDir;
    }

    @Override
    public String remapRomPath(String logicalPath) {
        if (logicalPath == null || !logicalPath.startsWith(LOGICAL_ROM_PREFIX)) {
            return logicalPath;
        }
        if (rootDir == null) {
            return logicalPath;
        }
        String relative = logicalPath.substring(LOGICAL_ROM_PREFIX.length());
        return new File(rootDir, relative).getAbsolutePath();
    }

    @Override
    public long getAvailableBytes() {
        return rootDir != null && rootDir.exists() ? rootDir.getUsableSpace() : 0L;
    }

    @Override
    public long getTotalBytes() {
        return rootDir != null && rootDir.exists() ? rootDir.getTotalSpace() : 0L;
    }

    @Override
    public boolean isWritable() {
        if (rootDir == null) return false;
        if (rootDir.exists()) return rootDir.canWrite();
        File parent = rootDir.getParentFile();
        return parent != null && parent.exists() && parent.canWrite();
    }

    @Override
    public boolean isAvailable() {
        if (rootDir == null) return false;
        if (rootDir.exists()) return true;
        File parent = rootDir.getParentFile();
        return parent != null && parent.exists();
    }
}
