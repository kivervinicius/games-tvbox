package com.kiver.fireretro;

/**
 * Device capabilities used for UX and compatibility decisions.
 *
 * <p>These values are never a security authorization signal.
 * Resolution order is always Platform -&gt; DeviceCapabilities -&gt;
 * DeviceProfile -&gt; ExperienceMode -&gt; UI adaptation.
 */
public final class DeviceCapabilities {
    public static final int ORIENTATION_UNDEFINED = 0;
    public static final int ORIENTATION_PORTRAIT = 1;
    public static final int ORIENTATION_LANDSCAPE = 2;

    /** Smallest-width qualifier breakpoint between phone and tablet layouts. */
    public static final int TABLET_SMALLEST_WIDTH_DP = 600;

    private final Platform platform;
    private final int androidApi;
    private final String manufacturer;
    private final String model;
    private final boolean television;
    private final boolean touchscreen;
    private final boolean mouse;
    private final boolean keyboard;
    private final boolean dpad;
    private final boolean remote;
    private final boolean gamepad;
    private final boolean joystick;
    private final String[] abis;
    private final int smallestWidthDp;
    private final float density;
    private final float fontScale;
    private final int orientation;
    private final boolean externalDisplay;
    private final boolean removableStorage;
    private final long availableStorageBytes;
    private final boolean playStore;
    private final boolean amazonStore;
    private final boolean lowRam;
    private final boolean gamerBuildFlavor;

    public DeviceCapabilities(
            Platform platform,
            int androidApi,
            String manufacturer,
            String model,
            boolean television,
            boolean touchscreen,
            boolean mouse,
            boolean keyboard,
            boolean dpad,
            boolean remote,
            boolean gamepad,
            boolean joystick,
            String[] abis,
            int smallestWidthDp,
            float density,
            float fontScale,
            int orientation,
            boolean externalDisplay,
            boolean removableStorage,
            long availableStorageBytes,
            boolean playStore,
            boolean amazonStore,
            boolean lowRam,
            boolean gamerBuildFlavor) {
        this.platform = platform != null ? platform : Platform.UNKNOWN;
        this.androidApi = androidApi;
        this.manufacturer = manufacturer != null ? manufacturer : "Generic";
        this.model = model != null ? model : "Generic";
        this.television = television;
        this.touchscreen = touchscreen;
        this.mouse = mouse;
        this.keyboard = keyboard;
        this.dpad = dpad;
        this.remote = remote;
        this.gamepad = gamepad;
        this.joystick = joystick;
        this.abis = abis != null ? abis.clone() : new String[0];
        this.smallestWidthDp = smallestWidthDp;
        this.density = density > 0 ? density : 1.0f;
        this.fontScale = fontScale > 0 ? fontScale : 1.0f;
        this.orientation = orientation;
        this.externalDisplay = externalDisplay;
        this.removableStorage = removableStorage;
        this.availableStorageBytes = Math.max(0, availableStorageBytes);
        this.playStore = playStore;
        this.amazonStore = amazonStore;
        this.lowRam = lowRam;
        this.gamerBuildFlavor = gamerBuildFlavor;
    }

    /** Phone + external display + controller behaves like a TV shell. */
    public boolean isDocked() {
        return externalDisplay && gamepad && !television;
    }

    /** Performance class, never a manufacturer check. */
    public boolean isLowMemory() {
        return lowRam || androidApi <= 25;
    }

    public boolean isWideTablet() {
        return smallestWidthDp >= TABLET_SMALLEST_WIDTH_DP;
    }

    public Platform getPlatform() { return platform; }
    public int getAndroidApi() { return androidApi; }
    public String getManufacturer() { return manufacturer; }
    public String getModel() { return model; }
    public boolean isTelevision() { return television; }
    public boolean hasTouchscreen() { return touchscreen; }
    public boolean hasMouse() { return mouse; }
    public boolean hasKeyboard() { return keyboard; }
    public boolean hasDpad() { return dpad; }
    public boolean hasRemote() { return remote; }
    public boolean hasGamepad() { return gamepad; }
    public boolean hasJoystick() { return joystick; }
    public String[] getAbis() { return abis.clone(); }
    public int getSmallestWidthDp() { return smallestWidthDp; }
    public float getDensity() { return density; }
    public float getFontScale() { return fontScale; }
    public int getOrientation() { return orientation; }
    public boolean hasExternalDisplay() { return externalDisplay; }
    public boolean hasRemovableStorage() { return removableStorage; }
    public long getAvailableStorageBytes() { return availableStorageBytes; }
    public boolean hasPlayStore() { return playStore; }
    public boolean hasAmazonStore() { return amazonStore; }
    public boolean isLowRam() { return lowRam; }
    public boolean isGamerBuildFlavor() { return gamerBuildFlavor; }
}
