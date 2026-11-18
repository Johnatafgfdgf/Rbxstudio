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
    private static final String API = "https://api.polyhaven.com/search";
    private static final String ASSET_PAGE = "https://polyhaven.com/a/";
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

    public static String search(String query, String type) throws IOException {
        String fixedUrl = buildSearchUrl(query, type);
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
                throw new IOException("Poly Haven limitou a frequência de pesquisas. Tente mais tarde.");
            if (code != 200)
                throw new IOException("Busca indisponível: Poly Haven respondeu HTTP " + code + ".");
            String contentType = connection.getContentType();
            if (contentType == null || !contentType.toLowerCase(Locale.ROOT)
                    .startsWith("application/json"))
                throw new IOException("Resposta da pesquisa não é JSON.");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            try (InputStream in = connection.getInputStream()) {
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (n == 0) continue;
                    if (out.size() + n > MAX_RESPONSE_BYTES)
                        throw new IOException("Resultado da pesquisa excedeu o limite de segurança.");
                    out.write(buf, 0, n);
                }
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            connection.disconnect();
        }
    }
}
