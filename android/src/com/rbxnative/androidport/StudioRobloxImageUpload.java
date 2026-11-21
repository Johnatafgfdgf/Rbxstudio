package com.rbxnative.androidport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Explicit one-shot upload of a locally reverified PNG/JPEG to Roblox Open
 * Cloud Assets v1. This class has no access to project, MCP or AI credentials.
 * It never follows redirects, writes local files or publishes experiences.
 *
 * A successful HTTP 200/201 yields an Operation JSON, NOT an asset ID. Only a
 * separate Get Operation request can later establish the asset result.
 */
public final class StudioRobloxImageUpload {
    private StudioRobloxImageUpload() {}

    private static final String CREATE_URL = "https://apis.roblox.com/assets/v1/assets";
    private static final String OPERATION_URL = "https://apis.roblox.com/assets/v1/";
    public static final int MAX_RESPONSE_BYTES = 64 * 1024;

    public static String requireCreator(String id) throws IOException {
        if (id == null || !id.matches("[1-9][0-9]{0,18}"))
            throw new IOException("Informe o ID numérico do criador no Roblox.");
        try {
            long parsed = Long.parseLong(id);
            if (parsed <= 0) throw new NumberFormatException();
        } catch (NumberFormatException invalid) {
            throw new IOException("ID de criador fora do limite numérico.");
        }
        return id;
    }

    public static String requireName(String name) throws IOException {
        String n = name == null ? "" : name.trim();
        if (!n.matches("[A-Za-z][A-Za-z0-9_-]{2,49}"))
            throw new IOException("Nome do decal: 3–50 caracteres, letras, números, _ ou -.");
        return n;
    }

    public static String requireKey(String key) throws IOException {
        if (key == null || key.length() < 12 || key.length() > 1200 ||
                !key.matches("[\\x21-\\x7e]+"))
            throw new IOException("Chave Open Cloud ausente ou inválida.");
        return key;
    }

    public static String requestJson(String creatorId, String displayName)
            throws IOException {
        String creator = requireCreator(creatorId);
        String name = requireName(displayName);
        // Strict validation of both dynamic fields prevents JSON injection.
        return "{\"assetType\":\"Decal\",\"displayName\":\"" + name +
                "\",\"description\":\"Fonte: Poly Haven CC0; envio autorizado pelo RBX Studio Android.\"," +
                "\"creationContext\":{\"creator\":{\"userId\":\"" + creator + "\"}}}";
    }

    public static String operationUrl(String path) throws IOException {
        if (path == null || !path.matches("operations/[A-Za-z0-9_-]{8,180}"))
            throw new IOException("Identificador de operação inválido.");
        return OPERATION_URL + path;
    }

    /** Image bytes must have passed the manifest verifier beforehand. */
    public static String uploadDecal(String key, String creatorId, String displayName,
            String format, byte[] bytes) throws IOException {
        String secret = requireKey(key);
        String metadata = requestJson(creatorId, displayName);
        String mime;
        if ("PNG".equals(format)) mime = "image/png";
        else if ("JPG".equals(format)) mime = "image/jpeg";
        else throw new IOException("Somente PNG/JPG podem ser enviados.");
        if (bytes == null || bytes.length < 16 ||
                bytes.length > StudioPolyHavenIntegrity.MAX_FILE_BYTES ||
                !StudioPolyHavenIntegrity.validSignature(format, bytes, bytes.length))
            throw new IOException("Bytes da imagem não são válidos.");
        String boundary = "rbx-image-" + UUID.randomUUID().toString();
        String prefix = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"request\"\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n\r\n" +
                metadata + "\r\n" +
                "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"fileContent\"; " +
                "filename=\"image." + ("PNG".equals(format) ? "png" : "jpeg") + "\"\r\n" +
                "Content-Type: " + mime + "\r\n\r\n";
        byte[] before = prefix.getBytes(StandardCharsets.UTF_8);
        byte[] after = ("\r\n--" + boundary + "--\r\n")
                .getBytes(StandardCharsets.UTF_8);
        HttpURLConnection conn = (HttpURLConnection) new URL(CREATE_URL)
                .openConnection();
        try {
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("x-api-key", secret);
            conn.setRequestProperty("Content-Type",
                    "multipart/form-data; boundary=" + boundary);
            conn.setFixedLengthStreamingMode(before.length + bytes.length + after.length);
            try (OutputStream output = conn.getOutputStream()) {
                output.write(before);
                output.write(bytes);
                output.write(after);
                output.flush();
            }
            return response(conn, true);
        } finally {
            conn.disconnect();
        }
    }

    /** Independent status check; no automatic retry, no asset ID guessing. */
    public static String getOperation(String key, String path) throws IOException {
        String secret = requireKey(key);
        String url = operationUrl(path);
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("x-api-key", secret);
            conn.setRequestProperty("Accept", "application/json");
            return response(conn, false);
        } finally {
            conn.disconnect();
        }
    }

    private static String response(HttpURLConnection connection, boolean creation)
            throws IOException {
        int status = connection.getResponseCode();
        if ((creation && status != 200 && status != 201 && status != 202) ||
                (!creation && status != 200))
            throw new IOException("Roblox Open Cloud respondeu HTTP " + status +
                    "; nenhuma publicação foi confirmada.");
        String mime = connection.getContentType();
        if (mime == null || !mime.toLowerCase(java.util.Locale.ROOT)
                .startsWith("application/json"))
            throw new IOException("Open Cloud não retornou JSON.");
        try (InputStream input = connection.getInputStream()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = input.read(chunk)) != -1) {
                if (n == 0) continue;
                if (output.size() + n > MAX_RESPONSE_BYTES)
                    throw new IOException("Resposta Open Cloud acima do limite.");
                output.write(chunk, 0, n);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
