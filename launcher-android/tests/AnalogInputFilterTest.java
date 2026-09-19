package com.kiver.fireretro;

public final class AnalogInputFilterTest {
    public static void main(String[] args) {
        testDirectionMapping();
        testDeadzone();
        testRepeatCadence();
        testRelease();
        testRouterAnalogEntry();
        System.out.println("PASS: AnalogInputFilter tests passed");
    }

    private static void testDirectionMapping() {
        assertEquals(GameAction.RIGHT, AnalogInputFilter.directionForAxes(0.8f, 0.1f), "Right deflection");
        assertEquals(GameAction.LEFT, AnalogInputFilter.directionForAxes(-0.9f, 0.0f), "Left deflection");
        assertEquals(GameAction.DOWN, AnalogInputFilter.directionForAxes(0.1f, 0.9f), "Down deflection");
        assertEquals(GameAction.UP, AnalogInputFilter.directionForAxes(0.0f, -0.9f), "Up deflection");
        assertEquals(GameAction.RIGHT, AnalogInputFilter.directionForAxes(0.9f, 0.8f), "Dominant axis wins");
    }

    private static void testDeadzone() {
        assertEquals(null, AnalogInputFilter.directionForAxes(0.0f, 0.0f), "Centered stick is silent");
        assertEquals(null, AnalogInputFilter.directionForAxes(0.2f, -0.15f), "Drift below deadzone is silent");
        assertEquals(null, AnalogInputFilter.directionForAxes(0.4f, 0.0f), "Below threshold is silent");
    }

    private static void testRepeatCadence() {
        AnalogInputFilter filter = new AnalogInputFilter();
        assertTrue(filter.shouldEmit(1, GameAction.RIGHT, 1000L), "First deflection emits");
        assertTrue(!filter.shouldEmit(1, GameAction.RIGHT, 1100L), "Hold repeats only after delay");
        assertTrue(!filter.shouldEmit(1, GameAction.RIGHT, 1000L + AnalogInputFilter.HOLD_DELAY_MS - 1), "No early repeat");
        assertTrue(filter.shouldEmit(1, GameAction.RIGHT, 1000L + AnalogInputFilter.HOLD_DELAY_MS + 1), "Repeat after hold delay");
        assertTrue(filter.shouldEmit(1, GameAction.UP, 1000L + AnalogInputFilter.HOLD_DELAY_MS + 1), "Fresh direction emits immediately on its own gate");
        assertTrue(!filter.shouldEmit(1, GameAction.UP, 1000L + AnalogInputFilter.HOLD_DELAY_MS + 2), "Fresh direction then gates repeats");
    }

    private static void testRelease() {
        AnalogInputFilter filter = new AnalogInputFilter();
        assertTrue(filter.shouldEmit(2, GameAction.LEFT, 500L), "First emits");
        filter.release(2, GameAction.LEFT);
        assertTrue(filter.shouldEmit(2, GameAction.LEFT, 600L), "Released stick re-emits");
        filter.releaseAll(2);
        assertTrue(filter.shouldEmit(2, GameAction.LEFT, 700L), "releaseAll clears");
    }

    private static void testRouterAnalogEntry() {
        ControllerInputRouter router = new ControllerInputRouter();
        assertEquals(null, router.shouldMoveAnalog(3, 0.0f, 0.0f, 100L), "Router silences centered stick");
        assertEquals(GameAction.UP, router.shouldMoveAnalog(3, 0.0f, -1.0f, 100L), "Router emits stick direction");
        assertEquals(null, router.shouldMoveAnalog(3, 0.0f, -1.0f, 150L), "Router gates hold repeat");
    }

    private static void assertEquals(Object expected, Object actual, String scenario) {
        if (expected == null && actual == null) return;
        if (expected == null || !expected.equals(actual)) {
            throw new AssertionError(scenario + ": expected <" + expected + ">, got <" + actual + ">");
        }
    }

    private static void assertTrue(boolean val, String scenario) {
        if (!val) throw new AssertionError(scenario);
    }
}
