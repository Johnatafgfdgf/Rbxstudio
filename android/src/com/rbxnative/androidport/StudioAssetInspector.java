package com.rbxnative.androidport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Bounded inspection of a file explicitly selected with Android's SAF picker.
 * Sniffs file bytes instead of trusting an extension or provider MIME type.
 * No network calls, disk writes, asset upload, code execution or AI transfer.
 */
public final class StudioAssetInspector {
    public static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
    public static final int MAX_AUDIO_BYTES = 16 * 1024 * 1024;
    private StudioAssetInspector() {}

    public static final class Inspected {
        public final String format;
        public final boolean image;
        public final boolean audio;
        public final int byteCount;
        public final String sha256;
        // Only keep image bytes for a bounded, downsampled local thumbnail.
        public final byte[] imageBytes;

        private Inspected(String format, boolean image, int byteCount,
                String sha256, byte[] imageBytes) {
            this.format = format;
            this.image = image;
            this.audio = !image;
            this.byteCount = byteCount;
            this.sha256 = sha256;
            this.imageBytes = imageBytes;
        }

        public String report() {
            return "Arquivo inspecionado localmente\n" +
                    "Tipo: " + (image ? "Imagem / textura VFX" : "Áudio / efeito sonoro") +
                    " (" + format + ")\n" +
                    "Tamanho: " + byteCount + " bytes\n" +
                    "SHA-256: " + sha256 + "\n" +
                    (format.equals("GIF")
                        ? "GIF: a prévia mostra só um quadro; não valida a animação.\n" : "") +
                    "Origem e licença NÃO verificadas. Antes de republicar é preciso " +
                    "comprovar os direitos e pedir sua autorização.\n" +
                    "Nenhum arquivo foi enviado à IA, inserido no jogo ou publicado no Roblox.";
        }
    }

    private static boolean prefix(byte[] b, byte[] pattern) {
        if (b.length < pattern.length) return false;
        for (int i = 0; i < pattern.length; i++)
            if (b[i] != pattern[i]) return false;
        return true;
    }

    private static boolean ascii(byte[] b, int offset, String s) {
        if (offset + s.length() > b.length) return false;
        for (int i = 0; i < s.length(); i++)
            if ((b[offset + i] & 255) != s.charAt(i)) return false;
        return true;
    }

    private static String format(byte[] b) {
        if (prefix(b, new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10}))
            return "PNG";
        if (b.length >= 3 && (b[0] & 255) == 255 &&
                (b[1] & 255) == 216 && (b[2] & 255) == 255)
            return "JPEG";
        if (ascii(b, 0, "RIFF") && ascii(b, 8, "WEBP")) return "WEBP";
        if (ascii(b, 0, "GIF87a") || ascii(b, 0, "GIF89a")) return "GIF";
        if (ascii(b, 0, "RIFF") && ascii(b, 8, "WAVE")) return "WAV";
        if (ascii(b, 0, "OggS")) return "OGG";
        if (ascii(b, 0, "fLaC")) return "FLAC";
        if (ascii(b, 0, "ID3") || (b.length > 1 &&
                (b[0] & 255) == 255 && ((b[1] & 224) == 224) &&
                ((b[1] & 6) != 0))) return "MP3";
        return "";
    }

    private static boolean image(String fmt) {
        return fmt.equals("PNG") || fmt.equals("JPEG") ||
                fmt.equals("WEBP") || fmt.equals("GIF");
    }

    public static Inspected inspect(InputStream source) throws IOException {
        if (source == null) throw new IOException("O arquivo não pôde ser aberto.");
        ByteArrayOutputStream memory = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IOException("SHA-256 indisponível", failure);
        }
        int size = 0;
        int n;
        while ((n = source.read(chunk)) != -1) {
            if (n == 0) continue;
            // Never read an arbitrarily large untrusted file into Android memory.
            if (n > MAX_AUDIO_BYTES - size)
                throw new IOException("Arquivo excede 16 MiB: leitura recusada.");
            memory.write(chunk, 0, n);
            digest.update(chunk, 0, n);
            size += n;
            if (size > MAX_IMAGE_BYTES) {
                byte[] bytes = memory.toByteArray();
                String type = format(bytes);
                if (image(type))
                    throw new IOException("Imagem excede 8 MiB: leitura recusada.");
            }
        }
        byte[] bytes = memory.toByteArray();
        String type = format(bytes);
        if (type.isEmpty())
            throw new IOException("Formato não reconhecido ou não permitido. SVG, HTML, ZIP, vídeos e executáveis não são aceitos.");
        boolean isImage = image(type);
        if (isImage && bytes.length > MAX_IMAGE_BYTES)
            throw new IOException("Imagem excede 8 MiB: leitura recusada.");
        StringBuilder hash = new StringBuilder(64);
        for (byte item : digest.digest())
            hash.append(String.format(Locale.ROOT, "%02x", item & 255));
        return new Inspected(type, isImage, size, hash.toString(),
                isImage ? bytes : null);
    }

    /**
     * Technical pixel indicators on a downsampled image. An image with
     * particles intentionally drawn on black or transparency may trigger
     * a warning: this is NOT an aesthetic or semantic review.
     */
    public static String pixelSignals(int[] argb, int width, int height) {
        if (argb == null || width < 1 || height < 1 ||
                (long) width * height != argb.length ||
                argb.length > 400000)
            throw new IllegalArgumentException("Amostra de imagem inválida");
        int opaque = 0, dark = 0, bright = 0, transparent = 0;
        long edgeTotal = 0;
        int edgePairs = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x, px = argb[idx];
                int alpha = px >>> 24;
                if (alpha < 24) { transparent++; continue; }
                opaque++;
                int r = (px >>> 16) & 255, g = (px >>> 8) & 255, b = px & 255;
                int luminance = (77 * r + 150 * g + 29 * b) >> 8;
                if (luminance <= 28) dark++;
                if (luminance >= 232) bright++;
                if (x > 0 && (argb[idx - 1] >>> 24) >= 24) {
                    int other = argb[idx - 1];
                    int l2 = (77 * ((other >>> 16) & 255) +
                            150 * ((other >>> 8) & 255) + 29 * (other & 255)) >> 8;
                    edgeTotal += Math.abs(luminance - l2);
                    edgePairs++;
                }
            }
        }
        int total = argb.length;
        StringBuilder out = new StringBuilder("Sinais técnicos dos pixels da prévia local:\n");
        out.append("Transparência na amostra: ")
                .append(100 * transparent / total).append("%\n");
        if (opaque == 0) {
            out.append("Alerta: a amostra não contém pixels visíveis.\n");
        } else {
            out.append("Pixels visíveis escuros: ").append(100 * dark / opaque).append("%\n");
            out.append("Pixels visíveis muito claros: ").append(100 * bright / opaque).append("%\n");
            if (dark * 100L > opaque * 65L)
                out.append("Atenção: predominam pixels escuros; em VFX isso pode ser intencional.\n");
            if (bright * 100L > opaque * 65L)
                out.append("Atenção: predominam pixels muito claros; confirme a exposição na cena.\n");
            if (edgePairs > 50 && edgeTotal / edgePairs < 5)
                out.append("Baixa variação local de luz; pode ser uma textura uniforme intencional.\n");
        }
        out.append("Isto NÃO avalia beleza, movimento, enquadramento, renderização, FPS ou efeitos no jogo.");
        return out.toString();
    }
}
