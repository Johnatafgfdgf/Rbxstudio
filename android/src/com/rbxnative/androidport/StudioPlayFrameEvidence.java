package com.rbxnative.androidport;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.PixelCopy;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.json.JSONObject;

/**
 * Explicit, one-use, short-lived screenshot of the ACTUAL Rook NativeActivity
 * window, obtained by Android PixelCopy after the player reports 'ready'.
 * No MediaProjection, background scraping, network request or AI upload.
 * All paths are fixed app-private names; no externally supplied path or URL.
 */
public final class StudioPlayFrameEvidence {
    private static final String REQUEST = "rook-frame-request.json";
    private static final String ACTIVE = "rook-frame-active.json";
    private static final String AUTHORIZATION = "rook-frame-authorization.json";
    private static final String REPORT = "rook-frame-report.json";
    private static final String IMAGE = "rook-frame-evidence.jpg";
    private static final int MAX_JSON_BYTES = 2048;
    private StudioPlayFrameEvidence() {}

    static File file(Context c, String name) {
        return new File(c.getFilesDir(), name);
    }

    private static JSONObject read(File file) throws Exception {
        if (!file.isFile() || file.length() < 2 ||
                file.length() > MAX_JSON_BYTES) return null;
        return new JSONObject(new String(Files.readAllBytes(file.toPath()),
                StandardCharsets.UTF_8));
    }

    private static void write(File file, JSONObject obj) throws Exception {
        byte[] bytes = obj.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_JSON_BYTES)
            throw new IOException("Metadata is too large");
        File pending = new File(file.getParentFile(), file.getName() + ".tmp");
        Files.write(pending.toPath(), bytes);
        Files.move(pending.toPath(), file.toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static void remove(File file) {
        try { Files.deleteIfExists(file.toPath()); }
        catch (IOException ignored) { /* Remains app-private. */ }
        try { Files.deleteIfExists(new File(
                file.getParentFile(), file.getName() + ".tmp").toPath()); }
        catch (IOException ignored) {}
    }

    private static void clearFiles(Context c) {
        remove(file(c, REQUEST));
        remove(file(c, ACTIVE));
        remove(file(c, AUTHORIZATION));
        remove(file(c, REPORT));
        remove(file(c, IMAGE));
    }

    /** Called from a user-confirmed UI action in the AI Hub only. */
    public static synchronized String arm(Context c) throws Exception {
        clearFiles(c);
        long now = System.currentTimeMillis();
        String id = UUID.randomUUID().toString();
        JSONObject grant = new JSONObject().put("id", id)
                .put("armedAt", now)
                .put("expiresAt", now + StudioPlayFrameRules.WINDOW_MS);
        // Retain an independent consent record even after begin() consumes
        // the one-shot REQUEST. This binds the later PixelCopy receipt to
        // what the user actually authorized, including revocations.
        write(file(c, AUTHORIZATION), grant);
        write(file(c, REQUEST), grant);
        return "Captura de UMA imagem do próximo Play Rook local autorizada " +
                "por até 10 minutos. Abra o Play local pelo editor e volte " +
                "à central de IA para importar. A imagem real será obtida " +
                "após o player informar que está pronto, não antes. " +
                "O arquivo privado é apagado após a importação ou revogação; " +
                "nenhuma imagem é enviada à API de IA automaticamente.";
    }

    /** Consume authorization when Rook launches; do not reuse on a later Play. */
    public static synchronized String begin(Context c) {
        File request = file(c, REQUEST);
        try {
            JSONObject data = read(request);
            if (data == null) return null;
            String id = data.optString("id", "");
            long armedAt = data.optLong("armedAt", 0);
            long expiresAt = data.optLong("expiresAt", 0);
            JSONObject original = read(file(c, AUTHORIZATION));
            if (!id.matches("[a-f0-9-]{36}") ||
                    original == null ||
                    !id.equals(original.optString("id", "")) ||
                    armedAt != original.optLong("armedAt", -1) ||
                    expiresAt != original.optLong("expiresAt", -1) ||
                    !StudioPlayFrameRules.validTime(
                            armedAt, expiresAt, System.currentTimeMillis()))
                return null;
            write(file(c, ACTIVE), data);
            return id;
        } catch (Exception ignored) {
            return null;
        } finally {
            remove(request);
        }
    }

    /** Check the active one-use request again so revocation stops late writes. */
    private static JSONObject active(Context c, String id) throws Exception {
        if (id == null || !id.matches("[a-f0-9-]{36}")) return null;
        JSONObject data = read(file(c, ACTIVE));
        JSONObject authorization = read(file(c, AUTHORIZATION));
        if (data == null || authorization == null ||
                !id.equals(data.optString("id", "")) ||
                !id.equals(authorization.optString("id", "")) ||
                data.optLong("armedAt", -1) !=
                        authorization.optLong("armedAt", -2) ||
                data.optLong("expiresAt", -1) !=
                        authorization.optLong("expiresAt", -2) ||
                !StudioPlayFrameRules.validTime(
                        data.optLong("armedAt", 0), data.optLong("expiresAt", 0),
                        System.currentTimeMillis())) return null;
        return data;
    }

    /** Called only when PixelCopy returns SUCCESS for the Rook window. */
    public static synchronized void save(Context c, String id, Bitmap frame,
            int pixelCopyResult) throws Exception {
        JSONObject current = active(c, id);
        if (current == null || pixelCopyResult != PixelCopy.SUCCESS ||
                frame == null || frame.isRecycled()) return;
        byte[] bytes = null;
        // Dense scenes can exceed the app-private frame budget at 75%.
        // Reduce quality before refusing instead of silently dropping a
        // valid, user-authorized real capture.
        for (int quality : new int[]{75, 60, 45, 30}) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!frame.compress(Bitmap.CompressFormat.JPEG, quality, out))
                continue;
            byte[] attempt = out.toByteArray();
            if (StudioPlayFrameRules.validImage(attempt,
                    frame.getWidth(), frame.getHeight())) {
                bytes = attempt;
                break;
            }
        }
        if (bytes == null)
            throw new IOException("Frame exceeds the bounded JPEG budget");
        // Only the app's own :rook process receives this one-use active ID.
        if (active(c, id) == null) return;
        File image = file(c, IMAGE);
        File tmp = file(c, IMAGE + ".tmp");
        Files.write(tmp.toPath(), bytes);
        Files.move(tmp.toPath(), image.toPath(),
                StandardCopyOption.REPLACE_EXISTING);
        // This can race an explicit revocation from the separate UI process.
        // Recheck before writing the success receipt, and delete any stale
        // image if the authorization has been removed during compression.
        if (active(c, id) == null) {
            remove(image);
            return;
        }
        long captured = System.currentTimeMillis();
        write(file(c, REPORT), new JSONObject()
                .put("status", "captured")
                .put("id", id)
                .put("source", "rook_native_window_pixelcopy")
                .put("playerReady", true)
                .put("pixelCopyResult", pixelCopyResult)
                .put("armedAt", current.getLong("armedAt"))
                .put("expiresAt", current.getLong("expiresAt"))
                .put("capturedAt", captured)
                .put("sha256", StudioPlayFrameRules.digest(bytes))
                .put("bytes", bytes.length)
                .put("width", frame.getWidth())
                .put("height", frame.getHeight()));
        remove(file(c, ACTIVE));
    }

    /** A failure is explicit; it must never be mistaken for game pixels. */
    public static synchronized void failed(Context c, String id, String reason) {
        try {
            JSONObject current = active(c, id);
            if (current == null) return;
            write(file(c, REPORT), new JSONObject()
                    .put("status", "failed").put("id", id)
                    .put("reason", reason == null ? "unknown" :
                            reason.substring(0, Math.min(60, reason.length())))
                    .put("armedAt", current.getLong("armedAt"))
                    .put("expiresAt", current.getLong("expiresAt")));
            remove(file(c, ACTIVE));
            remove(file(c, IMAGE));
        } catch (Exception ignored) {
            remove(file(c, ACTIVE));
        }
    }

    public static final class Evidence {
        public final byte[] jpeg;
        public final int width;
        public final int height;
        public final long capturedAtMillis;
        public final String sha256;
        public final String summary;
        Evidence(byte[] jpeg, int width, int height, long capturedAtMillis,
                String sha256, String summary) {
            this.jpeg = jpeg;
            this.width = width;
            this.height = height;
            this.capturedAtMillis = capturedAtMillis;
            this.sha256 = sha256;
            this.summary = summary;
        }
    }

    /**
     * Single-use import. A receipt requires PixelCopy.SUCCESS, a ready player,
     * actual private JPEG bytes with matching SHA-256, and unexpired consent.
     */
    public static synchronized Evidence takeOnce(Context c) throws Exception {
        File reportFile = file(c, REPORT);
        if (!reportFile.isFile()) return null;
        try {
            JSONObject report = read(reportFile);
            if (report == null) throw new IOException("Registro inválido.");
            if ("failed".equals(report.optString("status", "")))
                throw new IOException("O player não conseguiu capturar o Play: " +
                        report.optString("reason", "sem imagem"));
            if (!"captured".equals(report.optString("status", "")))
                throw new IOException("Nenhuma imagem confirmada.");
            File image = file(c, IMAGE);
            if (!image.isFile() || image.length() < 64 ||
                    image.length() > StudioPlayFrameRules.MAX_JPEG_BYTES)
                throw new IOException("Captura ausente ou tamanho inválido.");
            byte[] bytes = Files.readAllBytes(image.toPath());
            JSONObject authorization = read(file(c, AUTHORIZATION));
            if (authorization == null ||
                    !authorization.optString("id", "").equals(
                            report.optString("id", "")) ||
                    authorization.optLong("armedAt", -1) !=
                            report.optLong("armedAt", -2) ||
                    authorization.optLong("expiresAt", -1) !=
                            report.optLong("expiresAt", -2))
                throw new IOException("Captura sem autorização original correspondente.");
            long now = System.currentTimeMillis();
            if (!StudioPlayFrameRules.validReceipt(
                    authorization.optString("id", ""),
                    report.optString("id", ""),
                    report.optString("source", ""),
                    report.optBoolean("playerReady", false),
                    report.optInt("pixelCopyResult", -1),
                    report.optLong("armedAt", 0), report.optLong("expiresAt", 0),
                    report.optLong("capturedAt", 0),
                    report.optString("sha256", ""), bytes,
                    report.optInt("width", 0), report.optInt("height", 0), now))
                throw new IOException("Captura não verificável ou expirada.");
            if (report.optInt("bytes", -1) != bytes.length)
                throw new IOException("Tamanho não corresponde ao registro.");
            return new Evidence(bytes, report.getInt("width"), report.getInt("height"),
                    report.getLong("capturedAt"), report.getString("sha256"),
                    "Captura REAL da janela do player Rook local via PixelCopy " +
                    "após o sinal ready; " + report.getInt("width") + " × " +
                    report.getInt("height") + " pixels. Integridade SHA-256 " +
                    "confirmada. O arquivo privado foi consumido. " +
                    "O frame não comprova FPS, funcionamento de scripts, " +
                    "qualidade visual, áudio ou que o Play externo foi capturado.");
        } finally {
            clearFiles(c);
        }
    }

    public static synchronized void clear(Context c) { clearFiles(c); }
}
