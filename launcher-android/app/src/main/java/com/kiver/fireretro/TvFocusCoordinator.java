package com.kiver.fireretro;

/** Deterministic grid movement used by both D-pad and analog navigation. */
final class TvFocusCoordinator {
    static final int SIDEBAR = -1;
    static final int LEFT = 0;
    static final int RIGHT = 1;
    static final int UP = 2;
    static final int DOWN = 3;

    private TvFocusCoordinator() { }

    static int nextCard(int current, int direction, int columns, int itemCount) {
        if (current < 0 || current >= itemCount || columns < 1) return current;
        int column = current % columns;
        if (direction == LEFT) return column == 0 ? SIDEBAR : current - 1;
        if (direction == RIGHT) return (column == columns - 1 || current + 1 >= itemCount) ? current : current + 1;
        if (direction == UP) return current < columns ? current : current - columns;
        if (direction == DOWN) {
            if (current + columns < itemCount) return current + columns;
            int lastRowStart = ((itemCount - 1) / columns) * columns;
            if (lastRowStart <= current) return current;
            return Math.min(lastRowStart + column, itemCount - 1);
        }
        return current;
    }

    /** Horizontal movement must use the rendered row, not the flat catalogue order. */
    static int nextVisualColumn(int currentColumn, int rowSize, int direction) {
        if (currentColumn < 0 || currentColumn >= rowSize || rowSize < 1) return currentColumn;
        if (direction == LEFT) return currentColumn == 0 ? SIDEBAR : currentColumn - 1;
        if (direction == RIGHT) return currentColumn + 1 >= rowSize ? currentColumn : currentColumn + 1;
        return currentColumn;
    }
}
