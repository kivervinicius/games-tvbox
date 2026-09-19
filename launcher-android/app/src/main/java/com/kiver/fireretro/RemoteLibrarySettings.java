package com.kiver.fireretro;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Per-device remote library settings. Token is kept out of catalog files and diagnostics. */
public final class RemoteLibrarySettings {
    public static final String DEFAULT_MANIFEST_URL = "https://raw.githubusercontent.com/kivervinicius/games-tvbox-library/main/library.manifest.json";
    private static final String PREFS = "remote_library";
    private static final String URL = "manifest_url";
    private static final String TOKEN = "read_token";
    private static final String CLOUD_DEVICE_TOKEN = "cloud_device_token";
    private static final String ENABLED = "enabled";
    private RemoteLibrarySettings() { }

    public static Settings load(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new Settings(p.getString(URL, DEFAULT_MANIFEST_URL), decrypt(p.getString(TOKEN, "")), p.getBoolean(ENABLED, false));
    }

    public static void save(Context context, Settings settings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(URL, settings.manifestUrl == null ? "" : settings.manifestUrl.trim())
                .putString(TOKEN, encrypt(settings.token == null ? "" : settings.token.trim()))
                .putBoolean(ENABLED, settings.enabled).apply();
    }

    public static String loadCloudDeviceToken(Context context) {
        return decrypt(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(CLOUD_DEVICE_TOKEN, ""));
    }

    public static void saveCloudDeviceToken(Context context, String token) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(CLOUD_DEVICE_TOKEN, encrypt(token == null ? "" : token.trim())).apply();
    }

    private static SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if(!store.containsAlias("FireRetroLibrary")){
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder("FireRetroLibrary", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            generator.generateKey();
        }
        return ((KeyStore.SecretKeyEntry)store.getEntry("FireRetroLibrary",null)).getSecretKey();
    }
    private static String encrypt(String value){ try { Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,key()); byte[] iv=c.getIV(), data=c.doFinal(value.getBytes(StandardCharsets.UTF_8)); byte[] all=new byte[iv.length+data.length]; System.arraycopy(iv,0,all,0,iv.length); System.arraycopy(data,0,all,iv.length,data.length); return Base64.encodeToString(all,Base64.NO_WRAP); } catch(Exception e){ return ""; } }
    private static String decrypt(String value){ try { if(value==null||value.isEmpty())return ""; byte[] all=Base64.decode(value,Base64.NO_WRAP); Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,all,0,12)); return new String(c.doFinal(all,12,all.length-12),StandardCharsets.UTF_8); } catch(Exception e){ return ""; } }

    public static final class Settings {
        public final String manifestUrl, token; public final boolean enabled;
        public Settings(String manifestUrl, String token, boolean enabled) { this.manifestUrl = manifestUrl; this.token = token; this.enabled = enabled; }
    }
}
