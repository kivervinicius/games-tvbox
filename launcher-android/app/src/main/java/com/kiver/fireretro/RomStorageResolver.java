package com.kiver.fireretro;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RomStorageResolver {
    private final List<RomStorageStrategy> strategies;
    private static volatile RomStorageResolver defaultInstance;

    public RomStorageResolver(List<RomStorageStrategy> strategies) {
        if (strategies == null || strategies.isEmpty()) {
            this.strategies = Collections.singletonList(new LegacyExternalStorageStrategy());
        } else {
            this.strategies = Collections.unmodifiableList(new ArrayList<>(strategies));
        }
    }

    public static RomStorageResolver getDefault() {
        if (defaultInstance == null) {
            synchronized (RomStorageResolver.class) {
                if (defaultInstance == null) {
                    List<RomStorageStrategy> list = new ArrayList<>();
                    File removable = StoragePaths.removableRoot();
                    if (removable != null) {
                        String name = removable.getName().toLowerCase();
                        if (name.contains("usb") || name.contains("otg")) {
                            list.add(new UsbStorageStrategy(removable));
                        } else {
                            list.add(new RemovableStorageStrategy(removable));
                        }
                    }
                    list.add(new LegacyExternalStorageStrategy());
                    defaultInstance = new RomStorageResolver(list);
                }
            }
        }
        return defaultInstance;
    }

    public static void setDefault(RomStorageResolver resolver) {
        synchronized (RomStorageResolver.class) {
            defaultInstance = resolver;
        }
    }

    public List<RomStorageStrategy> getStrategies() {
        return strategies;
    }

    public RomStorageStrategy resolvePrimaryWritableStorage() {
        for (RomStorageStrategy strategy : strategies) {
            if (strategy.isAvailable() && strategy.isWritable()) {
                return strategy;
            }
        }
        return strategies.get(strategies.size() - 1);
    }

    public File resolveRomFile(String logicalPath) {
        if (logicalPath == null) return null;
        for (RomStorageStrategy strategy : strategies) {
            String remapped = strategy.remapRomPath(logicalPath);
            File candidate = new File(remapped);
            if (candidate.exists()) {
                return candidate;
            }
        }
        RomStorageStrategy primary = resolvePrimaryWritableStorage();
        return new File(primary.remapRomPath(logicalPath));
    }

    public String remapRomPath(String logicalPath) {
        File resolved = resolveRomFile(logicalPath);
        return resolved != null ? resolved.getAbsolutePath() : logicalPath;
    }

    public long getTotalAvailableBytes() {
        RomStorageStrategy primary = resolvePrimaryWritableStorage();
        return primary.getAvailableBytes();
    }
}
