package com.kiver.fireretro;

import android.content.Context;
import android.content.res.AssetManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads the synchronized catalog while keeping the bundled catalog available offline. */
public final class CatalogStore {
    private static final String EXTERNAL_CATALOG = "/sdcard/Android/data/com.kiver.fireretro/files/catalog/games.json";
    private static final String ASSET_CATALOG = "games.json";

    private CatalogStore() { }

    public static List<CatalogGame> load(Context context, File externalCatalog) {
        if (context == null) return Collections.emptyList();
        File external = externalCatalog != null
                ? (externalCatalog.isDirectory() ? new File(externalCatalog, "catalog/games.json") : externalCatalog)
                : new File(EXTERNAL_CATALOG);
        List<CatalogGame> externalGames = Collections.emptyList();
        if (external.isFile()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(external))) {
                externalGames = parse(reader);
            } catch (Exception ignored) { }
        }
        List<CatalogGame> bundledGames = Collections.emptyList();
        try {
            AssetManager assets = context.getAssets();
            try (InputStream stream = assets.open(ASSET_CATALOG);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
                bundledGames = parse(reader);
            }
        } catch (Exception ignored) {
            // An external catalog can still be used when the app asset is unavailable.
        }
        return merge(bundledGames, externalGames);
    }

    static List<CatalogGame> merge(List<CatalogGame> bundled, List<CatalogGame> external) {
        Map<String, CatalogGame> byPath = new LinkedHashMap<>();
        for (CatalogGame game : bundled) if (game != null && !game.path.trim().isEmpty()) byPath.put(game.path, game);
        for (CatalogGame game : external) if (game != null && !game.path.trim().isEmpty()) byPath.put(game.path, game);
        return new ArrayList<>(byPath.values());
    }

    private static List<CatalogGame> parse(BufferedReader reader) throws Exception {
        StringBuilder text = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) text.append(line);
        JSONObject root = new JSONObject(text.toString());
        JSONArray items = root.optJSONArray("items");
        List<CatalogGame> result = new ArrayList<>();
        if (items == null) return result;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String path = item.optString("path", "");
            if (path.trim().isEmpty()) continue;
            List<String> tags = new ArrayList<>();
            JSONArray tagArray = item.optJSONArray("tags");
            if (tagArray != null) for (int j = 0; j < tagArray.length(); j++) tags.add(tagArray.optString(j, ""));
            result.add(new CatalogGame(item.optString("label", "Jogo"), path,
                    item.optString("core_path", ""), item.optString("platform", "Outros"),
                    item.optString("image", ""), item.optString("description", ""),
                    item.optInt("year", 0), tags, item.optBoolean("publicDownload", false), item.optLong("downloadedAt", 0L),
                    item.optString("cloudId", item.optString("id", "")), item.optBoolean("remoteAvailable", false), item.optLong("size", 0L)));
        }
        return result;
    }

    public static final class CatalogGame {
        public final String label, path, corePath, platform, image, description;
        public final int year;
        public final List<String> tags;
        public final boolean publicDownload; public final long downloadedAt;
        public final String cloudId; public final boolean remoteAvailable; public final long size;

        CatalogGame(String label, String path, String corePath, String platform, String image,
                    String description, int year, List<String> tags, boolean publicDownload, long downloadedAt,
                    String cloudId, boolean remoteAvailable, long size) {
            this.label = label; this.path = path; this.corePath = corePath; this.platform = platform;
            this.image = image; this.description = description; this.year = year;
            this.tags = Collections.unmodifiableList(new ArrayList<>(tags)); this.publicDownload = publicDownload; this.downloadedAt = downloadedAt;
            this.cloudId = cloudId; this.remoteAvailable = remoteAvailable; this.size = size;
        }
    }
}
