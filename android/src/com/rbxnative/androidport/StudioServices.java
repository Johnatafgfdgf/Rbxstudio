package com.rbxnative.androidport;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.io.IOException;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Android services used by the original GPUI platform contract. */
public final class StudioServices {
    private static final String ALIAS = "rbx-native-studio-credentials-v1";
    private StudioServices() {}

    public static String resourcePaths(Activity activity) {
        StringBuilder paths = new StringBuilder(new File(activity.getFilesDir(), "resources").getAbsolutePath());
        try {
            ApplicationInfo installed = activity.getPackageManager().getApplicationInfo("com.roblox.client", 0);
            if (installed.sourceDir != null) paths.append(File.pathSeparator).append(installed.sourceDir);
            if (installed.splitSourceDirs != null) for (String path : installed.splitSourceDirs) paths.append(File.pathSeparator).append(path);
        } catch (PackageManager.NameNotFoundException absent) { /* Download official packages on demand. */ }
        return paths.toString();
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }

    /** Opaque password bytes and the original username survive process death.
     * Neither key material nor plaintext credentials are written to preferences.
     */
    public static synchronized String credential(Activity activity, String operation,
            String service, String username, String passwordHex) throws Exception {
        SharedPreferences prefs = activity.getSharedPreferences("studio-credentials-v1", Context.MODE_PRIVATE);
        byte[] associated = service.getBytes(StandardCharsets.UTF_8);
        String name = hex(MessageDigest.getInstance("SHA-256").digest(associated));
        if (operation.equals("delete")) {
            if (!prefs.edit().remove(name).commit()) throw new IOException("Credential removal failed");
            return null;
        }
        if (operation.equals("write")) {
            // Validate before storing, including zero and non-UTF8 password bytes.
            unhex(passwordHex);
            JSONObject plain = new JSONObject().put("username", username).put("password", passwordHex);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            cipher.updateAAD(associated);
            byte[] encrypted = cipher.doFinal(plain.toString().getBytes(StandardCharsets.UTF_8));
            String packed = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":"
                    + Base64.encodeToString(encrypted, Base64.NO_WRAP);
            if (!prefs.edit().putString(name, packed).commit()) throw new IOException("Credential storage failed");
            return null;
        }
        if (!operation.equals("read")) throw new IllegalArgumentException("Unknown credential operation");
        String packed = prefs.getString(name, null);
        if (packed == null) return null;
        String[] pieces = packed.split(":", -1);
        if (pieces.length != 2) throw new IOException("Invalid encrypted credential");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(pieces[0], Base64.NO_WRAP)));
        cipher.updateAAD(associated);
        JSONObject plain = new JSONObject(new String(cipher.doFinal(Base64.decode(pieces[1], Base64.NO_WRAP)), StandardCharsets.UTF_8));
        return hex(plain.getString("username").getBytes(StandardCharsets.UTF_8)) + ":" + plain.getString("password");
    }

    static String hex(byte[] bytes) {
        char[] alphabet = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            out[i * 2] = alphabet[(bytes[i] & 255) >>> 4];
            out[i * 2 + 1] = alphabet[bytes[i] & 15];
        }
        return new String(out);
    }

    static byte[] unhex(String text) {
        if ((text.length() & 1) != 0) throw new IllegalArgumentException("Invalid credential encoding");
        byte[] out = new byte[text.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int a = Character.digit(text.charAt(i * 2), 16), b = Character.digit(text.charAt(i * 2 + 1), 16);
            if (a < 0 || b < 0) throw new IllegalArgumentException("Invalid credential encoding");
            out[i] = (byte) ((a << 4) | b);
        }
        return out;
    }
}
