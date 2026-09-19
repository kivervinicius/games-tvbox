package com.kiver.fireretro;

import java.util.HashMap;
import java.util.Map;

/** Normalizes noisy TV remotes and gamepads into one predictable movement stream. */
final class ControllerInputRouter {
    static final int ACTION_DOWN = 0;
    static final int ACTION_UP = 1;
    static final long HOLD_DELAY_MS = 450L;
    static final long REPEAT_INTERVAL_MS = 140L;

    private static final class Press {
        final long startedAt;
        long lastAcceptedAt;
        Press(long startedAt) { this.startedAt = startedAt; this.lastAcceptedAt = startedAt; }
    }

    private final Map<String, Press> presses = new HashMap<>();
    private final AnalogInputFilter analogFilter = new AnalogInputFilter();

    boolean shouldMove(int deviceId, int direction, int action, int repeatCount, long eventTimeMs) {
        String key = deviceId + ":" + direction;
        if (action == ACTION_UP) {
            presses.remove(key);
            return false;
        }
        if (action != ACTION_DOWN) return false;

        Press press = presses.get(key);
        if (repeatCount <= 0) {
            if (press != null) return false;
            presses.put(key, new Press(eventTimeMs));
            return true;
        }
        if (press == null) return false;
        if (eventTimeMs - press.startedAt < HOLD_DELAY_MS) return false;
        if (eventTimeMs - press.lastAcceptedAt < REPEAT_INTERVAL_MS) return false;
        press.lastAcceptedAt = eventTimeMs;
        return true;
    }

    /**
     * Analog-stick entry point. Resolves axes to a direction with deadzone
     * handling and applies hold/repeat cadence shared with D-pad timings.
     */
    GameAction shouldMoveAnalog(int deviceId, float axisX, float axisY, long eventTimeMs) {
        GameAction direction = AnalogInputFilter.directionForAxes(axisX, axisY);
        if (direction == null) {
            analogFilter.releaseAll(deviceId);
            return null;
        }
        return analogFilter.shouldEmit(deviceId, direction, eventTimeMs) ? direction : null;
    }
}
