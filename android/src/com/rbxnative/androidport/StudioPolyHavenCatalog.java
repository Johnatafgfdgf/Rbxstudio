package com.rbxnative.androidport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Explicit, read-only search of Poly Haven's public CC0 asset metadata.
 * No user credentials, MCP contents, model prompts or project data are sent.
 * Only the typed search text and selected asset category reach Poly Haven.
 * Asset bytes are NOT downloaded, inserted into the editor or published.
 */
public final class StudioPolyHavenCatalog {
    public static final int MAX_RESULTS = 8;
    public static final int MAX_RESPONSE_BYTES = 256 * 1024;
    public static final int MAX_DETAILS_BYTES = 512 * 1024;
    private static final String API = "https://api.polyhaven.com/search";
    private static final String ASSET_PAGE = "https://polyhaven.com/a/";
    private static final String INFO_ENDPOINT = "https://api.polyhaven.com/v2/info/";
    private static final String FILES_ENDPOINT = "https://api.polyhaven.com/v2/files/";
    // Poly Haven API ToS asks API clients to identify themselves and show credit.
    private static final String USER_AGENT = "RBXStudioAndroid-PolyHavenBrowser/0.6.11";
    private StudioPolyHavenCatalog() {}

    public static String buildSearchUrl(String query, String type) throws IOException {
        if (query == null) throw new IOException("Digite uma descrição para pesquisar.");
        String trimmed = query.trim().toLowerCase(Locale.ROOT);
        if (trimmed.length() < 2 || trimmed.length() > 100)
            throw new IOException("A pesquisa precisa conter de 2 a 100 caracteres.");
        for (int i = 0; i < trimmed.length(); i++)
            if (Character.isISOControl(trimmed.charAt(i)))
                throw new IOException("A pesquisa contém caracteres de controle.");
        if (!("textures".equals(type) || "models".equals(type) || "hdris".equals(type)))
            throw new IOException("Categoria de assets não permitida.");
        return API + "?q=" + URLEncoder.encode(trimmed, StandardCharsets.UTF_8.name()) +
                "&t=" + type + "&limit=" + MAX_RESULTS;
    }

    public static boolean validSlug(String slug) {
        return slug != null && slug.matches("[a-z0-9][a-z0-9_-]{0,79}");
    }

    public static String assetPage(String slug) throws IOException {
        if (!validSlug(slug)) throw new IOException("Identificador de asset inválido.");
        return ASSET_PAGE + slug;
    }

    public static String buildInfoUrl(String slug) throws IOException {
        if (!validSlug(slug)) throw new IOException("Identificador inválido.");
        return INFO_ENDPOINT + slug;
    }

    public static String buildFilesUrl(String slug) throws IOException {
        if (!validSlug(slug)) throw new IOException("Identificador inválido.");
        return FILES_ENDPOINT + slug;
    }

    /** Only file-format keys, never URLs supplied by an untrusted API response. */
    public static String knownFormat(String key) {
        if (key == null) return "";
        String k = key.toLowerCase(Locale.ROOT);
        switch (k) {
            case "jpg": case "jpeg": case "png": case "webp":
            case "exr": case "hdr": case "gltf": case "glb":
            case "fbx": case "obj": case "blend": case "usd":
            case "usdz": case "tif": case "tiff": case "mtl":
                return k.equals("jpeg") ? "JPG" : k.toUpperCase(Locale.ROOT);
            default: return "";
        }
    }

    public static String search(String query, String type) throws IOException {
        return fetchJson(buildSearchUrl(query, type), MAX_RESPONSE_BYTES);
    }

    public static String fetchInfo(String slug) throws IOException {
        return fetchJson(buildInfoUrl(slug), MAX_DETAILS_BYTES);
    }

    public static String fetchFiles(String slug) throws IOException {
        return fetchJson(buildFilesUrl(slug), MAX_DETAILS_BYTES);
    }

    /**
     * All URLs are built from fixed HTTPS hosts plus a validated slug, never
     * read from network JSON. No asset-byte downloads, credentials or uploads.
     */
    private static String fetchJson(String fixedUrl, int maxBytes) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(fixedUrl).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(9000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            int code = connection.getResponseCode();
            if (code == 429)
                throw new IOException("Poly Haven limitou a frequência de consultas.");
            if (code == 403)
                throw new IOException("Arquivos não disponíveis publicamente (possível acesso antecipado).");
            if (code == 404)
                throw new IOException("Asset não encontrado no catálogo.");
            if (code != 200)
                throw new IOException("Poly Haven respondeu HTTP " + code + ".");
            String contentType = connection.getContentType();
            if (contentType == null || !contentType.toLowerCase(Locale.ROOT)
                    .startsWith("application/json"))
                throw new IOException("Resposta do catálogo não é JSON.");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            try (InputStream in = connection.getInputStream()) {
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (n == 0) continue;
                    if (out.size() + n > maxBytes)
                        throw new IOException("Metadados ultrapassam o limite local permitido.");
                    out.write(buf, 0, n);
                }
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            connection.disconnect();
        }
    }
}
