package com.kiver.fireretro;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class AndroidAppInstaller {
    interface Callback { void onProgress(long done, long total); void onReady(File apk); void onError(String message); }
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private AndroidAppInstaller() { }

    static void download(Context context, String source, String token, long expectedSize, String expectedSha, Callback callback) {
        if (!AndroidAppSource.isAllowed(source)) { callback.onError("Fonte de APK inválida"); return; }
        EXECUTOR.submit(() -> {
            File root = context.getExternalFilesDir("apps"); if (root == null) { callback.onError("Armazenamento indisponível"); return; }
            root.mkdirs(); File part = new File(root, "download.apk.part"); File apk = new File(root, "download.apk"); HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection)new URL(source).openConnection(); connection.setConnectTimeout(15000); connection.setReadTimeout(60000); connection.setRequestProperty("User-Agent", "JogosRetro/1.0");
                if (token != null && !token.trim().isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token.trim());
                int status = connection.getResponseCode(); if (status < 200 || status >= 300) throw new Exception("Download indisponível (" + status + ")");
                long total = expectedSize > 0 ? expectedSize : connection.getContentLengthLong(); long done = 0; MessageDigest digest = MessageDigest.getInstance("SHA-256"); byte[] buffer = new byte[32768];
                InputStream input = new BufferedInputStream(connection.getInputStream()); BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(part, false));
                try { int read; while ((read = input.read(buffer)) != -1) { output.write(buffer, 0, read); digest.update(buffer, 0, read); done += read; final long progress = done; new Handler(Looper.getMainLooper()).post(() -> callback.onProgress(progress, total)); } }
                finally { input.close(); output.close(); }
                if (expectedSize > 0 && done != expectedSize) throw new Exception("Tamanho recebido diferente do catálogo");
                if (expectedSha != null && expectedSha.length() == 64 && !hex(digest.digest()).equalsIgnoreCase(expectedSha)) throw new Exception("Verificação SHA-256 falhou");
                if (!part.renameTo(apk)) throw new Exception("Não foi possível preparar o APK");
                new Handler(Looper.getMainLooper()).post(() -> callback.onReady(apk));
            } catch (Exception error) { part.delete(); new Handler(Looper.getMainLooper()).post(() -> callback.onError(error.getMessage() == null ? "Falha no download" : error.getMessage())); }
            finally { if (connection != null) connection.disconnect(); }
        });
    }

    static void install(Context context, File apk) throws Exception {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        int sessionId = installer.createSession(params); PackageInstaller.Session session = installer.openSession(sessionId);
        try { BufferedInputStream input = new BufferedInputStream(new FileInputStream(apk)); java.io.OutputStream output = session.openWrite("base.apk", 0, apk.length()); try { byte[] buffer = new byte[32768]; int read; while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read); session.fsync(output); } finally { input.close(); output.close(); } PendingIntent pending = PendingIntent.getBroadcast(context, sessionId, new Intent(context, InstallStatusReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE); session.commit(pending.getIntentSender()); } catch (Exception error) { session.abandon(); throw error; } finally { session.close(); }
    }
    static void verifyPrivateApk(Context context, File apk, String expectedPackage, String expectedCertificateSha256) throws Exception {
        String expected = expectedCertificateSha256 == null ? "" : expectedCertificateSha256.replace(":", "").trim().toLowerCase();
        if (!expected.matches("[a-f0-9]{64}")) throw new Exception("Impressão digital do certificado não está cadastrada");
        PackageInfo info = context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
        if (info == null || !expectedPackage.equals(info.packageName)) throw new Exception("Nome do pacote do APK não corresponde ao catálogo");
        if (info.signingInfo == null) throw new Exception("Assinatura do APK não pôde ser lida");
        Signature[] signers = info.signingInfo.getApkContentsSigners();
        if (signers == null || signers.length == 0) throw new Exception("APK sem certificado de assinatura");
        for (Signature signer : signers) if (expected.equals(hex(MessageDigest.getInstance("SHA-256").digest(signer.toByteArray())))) return;
        throw new Exception("Certificado do APK é diferente do certificado aprovado");
    }
    static long archiveVersionCode(Context context, File apk) throws Exception {
        PackageInfo info = context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        if (info == null) throw new Exception("Versão do APK não pôde ser lida");
        return info.getLongVersionCode();
    }
    static boolean installedSignerMatches(Context context, String expectedCertificateSha256) throws Exception {
        String expected = expectedCertificateSha256 == null ? "" : expectedCertificateSha256.replace(":", "").trim().toLowerCase();
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
        if (info.signingInfo == null || info.signingInfo.getApkContentsSigners() == null) return false;
        for (Signature signer : info.signingInfo.getApkContentsSigners()) if (expected.equals(hex(MessageDigest.getInstance("SHA-256").digest(signer.toByteArray())))) return true;
        return false;
    }
    private static String hex(byte[] bytes) { StringBuilder result = new StringBuilder(); for (byte value : bytes) result.append(String.format("%02x", value & 255)); return result.toString(); }
}
