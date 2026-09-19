package com.kiver.fireretro;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.InputDevice;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keeps a local, non-sensitive history of controller models and connection state. */
final class ControllerRegistry {
    private static final String PREFS = "controller_registry";
    private static final String KEY = "devices";
    private ControllerRegistry() { }

    static final class DeviceInfo {
        final String name, model;
        final boolean connected;
        DeviceInfo(String name, String model, boolean connected) { this.name = name; this.model = model; this.connected = connected; }
    }

    static void observe(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray old = read(prefs); JSONArray next = new JSONArray();
        long now = System.currentTimeMillis();
        try {
            for (int i = 0; i < old.length(); i++) { JSONObject item = old.optJSONObject(i); if (item != null) { item.put("connected", false); next.put(item); } }
            for (int deviceId : InputDevice.getDeviceIds()) {
                InputDevice device = InputDevice.getDevice(deviceId); if (device == null || device.isVirtual()) continue;
                int sources = device.getSources();
                String deviceName = device.getName() == null ? "" : device.getName();
                String lowerName = deviceName.toLowerCase();
                boolean gameController = (sources & InputDevice.SOURCE_GAMEPAD) != 0 || (sources & InputDevice.SOURCE_JOYSTICK) != 0;
                boolean fireRemote = (sources & InputDevice.SOURCE_DPAD) != 0 && (lowerName.contains("amazon") || lowerName.contains("fire") || lowerName.contains("woble") || lowerName.contains("remote"));
                if (!gameController && !fireRemote) continue;
                String model = ControllerProfile.modelKey(device.getName(), device.getVendorId(), device.getProductId());
                JSONObject item = null;
                for (int i = 0; i < next.length(); i++) if (model.equals(next.optJSONObject(i).optString("model"))) { item = next.optJSONObject(i); break; }
                if (item == null) { item = new JSONObject(); item.put("model", model); item.put("name", device.getName()); next.put(item); }
                item.put("connected", true); item.put("lastSeen", now); item.put("vendor", device.getVendorId()); item.put("product", device.getProductId());
            }
            prefs.edit().putString(KEY, next.toString()).apply();
        } catch (Exception ignored) { }
    }

    static String summary(Context context) {
        JSONArray items = read(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE));
        StringBuilder text = new StringBuilder(); int connected = 0;
        try {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i); if (item == null) continue;
                boolean online = item.optBoolean("connected", false); if (online) connected++;
                text.append("• ").append(item.optString("name", "Controle sem nome")).append("\n")
                        .append("  ").append(online ? "CONECTADO" : "DESCONECTADO").append(" · ")
                        .append("Perfil geral ").append(online ? "pronto" : "aguardando reconexão").append("\n")
                        .append("  Analógicos → direcionais\n\n");
            }
        } catch (Exception ignored) { }
        if (text.length() == 0) text.append("Nenhum controle de jogo foi detectado pelo Fire OS.\n\nConecte ou pareie um controle e abra esta tela novamente.");
        return text.toString() + "\n" + connected + " conectado(s) · histórico local preservado";
    }

    static List<DeviceInfo> devices(Context context) {
        JSONArray items = read(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE));
        Map<String, DeviceInfo> grouped = new LinkedHashMap<>();
        try {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i); if (item == null) continue;
                String name = item.optString("name", "Controle sem nome"); String model = item.optString("model", "controle"); boolean connected = item.optBoolean("connected", false);
                String lower = name.toLowerCase(); String group = lower.contains("amazon") || lower.contains("fire") || lower.contains("woble") || lower.contains("remote") ? "fire-tv-remote" : model;
                DeviceInfo previous = grouped.get(group);
                if (previous == null || (!previous.connected && connected)) grouped.put(group, new DeviceInfo("fire-tv-remote".equals(group) ? "Controle remoto Fire TV" : name, group, connected));
            }
        } catch (Exception ignored) { }
        return new ArrayList<>(grouped.values());
    }

    private static JSONArray read(SharedPreferences prefs) { try { return new JSONArray(prefs.getString(KEY, "[]")); } catch (Exception ignored) { return new JSONArray(); } }
}
