package com.kiver.fireretro;

import android.os.StatFs;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Downloads verified private ROMs sequentially while the launcher remains usable. */
public final class CloudLibrarySync {
    public interface Listener {
        void onProgress(String id, String label, long downloaded, long total, int queueRemaining);
        void onItemInstalled(String id, String label);
        void onError(String id, String label, String message);
        void onIdle(String revision, int pendingCount);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final CloudDeviceClient api;
    private final File romRoot, catalogFile, coverRoot, cacheFile, etagFile, appsFile, themesFile, assignmentFile, updateFile;
    private volatile boolean stopped;
    private volatile boolean running;

    public CloudLibrarySync(CloudDeviceClient api, File romRoot, File catalogFile, File coverRoot) {
        this.api = api; this.romRoot = romRoot; this.catalogFile = catalogFile; this.coverRoot = coverRoot;
        this.cacheFile = new File(catalogFile.getParentFile(), "cloud-catalog.json");
        this.etagFile = new File(catalogFile.getParentFile(), "cloud-catalog.etag");
        this.appsFile = new File(catalogFile.getParentFile(), "apps.json");
        this.themesFile = new File(catalogFile.getParentFile(), "themes.json");
        this.assignmentFile = new File(catalogFile.getParentFile(), "theme-assignment.txt");
        this.updateFile = new File(catalogFile.getParentFile(), "launcher-update.json");
    }

    public void start(final String deviceToken, final long reserveBytes, final Listener listener) {
        stopped = false;
        running = true;
        executor.submit(new Runnable() { @Override public void run() { try { sync(deviceToken, reserveBytes, listener); } finally { running = false; } } });
    }

    public void stop() { stopped = true; }
    public void close() { stopped = true; executor.shutdownNow(); }
    public boolean isRunning() { return running; }

    public void installItem(final String deviceToken, final String itemId, final long reserveBytes, final Listener listener) {
        if (running) { listener.onError(itemId, "Jogo", "A biblioteca já está executando outra operação"); return; }
        stopped = false; running = true;
        executor.submit(new Runnable() { @Override public void run() {
            try {
                if (!cacheFile.isFile()) throw new Exception("Sincronize o catálogo antes de instalar");
                JSONObject root = new JSONObject(readAll(new FileInputStream(cacheFile)));
                JSONArray items = root.optJSONArray("items"); JSONObject selected = null;
                if (items != null) for (int i = 0; i < items.length(); i++) { JSONObject candidate = items.optJSONObject(i); if (candidate != null && itemId.equals(candidate.optString("id")) && "rom".equals(candidate.optString("kind"))) { selected = candidate; break; } }
                if (selected == null) throw new Exception("O jogo não está mais disponível no catálogo");
                install(selected, deviceToken, reserveBytes, 0, listener);
                listener.onIdle(root.optString("revision", ""), 0);
            } catch (Exception error) { listener.onError(itemId, "Jogo", message(error)); }
            finally { running = false; }
        } });
    }

    private void sync(String token, long reserveBytes, Listener listener) {
        try {
            String previousEtag = etagFile.isFile() ? readAll(new FileInputStream(etagFile)).trim() : "";
            JSONObject catalog = api.catalog(token, previousEtag);
            if (catalog.optBoolean("_notModified", false)) {
                if (!cacheFile.isFile()) throw new Exception("O servidor informou catálogo inalterado, mas o cache local não existe");
                catalog = new JSONObject(readAll(new FileInputStream(cacheFile)));
            } else {
                String nextEtag = catalog.optString("_etag", "");
                catalog.remove("_etag"); catalog.remove("_httpStatus");
                writeAtomically(cacheFile, catalog.toString().getBytes("UTF-8"));
                if (!nextEtag.isEmpty()) writeAtomically(etagFile, nextEtag.getBytes("UTF-8"));
            }
            reportDeviceState(token, catalog.optString("revision", ""));
            JSONArray items = catalog.optJSONArray("items");
            if (items == null) throw new Exception("Catálogo da biblioteca está malformado");
            writeApps(items);
            writeThemes(items, token, listener);
            writeAtomically(assignmentFile, catalog.optString("assignedThemeId", "").getBytes("UTF-8"));
            writeLauncherUpdate(items);
            int pending = 0;
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null || !"rom".equals(item.optString("kind"))) continue;
                if (safeDestination(item.optString("path", "")) == null || !validSha(item.optString("sha256", "")) || item.optLong("size", -1) <= 0) {
                    listener.onError(item.optString("id", ""), item.optString("label", "Jogo"), "Item ignorado: caminho, tamanho ou hash inválido");
                    continue;
                }
                File destination = safeDestination(item.optString("path", ""));
                // Existing local files are user data. Never replace or delete them from a cloud sync.
                if (destination.exists()) { JSONObject local = new JSONObject(item.toString()); local.put("remoteAvailable", false); local.put("cloudId", item.optString("id", "")); mergeCatalog(local); }
                else { pending += 1; publishRemoteCard(item, token, listener); }
            }
            listener.onIdle(catalog.optString("revision", ""), pending);
        } catch (Exception error) {
            listener.onError("catalog", "Catálogo", message(error));
            listener.onIdle("", 0);
        }
    }

    private void publishRemoteCard(JSONObject item, String token, Listener listener) throws Exception {
        JSONObject remote = new JSONObject(item.toString());
        remote.put("cloudId", item.optString("id", "")); remote.put("remoteAvailable", true); remote.put("downloadedAt", 0L);
        String image = downloadCover(item, token, listener); if (!image.isEmpty()) remote.put("image", image);
        mergeCatalog(remote);
    }

    private void reportDeviceState(String token, String revision) {
        try {
            StatFs stat = new StatFs(romRoot.getAbsolutePath());
            JSONObject state = new JSONObject();
            state.put("deviceId", api.deviceId());
            state.put("model", Build.MANUFACTURER + " " + Build.MODEL);
            state.put("androidApi", Build.VERSION.SDK_INT);
            state.put("abi", Build.SUPPORTED_ABIS.length == 0 ? "" : Build.SUPPORTED_ABIS[0]);
            state.put("launcherVersion", api.appVersion());
            state.put("freeBytes", stat.getAvailableBlocksLong() * stat.getBlockSizeLong());
            state.put("catalogRevision", revision);
            api.reportState(token, state);
        } catch (Exception ignored) {
            // Status reporting must never prevent offline use or catalog downloads.
        }
    }

    private void writeLauncherUpdate(JSONArray items) throws Exception {
        JSONObject latest = new JSONObject(); int newest = -1;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i); if (item == null || !"launcher".equals(item.optString("kind"))) continue;
            int code = item.optInt("versionCode", 0); if (code > newest) { newest = code; latest = new JSONObject(item.toString()); }
        }
        writeAtomically(updateFile, latest.toString().getBytes("UTF-8"));
    }

    private void writeApps(JSONArray catalogItems) throws Exception {
        JSONObject root = new JSONObject(); JSONArray apps = new JSONArray(); root.put("version", 1);
        for (int i = 0; i < catalogItems.length(); i++) {
            JSONObject item = catalogItems.optJSONObject(i); if (item == null) continue;
            String kind = item.optString("kind", "");
            if (!"android-app".equals(kind) && !"android-game".equals(kind)) continue;
            JSONObject entry = new JSONObject();
            entry.put("title", item.optString("label", "Aplicativo"));
            entry.put("package", item.optString("packageName", ""));
            entry.put("category", item.optString("category", "app"));
            String sourceType = item.optString("sourceType", "private-apk");
            entry.put("sourceType", sourceType);
            entry.put("storeType", item.optString("storeType", ""));
            entry.put("source", "store".equals(sourceType) ? item.optString("storeUrl", "") : item.optString("id", ""));
            entry.put("size", item.optLong("size", 0)); entry.put("sha256", item.optString("sha256", ""));
            entry.put("certificateSha256", item.optString("certificateSha256", ""));
            entry.put("minApi", item.optInt("minApi", 28)); apps.put(entry);
        }
        root.put("items", apps); writeAtomically(appsFile, root.toString().getBytes("UTF-8"));
    }

    private void writeThemes(JSONArray catalogItems, String token, Listener listener) throws Exception {
        JSONArray themes = new JSONArray();
        for (int i = 0; i < catalogItems.length(); i++) {
            JSONObject item = catalogItems.optJSONObject(i); if (item == null || !"theme".equals(item.optString("kind"))) continue;
            JSONObject profile = item.optJSONObject("themeProfile"); if (profile == null) continue;
            JSONObject colors = profile.optJSONObject("colors"); if (colors == null) colors = new JSONObject();
            String backgroundAssetId = profile.optString("backgroundAssetId", "");
            String background = backgroundAssetId.matches("[A-Fa-f0-9-]{36}") ? downloadThemeBackground(backgroundAssetId, token, listener) : "";
            JSONObject theme = new JSONObject(); theme.put("id", profile.optString("id", item.optString("themeId", "")));
            theme.put("name", item.optString("label", "Tema")); theme.put("background", background);
            theme.put("accent", colors.optString("accent", "#38D9FF")); theme.put("backgroundColor", colors.optString("background", "#07183A"));
            theme.put("textColor", colors.optString("text", "#FFFFFF")); theme.put("title", profile.optString("title", item.optString("label", "Jogos Retro")));
            theme.put("subtitle", profile.optString("subtitle", "Clássicos para jogar")); theme.put("textSize", profile.optInt("textSize", 38));
            theme.put("shadow", profile.optInt("shadow", 70)); theme.put("overlay", profile.optInt("overlay", 68)); theme.put("density", profile.optInt("density", 4));
            theme.put("cardLayout", profile.optString("layout", "compact-grid")); theme.put("scale", "contain"); themes.put(theme);
        }
        writeAtomically(themesFile, themes.toString().getBytes("UTF-8"));
    }

    private String downloadThemeBackground(String assetId, String token, Listener listener) {
        File destination = new File(coverRoot, assetId + ".img"); if (destination.isFile()) return destination.getName();
        try {
            JSONObject ticket = api.downloadTicket(token, assetId); long size = ticket.optLong("size", -1); String hash = ticket.optString("sha256", "");
            if (size <= 0 || size > 25L * 1024L * 1024L || !validSha(hash)) return "";
            StatFs stat = new StatFs(romRoot.getAbsolutePath()); long free = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
            if (free - size < 350L * 1024L * 1024L) return "";
            coverRoot.mkdirs(); File part = new File(destination.getAbsolutePath() + ".part");
            try { download(ticket.optString("url", ""), part, size, hash, assetId, "Fundo do tema", 0, listener); return !destination.exists() && part.renameTo(destination) ? destination.getName() : ""; }
            finally { if (part.exists()) part.delete(); }
        } catch (Exception ignored) { return ""; }
    }

    private void install(JSONObject item, String token, long reserveBytes, int queueRemaining, Listener listener) throws Exception {
        if (stopped) return;
        String id = item.optString("id", ""), label = item.optString("label", "Jogo");
        File destination = safeDestination(item.optString("path", ""));
        if (destination == null) throw new Exception("Destino de jogo inválido");
        if (!validCore(item.optString("core_path", ""))) throw new Exception("Core de RetroArch inválido");
        long expected = item.optLong("size", -1);
        StatFs stat = new StatFs(romRoot.getAbsolutePath());
        long free = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
        if (free - expected < reserveBytes) throw new Exception("Espaço insuficiente; reservado o espaço de segurança do sistema");
        File parent = destination.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) throw new Exception("Não foi possível preparar a pasta do jogo");
        if (destination.exists()) return;
        File part = new File(destination.getAbsolutePath() + ".part");
        if (part.exists() && !part.delete()) throw new Exception("Não foi possível limpar um download temporário anterior");
        try {
            JSONObject ticket = api.downloadTicket(token, id);
            download(ticket.optString("url", ""), part, expected, item.optString("sha256", ""), id, label, queueRemaining, listener);
            if (destination.exists()) throw new Exception("O arquivo local apareceu durante o download; mantido sem alteração");
            if (!part.renameTo(destination)) throw new Exception("Não foi possível concluir atomicamente o arquivo");
            String image = downloadCover(item, token, listener);
            JSONObject local = new JSONObject(item.toString());
            local.put("downloadedAt", System.currentTimeMillis());
            local.put("cloudId", id); local.put("remoteAvailable", false);
            if (!image.isEmpty()) local.put("image", image);
            mergeCatalog(local);
            listener.onItemInstalled(id, label);
        } finally { if (part.exists()) part.delete(); }
    }

    private String downloadCover(JSONObject item, String token, Listener listener) {
        String coverId = item.optString("coverAssetId", "");
        if (!coverId.matches("[A-Fa-f0-9-]{36}")) return "";
        try {
            File destination = new File(coverRoot, coverId + ".img");
            if (destination.isFile()) return destination.getName();
            coverRoot.mkdirs();
            File part = new File(destination.getAbsolutePath() + ".part");
            try {
                JSONObject ticket = api.downloadTicket(token, coverId);
                long size = ticket.optLong("size", -1);
                String hash = ticket.optString("sha256", "");
                if (size <= 0 || !validSha(hash)) throw new Exception("Metadados da capa inválidos");
                download(ticket.optString("url", ""), part, size, hash, coverId, "Capa", 0, listener);
                if (!destination.exists() && part.renameTo(destination)) return destination.getName();
                return destination.isFile() ? destination.getName() : "";
            } finally { if (part.exists()) part.delete(); }
        } catch (Exception ignored) { return ""; }
    }

    private void download(String signedUrl, File part, long expectedSize, String expectedSha, String id, String label, int remaining, Listener listener) throws Exception {
        URL url = new URL(signedUrl);
        String host = url.getHost().toLowerCase();
        if (!"https".equalsIgnoreCase(url.getProtocol()) || !host.endsWith(".r2.cloudflarestorage.com")) throw new Exception("Link seguro de armazenamento inválido");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000); connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "JogosRetro-TV/1.0");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new Exception("Download indisponível (" + status + ")");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long received = 0; byte[] buffer = new byte[32768];
            try (InputStream input = new BufferedInputStream(connection.getInputStream()); BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(part, false))) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (stopped) throw new Exception("Download interrompido; arquivo parcial descartado");
                    output.write(buffer, 0, count); digest.update(buffer, 0, count); received += count;
                    listener.onProgress(id, label, received, expectedSize, remaining);
                }
            }
            if (received != expectedSize) throw new Exception("O tamanho recebido não confere com o catálogo");
            if (!hex(digest.digest()).equalsIgnoreCase(expectedSha)) throw new Exception("A verificação SHA-256 falhou; arquivo parcial descartado");
        } finally { connection.disconnect(); }
    }

    private void mergeCatalog(JSONObject item) throws Exception {
        JSONObject root = catalogFile.isFile() ? new JSONObject(readAll(new FileInputStream(catalogFile))) : new JSONObject();
        JSONArray items = root.optJSONArray("items");
        if (items == null) { items = new JSONArray(); root.put("version", 1); root.put("items", items); }
        String path = item.optString("path", "");
        for (int i = 0; i < items.length(); i++) if (path.equals(items.optJSONObject(i).optString("path"))) { items.put(i, item); catalogFile.getParentFile().mkdirs(); writeAtomically(catalogFile, root.toString().getBytes("UTF-8")); return; }
        items.put(item); catalogFile.getParentFile().mkdirs(); writeAtomically(catalogFile, root.toString().getBytes("UTF-8"));
    }

    private File safeDestination(String path) {
        if (path == null || !path.startsWith("/sdcard/roms/") || path.endsWith("/") || path.length() > 240) return null;
        String relative = path.substring("/sdcard/roms/".length());
        String[] parts = relative.split("/");
        for (String part : parts) if (part.equals(".") || part.equals("..") || part.matches(".*[^A-Za-z0-9._ ()-].*")) return null;
        try {
            File rootFile = romRoot.getCanonicalFile();
            File file = new File(rootFile, relative).getCanonicalFile();
            String root = rootFile.getPath() + File.separator;
            return file.getPath().startsWith(root) ? file : null;
        } catch (Exception ignored) { return null; }
    }

    private static boolean validCore(String path) { return path != null && path.matches("/data/user/0/[A-Za-z0-9_.]+/cores/[A-Za-z0-9_-]+\\.so"); }
    private static boolean validSha(String value) { return value != null && value.matches("[A-Fa-f0-9]{64}"); }
    private static String message(Exception error) { if (error instanceof CloudDeviceClient.CloudApiException && ((CloudDeviceClient.CloudApiException) error).status == 429) return "Limite diário temporariamente atingido. A biblioteca local continua disponível; tente novamente após 21:00 (Brasília)."; return error.getMessage() == null ? "Falha temporária na sincronização" : error.getMessage(); }
    private static String hex(byte[] bytes) { StringBuilder out = new StringBuilder(); for (byte b : bytes) out.append(String.format("%02x", b & 255)); return out.toString(); }
    private static String readAll(InputStream input) throws Exception { try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) { byte[] buffer = new byte[8192]; int n; while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n); return new String(out.toByteArray(), "UTF-8"); } }
    private static void writeAtomically(File file, byte[] bytes) throws Exception {
        File parent = file.getParentFile(); if (parent != null) parent.mkdirs();
        File part = new File(file.getAbsolutePath() + ".part");
        File backup = new File(file.getAbsolutePath() + ".bak");
        try (FileOutputStream out = new FileOutputStream(part, false)) { out.write(bytes); out.getFD().sync(); }
        if (backup.exists() && !backup.delete()) throw new Exception("Não foi possível preparar o cache anterior");
        boolean movedOld = file.exists() && file.renameTo(backup);
        if (!part.renameTo(file)) {
            if (movedOld) backup.renameTo(file);
            throw new Exception("Não foi possível salvar o catálogo em cache");
        }
        if (backup.exists()) backup.delete();
    }
}
