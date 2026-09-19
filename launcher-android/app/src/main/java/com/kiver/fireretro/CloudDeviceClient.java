package com.kiver.fireretro;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

/** Narrow client for the private Cloudflare API. It never handles Cloudflare account secrets. */
public final class CloudDeviceClient {
    private final Context context;
    private final String origin;
    private final String deviceId;

    public CloudDeviceClient(Context context, String configuredOrigin) {
        this.context = context.getApplicationContext();
        origin = CloudApiEndpoint.normalize(configuredOrigin);
        SharedPreferences preferences = this.context.getSharedPreferences("cloud_device", Context.MODE_PRIVATE);
        String saved = preferences.getString("device_id", "");
        if (saved.isEmpty()) {
            saved = UUID.randomUUID().toString();
            preferences.edit().putString("device_id", saved).apply();
        }
        deviceId = saved;
    }

    public boolean isConfigured() { return !origin.isEmpty(); }
    public String deviceId() { return deviceId; }
    public String appVersion() {
        try { return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName; }
        catch (Exception ignored) { return ""; }
    }

    public JSONObject startPairing() throws Exception {
        JSONObject body = new JSONObject();
        body.put("deviceId", deviceId);
        body.put("platform", "android");
        body.put("requestedType", "tv");
        body.put("model", Build.MANUFACTURER + " " + Build.MODEL);
        return request("POST", "/api/device/pair/start", null, body);
    }

    /** Returns status=waiting_for_approval until the administrator approves the TV. */
    public JSONObject completePairing(String pairId, String code) throws Exception {
        JSONObject body = new JSONObject();
        body.put("pairId", pairId);
        body.put("code", code);
        body.put("model", Build.MANUFACTURER + " " + Build.MODEL);
        return request("POST", "/api/device/pair/complete", null, body);
    }

    public JSONObject catalog(String deviceToken) throws Exception {
        return catalog(deviceToken, "");
    }

    public JSONObject catalog(String deviceToken, String etag) throws Exception {
        return request("GET", "/api/device/catalog", deviceToken, null, etag);
    }

    public JSONObject downloadTicket(String deviceToken, String itemId) throws Exception {
        if (itemId == null || !itemId.matches("[A-Za-z0-9_.:-]{8,100}")) throw new IllegalArgumentException("Invalid catalog item ID");
        JSONObject ticket = request("GET", "/api/device/download/" + itemId, deviceToken, null);
        URL signed = new URL(ticket.optString("url", ""));
        if (!"https".equalsIgnoreCase(signed.getProtocol()) || !signed.getHost().toLowerCase().endsWith(".r2.cloudflarestorage.com")) throw new SecurityException("Cloud download URL is not a private R2 endpoint");
        return ticket;
    }

    public JSONObject reportState(String deviceToken, JSONObject state) throws Exception {
        SharedPreferences preferences = context.getSharedPreferences("cloud_device", Context.MODE_PRIVATE);
        String fingerprint = state.toString();
        String previous = preferences.getString("state_fingerprint", "");
        long previousAt = preferences.getLong("state_reported_at", 0L);
        long now = System.currentTimeMillis();
        if (!DeviceStateThrottle.shouldReport(previous, previousAt, fingerprint, now)) {
            JSONObject skipped = new JSONObject(); skipped.put("accepted", true); skipped.put("written", false); skipped.put("localThrottle", true); return skipped;
        }
        JSONObject response = request("POST", "/api/device/state", deviceToken, state);
        preferences.edit().putString("state_fingerprint", fingerprint).putLong("state_reported_at", now).apply();
        return response;
    }

    private JSONObject request(String method, String route, String deviceToken, JSONObject body) throws Exception {
        return request(method, route, deviceToken, body, "");
    }

    private JSONObject request(String method, String route, String deviceToken, JSONObject body, String ifNoneMatch) throws Exception {
        if (origin.isEmpty()) throw new IllegalStateException("Cloudflare API endpoint is not configured in this build");
        String endpoint = CloudApiEndpoint.url(origin, route);
        if (endpoint.isEmpty()) throw new IllegalArgumentException("Invalid Cloudflare API route");
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "JogosRetro-TV/1.0");
            if (ifNoneMatch != null && !ifNoneMatch.trim().isEmpty()) connection.setRequestProperty("If-None-Match", ifNoneMatch.trim());
            if (deviceToken != null && !deviceToken.trim().isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + deviceToken.trim());
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] payload = body.toString().getBytes("UTF-8");
                try (OutputStream output = connection.getOutputStream()) { output.write(payload); }
            }
            int status = connection.getResponseCode();
            if (status == 304) { JSONObject unchanged = new JSONObject(); unchanged.put("_httpStatus", 304); unchanged.put("_notModified", true); return unchanged; }
            InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String response = read(input);
            JSONObject json = response.isEmpty() ? new JSONObject() : new JSONObject(response);
            json.put("_httpStatus", status);
            String responseEtag = connection.getHeaderField("ETag");
            if (responseEtag != null && !responseEtag.trim().isEmpty()) json.put("_etag", responseEtag.trim());
            if (status < 200 || status >= 300) throw new CloudApiException(status, json.optString("message", json.optString("error", "Cloud API request failed")), json);
            return json;
        } finally { connection.disconnect(); }
    }

    private static String read(InputStream input) throws Exception {
        if (input == null) return "";
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), "UTF-8");
        }
    }

    public static final class CloudApiException extends Exception {
        public final int status;
        public final JSONObject response;
        CloudApiException(int status, String message, JSONObject response) { super(message); this.status = status; this.response = response; }
    }
}
