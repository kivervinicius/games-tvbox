package com.kiver.fireretro;

import java.util.HashMap;
import java.util.Map;

/**
 * Analog-stick navigation filter: deadzone, threshold, hold delay and
 * repeat cadence so sticks can drive focus without drift.
 */
public final class AnalogInputFilter {
    /** Values below this magnitude are treated as centered (no drift). */
    public static final float DEADZONE = 0.25f;
    /** Axis magnitude required to emit a directional action. */
    public static final float THRESHOLD = 0.5f;
    public static final long HOLD_DELAY_MS = 450L;
    public static final long REPEAT_INTERVAL_MS = 140L;

    private static final class StickPress {
        final long startedAt;
        long lastAcceptedAt;
        StickPress(long startedAt) { this.startedAt = startedAt; this.lastAcceptedAt = startedAt; }
    }

    private final Map<String, StickPress> presses = new HashMap<>();

    /** Maps stick axes to a dominant direction, or null when centered/below threshold. */
    public static GameAction directionForAxes(float x, float y) {
        float ax = Math.abs(x);
        float ay = Math.abs(y);
        float dx = ax < DEADZONE ? 0f : ax;
        float dy = ay < DEADZONE ? 0f : ay;
        if (dx < THRESHOLD && dy < THRESHOLD) {
            return null;
        }
        if (dx >= dy) {
            return x > 0 ? GameAction.RIGHT : GameAction.LEFT;
        }
        return y > 0 ? GameAction.DOWN : GameAction.UP;
    }

    /**
     * Repeat gate for an already-resolved analog direction. First deflection
     * emits immediately; held deflection repeats after HOLD_DELAY_MS at
     * REPEAT_INTERVAL_MS cadence. Centering (action null) releases the key.
     */
    public boolean shouldEmit(int deviceId, GameAction direction, long eventTimeMs) {
        if (direction == null) {
            return false;
        }
        String key = deviceId + ":" + direction.ordinal();
        StickPress press = presses.get(key);
        if (press == null) {
            presses.put(key, new StickPress(eventTimeMs));
            return true;
        }
        if (eventTimeMs - press.startedAt < HOLD_DELAY_MS) {
            return false;
        }
        if (eventTimeMs - press.lastAcceptedAt < REPEAT_INTERVAL_MS) {
            return false;
        }
        press.lastAcceptedAt = eventTimeMs;
        return true;
    }

    public void release(int deviceId, GameAction direction) {
        if (direction == null) {
            return;
        }
        presses.remove(deviceId + ":" + direction.ordinal());
    }

    public void releaseAll(int deviceId) {
        String prefix = deviceId + ":";
        for (String key : presses.keySet().toArray(new String[0])) {
            if (key.startsWith(prefix)) {
                presses.remove(key);
            }
        }
    }
}
