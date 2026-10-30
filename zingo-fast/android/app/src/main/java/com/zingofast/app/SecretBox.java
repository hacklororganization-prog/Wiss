package com.zingofast.app;

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

/** API keys encrypted with an AES-GCM key that never leaves the Android Keystore. */
final class SecretBox {
    private static final String ALIAS = "zingo_secrets", STORE = "AndroidKeyStore";

    private SecretBox() { }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance(STORE);
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE);
        g.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return g.generateKey();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("zingo_secrets", Context.MODE_PRIVATE);
    }

    static boolean set(Context c, String name, String value) {
        if (!name.matches("^[a-z_]{2,40}$")) return false;
        try {
            if (value == null || value.isEmpty()) { prefs(c).edit().remove(name).apply(); return true; }
            Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
            ci.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = ci.getIV(), ct = ci.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] all = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(ct, 0, all, iv.length, ct.length);
            prefs(c).edit().putString(name, iv.length + ":" + Base64.encodeToString(all, Base64.NO_WRAP)).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static String get(Context c, String name) {
        try {
            String v = prefs(c).getString(name, null);
            if (v == null) return "";
            int k = v.indexOf(':'), ivLen = Integer.parseInt(v.substring(0, k));
            byte[] all = Base64.decode(v.substring(k + 1), Base64.NO_WRAP);
            Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
            ci.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, all, 0, ivLen));
            return new String(ci.doFinal(all, ivLen, all.length - ivLen), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
