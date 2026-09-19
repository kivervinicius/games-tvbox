package com.kiver.fireretro;

import static com.kiver.fireretro.DownloadStateMachine.State.*;

public final class DownloadStateMachineTest {
    public static void main(String[] args) {
        testHappyPath();
        testPauseResume();
        testFailureRetry();
        testInvalidTransitions();
        System.out.println("PASS: DownloadStateMachine tests passed");
    }

    private static void testHappyPath() {
        DownloadStateMachine machine = new DownloadStateMachine();
        assertEquals(QUEUED, machine.getState(), "Starts queued");
        machine.transition(DOWNLOADING);
        machine.transition(VERIFYING);
        assertTrue(machine.isActive(), "Verifying is active");
        machine.transition(INSTALLING);
        machine.transition(READY);
        assertTrue(machine.isTerminal(), "Ready is terminal");
        assertTrue(!machine.isActive(), "Ready is not active");
    }

    private static void testPauseResume() {
        DownloadStateMachine machine = new DownloadStateMachine();
        machine.transition(PAUSED);
        assertTrue(!machine.isActive() && !machine.isTerminal(), "Paused is parked");
        machine.transition(DOWNLOADING);
        machine.transition(PAUSED);
        machine.transition(QUEUED);
        machine.transition(DOWNLOADING);
        assertEquals(DOWNLOADING, machine.getState(), "Re-queued download resumes");
    }

    private static void testFailureRetry() {
        DownloadStateMachine machine = new DownloadStateMachine();
        machine.transition(DOWNLOADING);
        machine.transition(FAILED);
        assertTrue(machine.isTerminal(), "Failed is terminal until retry");
        machine.transition(QUEUED);
        assertEquals(QUEUED, machine.getState(), "Failed retries via queued");
    }

    private static void testInvalidTransitions() {
        assertTrue(!DownloadStateMachine.canTransition(QUEUED, READY), "No queued->ready skip");
        assertTrue(!DownloadStateMachine.canTransition(VERIFYING, DOWNLOADING), "No verifying rollback");
        assertTrue(!DownloadStateMachine.canTransition(READY, QUEUED), "Ready never reopens");
        assertTrue(!DownloadStateMachine.canTransition(null, QUEUED), "Null from rejected");
        DownloadStateMachine machine = new DownloadStateMachine();
        boolean thrown = false;
        try {
            machine.transition(READY);
        } catch (IllegalStateException expected) {
            thrown = true;
        }
        assertTrue(thrown, "Invalid transition throws");
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
