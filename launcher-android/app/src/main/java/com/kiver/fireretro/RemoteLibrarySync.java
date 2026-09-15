package com.kiver.fireretro;

import android.os.StatFs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
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

/** Foreground, sequential synchronizer for a private GitHub release manifest. */
public final class RemoteLibrarySync {
    public interface Listener {
        void onProgress(String id, String label, long downloaded, long total, int queueRemaining);
        void onItemInstalled(String id, String label);
        void onError(String id, String label, String message);
        void onIdle();
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean stopped;
    private final File romRoot;
    private final File catalogFile;
    private final File coverRoot;

    public RemoteLibrarySync(File romRoot, File catalogFile, File coverRoot) {
        this.romRoot = romRoot;
        this.catalogFile = catalogFile;
        this.coverRoot = coverRoot;
    }

    public void start(final String manifestUrl, final String token, final long reserveBytes, final Listener listener) {
        stopped = false;
        executor.submit(new Runnable() {
            @Override public void run() { sync(manifestUrl, token, reserveBytes, listener); }
        });
    }

    public void stop() { stopped = true; }

    public void close() { stopped = true; executor.shutdownNow(); }

    private void sync(String manifestUrl, String token, long reserveBytes, Listener listener) {
        HttpURLConnection connection = null;
        try {
            if (manifestUrl == null || !manifestUrl.startsWith("https://")) throw new Exception("URL HTTPS inválida");
            connection = (HttpURLConnection) new URL(manifestUrl).openConnection();
            connection.setConnectTimeout(15000); connection.setReadTimeout(30000);
            connection.setRequestProperty("Accept", "application/json");
            if (token != null && !token.trim().isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token.trim());
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) throw new Exception("Catálogo remoto indisponível (" + connection.getResponseCode() + ")");
            JSONObject manifest = new JSONObject(readAll(connection.getInputStream()));
            JSONArray items = manifest.optJSONArray("items");
            if (items == null) throw new Exception("Formato de catálogo inválido");
            List<JSONObject> pending = new ArrayList<>();
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i); if (item == null) continue;
                String path = item.optString("path", "");
                if (!path.startsWith("/sdcard/roms/") || item.optLong("size", -1) < 0 || item.optString("sha256", "").length() != 64) continue;
                File destination = new File(path);
                if (!destination.isFile() || destination.length() != item.optLong("size")) pending.add(item);
            }
            for (int i = 0; i < pending.size() && !stopped; i++) {
                JSONObject item = pending.get(i); String id = item.optString("id", ""); String label = item.optString("label", "Jogo");
                try { install(item, token, reserveBytes, pending.size() - i - 1, listener); }
                catch (Exception error) { listener.onError(id, label, error.getMessage() == null ? "Falha no download" : error.getMessage()); }
            }
            listener.onIdle();
        } catch (Exception error) { listener.onError("catalog", "Catálogo", error.getMessage() == null ? "Falha na sincronização" : error.getMessage()); }
        finally { if (connection != null) connection.disconnect(); }
    }

    private void install(JSONObject item, String token, long reserveBytes, int queueRemaining, Listener listener) throws Exception {
        String id=item.optString("id", ""), label=item.optString("label", "Jogo"), path=item.optString("path", "");
        long expected=item.optLong("size", -1); if (expected < 0) throw new Exception("Tamanho inválido");
        StatFs stat = new StatFs(romRoot.getAbsolutePath());
        long freeSpace = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
        if (freeSpace - expected < reserveBytes) { listener.onError(id, label, "Espaço insuficiente"); return; }
        File destination = new File(path); File parent=destination.getParentFile(); if(parent==null) throw new Exception("Caminho inválido"); parent.mkdirs();
        File part = new File(destination.getAbsolutePath() + ".part");
        HttpURLConnection c=(HttpURLConnection)new URL(item.optString("downloadUrl", "")).openConnection();
        try {
            c.setConnectTimeout(15000); c.setReadTimeout(30000); if(token!=null&&!token.trim().isEmpty()) c.setRequestProperty("Authorization", "Bearer "+token.trim());
            if(c.getResponseCode()<200||c.getResponseCode()>=300) throw new Exception("Download indisponível ("+c.getResponseCode()+")");
            MessageDigest digest=MessageDigest.getInstance("SHA-256"); long done=0; byte[] buffer=new byte[32768];
            try(InputStream input=new BufferedInputStream(c.getInputStream()); BufferedOutputStream output=new BufferedOutputStream(new FileOutputStream(part,false))){
                int read; while((read=input.read(buffer))!=-1){ if(stopped) throw new Exception("Download interrompido"); output.write(buffer,0,read); digest.update(buffer,0,read); done+=read; listener.onProgress(id,label,done,expected,queueRemaining); }
            }
            if(done!=expected) throw new Exception("Tamanho recebido diferente do manifesto");
            if(!hex(digest.digest()).equalsIgnoreCase(item.optString("sha256"))) throw new Exception("Verificação SHA-256 falhou");
            if(!part.renameTo(destination)) throw new Exception("Não foi possível concluir o arquivo");
            installCover(item); mergeCatalog(item); listener.onItemInstalled(id,label);
        } finally { c.disconnect(); if(part.isFile()&&stopped) part.delete(); }
    }

    private void installCover(JSONObject item) {
        String image=item.optString("image", ""), url=item.optString("coverUrl", ""); if(image.isEmpty()||url.isEmpty()||!url.startsWith("https://")) return;
        File target=new File(coverRoot,new File(image).getName()); if(target.isFile()) return; coverRoot.mkdirs();
        try { HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection(); c.setConnectTimeout(10000); c.setReadTimeout(20000); if(c.getResponseCode()>=200&&c.getResponseCode()<300){try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(target)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}} c.disconnect(); } catch(Exception ignored) { }
    }

    private void mergeCatalog(JSONObject item) throws Exception {
        JSONObject root=catalogFile.isFile()?new JSONObject(readAll(new FileInputStream(catalogFile))):new JSONObject(); JSONArray items=root.optJSONArray("items"); if(items==null){items=new JSONArray();root.put("version",1);root.put("items",items);}
        String path=item.optString("path"); for(int i=0;i<items.length();i++) if(path.equals(items.optJSONObject(i).optString("path"))) return;
        JSONObject copy=new JSONObject(item.toString()); copy.put("downloadedAt", System.currentTimeMillis()); items.put(copy); catalogFile.getParentFile().mkdirs(); try(FileOutputStream out=new FileOutputStream(catalogFile)){out.write(root.toString().getBytes("UTF-8"));}
    }

    private static String readAll(InputStream in) throws Exception { StringBuilder s=new StringBuilder(); byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1)s.append(new String(b,0,n,"UTF-8")); in.close(); return s.toString(); }
    private static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format("%02x",b&255));return s.toString();}
}
