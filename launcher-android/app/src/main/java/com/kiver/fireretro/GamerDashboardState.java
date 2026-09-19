package com.kiver.fireretro;

public final class GamerDashboardState {
    private boolean visible;
    private int batteryPercent = 100;
    private boolean charging = false;
    private long availableStorageBytes = 0L;
    private boolean controllerConnected = false;
    private String controllerName = "Nenhum";
    private boolean nintendoLayout = false;
    private boolean touchControlsVisible = false;

    public GamerDashboardState() { }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void toggleVisibility() {
        this.visible = !this.visible;
    }

    public int getBatteryPercent() {
        return batteryPercent;
    }

    public void setBattery(int percent, boolean charging) {
        this.batteryPercent = Math.max(0, Math.min(100, percent));
        this.charging = charging;
    }

    public boolean isCharging() {
        return charging;
    }

    public String formatBattery() {
        return batteryPercent + "%" + (charging ? " (Carregando)" : "");
    }

    public long getAvailableStorageBytes() {
        return availableStorageBytes;
    }

    public void setAvailableStorageBytes(long bytes) {
        this.availableStorageBytes = Math.max(0L, bytes);
    }

    public String formatStorage() {
        if (availableStorageBytes >= 1024L * 1024L * 1024L) {
            double gb = (double) availableStorageBytes / (1024.0 * 1024.0 * 1024.0);
            return String.format(java.util.Locale.US, "%.1f GB livres", gb);
        }
        double mb = (double) availableStorageBytes / (1024.0 * 1024.0);
        return String.format(java.util.Locale.US, "%.0f MB livres", mb);
    }

    public boolean isControllerConnected() {
        return controllerConnected;
    }

    public String getControllerName() {
        return controllerName;
    }

    public void setController(boolean connected, String name) {
        this.controllerConnected = connected;
        this.controllerName = (connected && name != null && !name.trim().isEmpty()) ? name : "Nenhum";
    }

    public boolean isNintendoLayout() {
        return nintendoLayout;
    }

    public void setNintendoLayout(boolean nintendoLayout) {
        this.nintendoLayout = nintendoLayout;
    }

    public void toggleNintendoLayout() {
        this.nintendoLayout = !this.nintendoLayout;
    }

    public boolean isTouchControlsVisible() {
        return touchControlsVisible;
    }

    public void setTouchControlsVisible(boolean visible) {
        this.touchControlsVisible = visible;
    }

    public static boolean shouldEnableTouchFallback(boolean hasTouchscreen, boolean hasPhysicalController) {
        return hasTouchscreen && !hasPhysicalController;
    }
}
