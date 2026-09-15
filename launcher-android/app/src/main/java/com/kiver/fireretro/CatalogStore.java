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
import java.util.List;

/** Loads the synchronized catalog while keeping the bundled catalog available offline. */
public final class CatalogStore {
    private static final String EXTERNAL_CATALOG = "/sdcard/Android/data/com.kiver.fireretro/files/catalog/games.json";
    private static final String ASSET_CATALOG = "assets/games.json";

    private CatalogStore() { }

    public static List<CatalogGame> load(Context context, File externalCatalog) {
        if (context == null) return Collections.emptyList();
        File external = externalCatalog != null
                ? (externalCatalog.isDirectory() ? new File(externalCatalog, "catalog/games.json") : externalCatalog)
                : new File(EXTERNAL_CATALOG);
        if (external.isFile()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(external))) {
                List<CatalogGame> games = parse(reader);
                if (!games.isEmpty()) return games;
            } catch (Exception ignored) { }
        }
        try {
            AssetManager assets = context.getAssets();
            try (InputStream stream = assets.open("games.json");
                 BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
                return parse(reader);
            }
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
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
                    item.optInt("year", 0), tags, item.optBoolean("publicDownload", false)));
        }
        return result;
    }

    public static final class CatalogGame {
        public final String label, path, corePath, platform, image, description;
        public final int year;
        public final List<String> tags;
        public final boolean publicDownload;

        CatalogGame(String label, String path, String corePath, String platform, String image,
                    String description, int year, List<String> tags, boolean publicDownload) {
            this.label = label; this.path = path; this.corePath = corePath; this.platform = platform;
            this.image = image; this.description = description; this.year = year;
            this.tags = Collections.unmodifiableList(new ArrayList<>(tags)); this.publicDownload = publicDownload;
        }
    }
}
