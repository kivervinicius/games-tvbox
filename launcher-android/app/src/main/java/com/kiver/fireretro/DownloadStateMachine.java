package com.kiver.fireretro;

/**
 * Download lifecycle: queued -> downloading -> verifying -> installing ->
 * ready, with paused/failed branches and retry back to queued.
 * UI shells observe state; they never infer it from byte counters.
 */
public final class DownloadStateMachine {
    public enum State {
        QUEUED,
        DOWNLOADING,
        VERIFYING,
        INSTALLING,
        READY,
        FAILED,
        PAUSED
    }

    private State state;

    public DownloadStateMachine() {
        this.state = State.QUEUED;
    }

    public State getState() {
        return state;
    }

    public boolean isTerminal() {
        return state == State.READY || state == State.FAILED;
    }

    public boolean isActive() {
        return state == State.DOWNLOADING || state == State.VERIFYING || state == State.INSTALLING;
    }

    public static boolean canTransition(State from, State to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        switch (from) {
            case QUEUED:
                return to == State.DOWNLOADING || to == State.PAUSED || to == State.FAILED;
            case DOWNLOADING:
                return to == State.VERIFYING || to == State.PAUSED || to == State.FAILED;
            case VERIFYING:
                return to == State.INSTALLING || to == State.FAILED;
            case INSTALLING:
                return to == State.READY || to == State.FAILED;
            case PAUSED:
                return to == State.QUEUED || to == State.DOWNLOADING || to == State.FAILED;
            case FAILED:
                return to == State.QUEUED;
            case READY:
            default:
                return false;
        }
    }

    public void transition(State next) {
        if (!canTransition(state, next)) {
            throw new IllegalStateException("Invalid download transition " + state + " -> " + next);
        }
        state = next;
    }
}
