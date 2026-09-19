package com.kiver.fireretro;

public final class TvFocusCoordinatorTest {
    public static void main(String[] args) {
        if (TvFocusCoordinator.nextCard(0, TvFocusCoordinator.LEFT, 4, 10) != TvFocusCoordinator.SIDEBAR) throw new AssertionError("first column must enter sidebar");
        if (TvFocusCoordinator.nextCard(5, TvFocusCoordinator.LEFT, 4, 10) != 4) throw new AssertionError("left must stay in row");
        if (TvFocusCoordinator.nextCard(5, TvFocusCoordinator.RIGHT, 4, 10) != 6) throw new AssertionError("right must stay in row");
        if (TvFocusCoordinator.nextCard(7, TvFocusCoordinator.RIGHT, 4, 10) != 7) throw new AssertionError("right edge must not wrap rows");
        if (TvFocusCoordinator.nextCard(2, TvFocusCoordinator.DOWN, 4, 10) != 6) throw new AssertionError("down must preserve column");
        if (TvFocusCoordinator.nextCard(6, TvFocusCoordinator.DOWN, 4, 10) != 9) throw new AssertionError("last short row must clamp");
        if (TvFocusCoordinator.nextCard(6, TvFocusCoordinator.UP, 4, 10) != 2) throw new AssertionError("up must preserve column");
        if (TvFocusCoordinator.nextCard(1, TvFocusCoordinator.UP, 4, 10) != 1) throw new AssertionError("top edge must stay put");
        System.out.println("PASS: TV focus coordinator");
    }
}
