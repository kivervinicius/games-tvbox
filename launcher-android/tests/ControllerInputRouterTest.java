package com.kiver.fireretro;

public final class ControllerInputRouterTest {
    public static void main(String[] args) {
        ControllerInputRouter router = new ControllerInputRouter();

        if (!router.shouldMove(1, 20, ControllerInputRouter.ACTION_DOWN, 0, 1_000L)) throw new AssertionError("first press must move");
        if (router.shouldMove(1, 20, ControllerInputRouter.ACTION_DOWN, 0, 1_025L)) throw new AssertionError("duplicate DOWN must be ignored");
        if (router.shouldMove(1, 20, ControllerInputRouter.ACTION_DOWN, 1, 1_300L)) throw new AssertionError("hold must wait 450 ms");
        if (!router.shouldMove(1, 20, ControllerInputRouter.ACTION_DOWN, 2, 1_450L)) throw new AssertionError("hold must repeat after 450 ms");
        if (router.shouldMove(1, 20, ControllerInputRouter.ACTION_DOWN, 3, 1_520L)) throw new AssertionError("hold repeat must wait 140 ms");
        if (!router.shouldMove(1, 20, ControllerInputRouter.ACTION_DOWN, 4, 1_590L)) throw new AssertionError("hold repeat must use 140 ms interval");
        if (router.shouldMove(1, 20, ControllerInputRouter.ACTION_UP, 0, 1_610L)) throw new AssertionError("release never moves");
        if (!router.shouldMove(1, 20, ControllerInputRouter.ACTION_DOWN, 0, 1_640L)) throw new AssertionError("a new tap after release must move immediately");

        if (!router.shouldMove(2, 20, ControllerInputRouter.ACTION_DOWN, 0, 1_650L)) throw new AssertionError("devices must have independent state");
        System.out.println("PASS: controller input router");
    }
}
