package com.kiver.fireretro;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Stable controller model identity and RetroArch health rules. */
final class ControllerProfile {
    private ControllerProfile() { }

    static String modelKey(String name, int vendorId, int productId) {
        String safe = name == null ? "controle" : name.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-");
        if (vendorId != 0 || productId != 0) return vendorId + ":" + productId;
        return safe.isEmpty() ? "controle" : safe;
    }

    static boolean canCopy(String sourceModel, String targetModel) {
        return sourceModel != null && !sourceModel.isEmpty() && sourceModel.equals(targetModel);
    }

    static boolean healthy(String config) {
        if (config == null) return false;
        return config.contains("input_player1_analog_dpad_mode = \"3\"")
                && config.contains("input_menu_toggle_gamepad_combo = \"7\"")
                && config.contains("input_quit_gamepad_combo = \"4\"")
                && config.contains("input_exit_emulator_btn = \"nul\"")
                && config.contains("quit_press_twice = \"false\"");
    }

    /** Replace only the launcher-owned safety settings and retain every other RetroArch line. */
    static String mergeSafeSettings(String config) {
        Map<String, String> required = new LinkedHashMap<>();
        required.put("input_player1_analog_dpad_mode", "3");
        required.put("input_menu_toggle_gamepad_combo", "7");
        required.put("input_quit_gamepad_combo", "4");
        required.put("input_exit_emulator", "nul");
        required.put("input_exit_emulator_axis", "nul");
        required.put("input_exit_emulator_btn", "nul");
        required.put("input_exit_emulator_mbtn", "nul");
        required.put("quit_press_twice", "false");
        Set<String> applied = new HashSet<>();
        StringBuilder result = new StringBuilder();
        if (config != null) {
            String[] lines = config.split("\\r?\\n", -1);
            for (String line : lines) {
                String trimmed = line.trim();
                int equals = trimmed.indexOf('=');
                if (equals > 0) {
                    String key = trimmed.substring(0, equals).trim();
                    if (required.containsKey(key)) {
                        if (applied.add(key)) {
                            result.append(key).append(" = ").append((char) 34).append(required.get(key)).append((char) 34).append('\n');
                        }
                        continue;
                    }
                }
                if (!line.isEmpty() || result.length() > 0) result.append(line).append('\n');
            }
        }
        for (Map.Entry<String, String> entry : required.entrySet()) {
            if (!applied.contains(entry.getKey())) result.append(entry.getKey()).append(" = ").append((char) 34).append(entry.getValue()).append((char) 34).append('\n');
        }
        return result.toString();
    }
}
