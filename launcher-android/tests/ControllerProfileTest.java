package com.kiver.fireretro;

public final class ControllerProfileTest {
    public static void main(String[] args) {
        if (!ControllerProfile.canCopy(ControllerProfile.modelKey("Wireless Controller", 1118, 654), "1118:654")) throw new AssertionError("same model must copy");
        if (ControllerProfile.canCopy("1118:654", "054c:09cc")) throw new AssertionError("different model must not copy");
        if (ControllerProfile.healthy("input_player1_analog_dpad_mode = \"3\"\ninput_menu_toggle_gamepad_combo = \"7\"\ninput_quit_gamepad_combo = \"4\"\nquit_press_twice = \"false\"\n") == false) throw new AssertionError("healthy profile rejected");
        if (ControllerProfile.healthy("input_player1_analog_dpad_mode = \"0\"")) throw new AssertionError("bad profile accepted");
        String merged = ControllerProfile.mergeSafeSettings("# keep\ninput_player1_analog_dpad_mode = \"0\"\ninput_custom = \"keep\"\n");
        if (!merged.contains("input_player1_analog_dpad_mode = \"3\"")) throw new AssertionError("analog D-pad mode must be repaired");
        if (!merged.contains("input_custom = \"keep\"")) throw new AssertionError("unrelated settings must be preserved");
        if (merged.indexOf("input_player1_analog_dpad_mode") != merged.lastIndexOf("input_player1_analog_dpad_mode")) throw new AssertionError("setting must not be duplicated");
        System.out.println("PASS: controller model matching and RetroArch health");
    }
}
