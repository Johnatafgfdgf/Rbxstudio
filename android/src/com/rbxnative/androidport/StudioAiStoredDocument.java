package com.rbxnative.androidport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Reads the username field returned by the existing encrypted credential service. */
final class StudioAiStoredDocument {
    static String read(String packed) throws IOException {
        return read(packed,180000);
    }
    static String read(String packed,int limit) throws IOException {
        if (packed == null) return "";
        int colon = packed.indexOf(':');
        if (colon <= 0) throw new IOException("Documento criptografado inválido.");
        String text = decode(packed.substring(0, colon));
        // R8 encoded checkpoints once before passing them to credential().
        if (text.matches("[0-9a-fA-F]+") && (text.length() & 1) == 0) {
            String decoded=decode(text);
            if(decoded.startsWith("{")||decoded.startsWith("["))text=decoded;
        }
        if (text.length() > limit) throw new IOException("Documento acima do limite local.");
        return text;
    }

    private static String decode(String hex) throws IOException {
        if ((hex.length() & 1) != 0) throw new IOException("Codificação inválida.");
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int high = Character.digit(hex.charAt(2 * i), 16);
            int low = Character.digit(hex.charAt(2 * i + 1), 16);
            if (high < 0 || low < 0) throw new IOException("Codificação inválida.");
            bytes[i] = (byte) ((high << 4) | low);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
