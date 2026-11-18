package com.rbxnative.androidport;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Read-only comparison of ONE user-selected local file against metadata
 * returned by the official Poly Haven /files/{slug} endpoint. No network,
 * project mutation, asset insertion, publication or persistent file storage.
 *
 * MD5 comes from Poly Haven's public manifest. It detects ordinary mismatches
 * but is not a collision-resistant proof of authorship or file safety.
 */
public final class StudioPolyHavenIntegrity {
    public static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    private StudioPolyHavenIntegrity() {}

    public static final class Candidate {
        public final String slug, format, variant;
        public final int expectedSize;
        public final String expectedMd5;

        public Candidate(String slug, String format, String variant,
                long expectedSize, String expectedMd5, String sourceUrl)
                throws IOException {
            if (!StudioPolyHavenCatalog.validSlug(slug))
                throw new IOException("Origem do asset inválida.");
            if (!("PNG".equals(format) || "JPG".equals(format)))
                throw new IOException("A comparação local só aceita PNG/JPG.");
            if (variant == null || !variant.matches("[A-Za-z0-9_./-]{1,140}") ||
                    variant.contains("..") || variant.startsWith("/") ||
                    variant.contains("//"))
                throw new IOException("Caminho de formato inválido.");
            if (expectedSize < 16 || expectedSize > MAX_FILE_BYTES)
                throw new IOException("Arquivo acima de 8 MiB ou tamanho inválido.");
            if (expectedMd5 == null || !expectedMd5.matches("(?i)[0-9a-f]{32}"))
                throw new IOException("Checksum oficial ausente/inválido.");
            if (!officialFileUrl(sourceUrl))
                throw new IOException("URL de origem não permitida.");
            this.slug = slug;
            this.format = format;
            this.variant = variant;
            this.expectedSize = (int) expectedSize;
            this.expectedMd5 = expectedMd5.toLowerCase(Locale.ROOT);
        }

        public String label() {
            return format + " • " + variant.replace('/', ' ') +
                    " • " + expectedSize / 1024 + " KiB";
        }
    }

    /** Never follow manifest-supplied URLs; this only validates provenance. */
    public static boolean officialFileUrl(String value) {
        if (value == null || value.length() > 1024) return false;
        try {
            URI u = new URI(value);
            return "https".equalsIgnoreCase(u.getScheme()) &&
                    "dl.polyhaven.org".equalsIgnoreCase(u.getHost()) &&
                    u.getUserInfo() == null && u.getPort() == -1 &&
                    u.getRawQuery() == null && u.getRawFragment() == null &&
                    u.getRawPath() != null &&
                    u.getRawPath().startsWith("/file/ph-assets/");
        } catch (Exception invalid) {
            return false;
        }
    }

    public static final class Verification {
        public final boolean matches;
        public final int bytes;
        public final String sha256;

        private Verification(boolean matches, int bytes, String sha256) {
            this.matches = matches;
            this.bytes = bytes;
            this.sha256 = sha256;
        }

        public String report(Candidate file) {
            return (matches ? "Tamanho e MD5 conferem com a ficha consultada." :
                    "Arquivo NÃO corresponde à ficha consultada.") +
                    "\nAsset: " + file.slug + " • " + file.variant +
                    "\nTamanho local: " + bytes + " bytes; esperado: " +
                    file.expectedSize + " bytes" +
                    "\nSHA-256 calculado localmente: " + sha256 +
                    "\nMD5 é uma checagem de consistência, NÃO comprova " +
                    "autoria, ausência de código malicioso ou compatibilidade no Roblox." +
                    "\nNenhum arquivo foi importado, publicado ou enviado à IA.";
        }
    }

    public static Verification verify(Candidate file, InputStream source)
            throws IOException {
        if (file == null || source == null)
            throw new IOException("Arquivo ou candidato ausente.");
        final MessageDigest md5, sha;
        try {
            md5 = MessageDigest.getInstance("MD5");
            sha = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IOException("Algoritmo de checksum indisponível.", unavailable);
        }
        byte[] chunk = new byte[8192];
        byte[] beginning = new byte[12];
        int beginLength = 0;
        int count = 0, zeroReads = 0, read;
        while ((read = source.read(chunk)) != -1) {
            if (read == 0) {
                if (++zeroReads > 8) throw new IOException("Leitura interrompida.");
                continue;
            }
            zeroReads = 0;
            if (read > file.expectedSize - count || read > MAX_FILE_BYTES - count)
                throw new IOException("Arquivo excedeu o tamanho declarado/8 MiB.");
            for (int i = 0; i < read && beginLength < beginning.length; i++)
                beginning[beginLength++] = chunk[i];
            md5.update(chunk, 0, read);
            sha.update(chunk, 0, read);
            count += read;
        }
        if (!validSignature(file.format, beginning, beginLength))
            throw new IOException("Os bytes não correspondem ao formato esperado.");
        boolean matches = count == file.expectedSize &&
                file.expectedMd5.equals(hex(md5.digest()));
        return new Verification(matches, count, hex(sha.digest()));
    }

    public static boolean validSignature(String type, byte[] bytes, int length) {
        if (bytes == null || length > bytes.length || length < 8) return false;
        if ("PNG".equals(type)) {
            int[] p = {137, 80, 78, 71, 13, 10, 26, 10};
            for (int i = 0; i < p.length; i++)
                if ((bytes[i] & 255) != p[i]) return false;
            return true;
        }
        return "JPG".equals(type) && (bytes[0] & 255) == 255 &&
                (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
    }

    private static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] output = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            output[2 * i] = digits[(bytes[i] >>> 4) & 15];
            output[2 * i + 1] = digits[bytes[i] & 15];
        }
        return new String(output);
    }
}
