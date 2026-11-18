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
        // Audio metadata is extracted locally from a bounded header, never decoded or uploaded.
        public final String audioDetails;

        private Inspected(String format, boolean image, int byteCount,
                String sha256, byte[] imageBytes, String audioDetails) {
            this.format = format;
            this.image = image;
            this.audio = !image;
            this.byteCount = byteCount;
            this.sha256 = sha256;
            this.imageBytes = imageBytes;
            this.audioDetails = audioDetails;
        }

        public String report() {
            return "Arquivo inspecionado localmente\n" +
                    "Tipo: " + (image ? "Imagem / textura VFX" : "Áudio / efeito sonoro") +
                    " (" + format + ")\n" +
                    "Tamanho: " + byteCount + " bytes\n" +
                    "SHA-256: " + sha256 + "\n" +
                    (format.equals("GIF")
                        ? "GIF: a prévia mostra só um quadro; não valida a animação.\n" : "") +
                    (audioDetails.isEmpty() ? "" : audioDetails + "\n") +
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
        // Audio is hashed while streaming: never buffer a multi-megabyte sound.
        // Only image bytes are kept (up to 8 MiB) for a small local preview.
        ByteArrayOutputStream imageMemory = new ByteArrayOutputStream();
        byte[] firstBytes = new byte[16];
        // Keep at most 64 KiB for WAV chunk-header inspection, even for large audio.
        byte[] audioHeader = new byte[64 * 1024];
        int audioHeaderLength = 0;
        int firstLength = 0;
        byte[] chunk = new byte[8192];
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IOException("SHA-256 indisponível", failure);
        }
        int size = 0;
        String type = null;
        int n;
        while ((n = source.read(chunk)) != -1) {
            if (n == 0) continue;
            if (n > MAX_AUDIO_BYTES - size)
                throw new IOException("Arquivo excede 16 MiB: leitura recusada.");
            digest.update(chunk, 0, n);
            if (audioHeaderLength < audioHeader.length) {
                int take = Math.min(n, audioHeader.length - audioHeaderLength);
                System.arraycopy(chunk, 0, audioHeader, audioHeaderLength, take);
                audioHeaderLength += take;
            }
            for (int j = 0; j < n && firstLength < firstBytes.length; j++)
                firstBytes[firstLength++] = chunk[j];
            if (type == null && firstLength >= 12) {
                type = format(firstBytes);
                if (type.isEmpty())
                    throw new IOException("Formato não permitido. SVG, HTML, ZIP, vídeo ou executável são recusados.");
            }
            if (type != null && image(type) && n > MAX_IMAGE_BYTES - size)
                throw new IOException("Imagem excede 8 MiB: leitura recusada.");
            // If a provider splits its first bytes across tiny reads, only
            // buffer this short prefix until we know the content type.
            if (type == null || image(type)) imageMemory.write(chunk, 0, n);
            size += n;
        }
        if (type == null) type = format(java.util.Arrays.copyOf(firstBytes, firstLength));
        if (type.isEmpty())
            throw new IOException("Arquivo vazio, incompleto ou de formato não permitido.");
        boolean isImage = image(type);
        StringBuilder hash = new StringBuilder(64);
        for (byte item : digest.digest())
            hash.append(String.format(Locale.ROOT, "%02x", item & 255));
        String audioDetails = type.equals("WAV")
                ? describeWav(audioHeader, audioHeaderLength, size) : "";
        return new Inspected(type, isImage, size, hash.toString(),
                isImage ? imageMemory.toByteArray() : null, audioDetails);
    }


    private static int le16(byte[] bytes, int at) {
        return (bytes[at] & 255) | ((bytes[at + 1] & 255) << 8);
    }

    private static long le32(byte[] bytes, int at) {
        return (bytes[at] & 255L) | ((bytes[at + 1] & 255L) << 8) |
                ((bytes[at + 2] & 255L) << 16) | ((bytes[at + 3] & 255L) << 24);
    }

    /**
     * Read-only WAV PCM/IEEE-float header inspection. Duration is derived
     * from the declared data chunk, with RIFF bounds validated against the
     * actual streamed length; compressed/unknown codecs are not guessed.
     * Never reads or buffers more than the first 64 KiB of sound.
     */
    private static String describeWav(byte[] header, int length, int fileLength) {
        final String unavailable = "WAV: duração não verificável (cabeçalho incompleto ou inconsistente).";
        if (length < 12 || fileLength < 44 ||
                !ascii(header, 0, "RIFF") || !ascii(header, 8, "WAVE"))
            return unavailable;
        long riffEnd = le32(header, 4) + 8L;
        if (riffEnd < 44 || riffEnd > fileLength) return unavailable;
        int codec = -1, channels = 0, sampleRate = 0, bits = 0;
        long byteRate = 0, blockAlign = 0, dataLength = -1;
        long pos = 12;
        while (pos + 8 <= length && pos + 8 <= riffEnd) {
            int at = (int) pos;
            long chunkSize = le32(header, at + 4);
            long start = pos + 8, end = start + chunkSize;
            if (end > riffEnd || end > fileLength) return unavailable;
            if (ascii(header, at, "fmt ") && chunkSize >= 16 &&
                    start + 16 <= length) {
                int p = (int) start;
                codec = le16(header, p);
                channels = le16(header, p + 2);
                long rate = le32(header, p + 4);
                sampleRate = rate > Integer.MAX_VALUE ? 0 : (int) rate;
                byteRate = le32(header, p + 8);
                blockAlign = le16(header, p + 12);
                bits = le16(header, p + 14);
            } else if (ascii(header, at, "data")) {
                dataLength = chunkSize;
            }
            long next = end + (chunkSize & 1L);
            if (next <= pos || next > length || next > riffEnd) break;
            pos = next;
        }
        if (codec != 1 && codec != 3)
            return "WAV: codec não PCM/float ou não identificado; duração não calculada.";
        if (channels < 1 || channels > 16 || sampleRate < 1000 ||
                sampleRate > 384000 || bits < 8 || bits > 64 ||
                (bits % 8) != 0 || dataLength < 0 || byteRate == 0 ||
                blockAlign != channels * (bits / 8L) ||
                byteRate != sampleRate * blockAlign ||
                (codec == 3 && bits != 32 && bits != 64))
            return unavailable;
        return String.format(Locale.ROOT,
                "WAV: %d canal(is), %d Hz, %d bits por amostra. " +
                "Duração estimada: %.2f s (pelo bloco de dados; som não reproduzido).",
                channels, sampleRate, bits, dataLength / (double) byteRate);
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
