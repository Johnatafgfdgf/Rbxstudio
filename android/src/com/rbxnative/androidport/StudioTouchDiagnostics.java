package com.rbxnative.androidport;

import android.content.Context;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * One-shot, user-authorized local touch diagnostics for the separate Android
 * Rook player. Captures only event totals, never positions, input text, raw
 * events, scripts, URLs, user IDs, images or API keys. No remote transmission.
 *
 * Important: an event in NativeActivity.dispatchTouchEvent proves ONLY that
 * this Java path received a touchscreen MotionEvent. It does not prove the
 * native game received it, or that GuiButton.Activated fired.
 */
public final class StudioTouchDiagnostics {
    private static final String REQUEST = "rbx-rook-touch-request.json";
    private static final String REPORT = "rbx-rook-touch-report.json";
    private static final long ARM_WINDOW_MS = 10 * 60 * 1000L;
    private static final int MAX_FILE_BYTES = 4096;
    private StudioTouchDiagnostics() {}

    /** The current Android MCP Play route opens the external Roblox publisher.
     * It is NOT attached to Rook's own Client/Server DOM. Never dispatch it
     * from the AI Hub until a real native local-MCP data bridge is verified. */
    public static boolean hasVerifiedLocalMcpPlayBridge() { return false; }

    static File requestFile(Context context) { return new File(context.getFilesDir(), REQUEST); }
    static File reportFile(Context context) { return new File(context.getFilesDir(), REPORT); }

    private static JSONObject read(File file) throws Exception {
        if (!file.isFile() || file.length() < 2 || file.length() > MAX_FILE_BYTES)
            return null;
        return new JSONObject(new String(Files.readAllBytes(file.toPath()),
                StandardCharsets.UTF_8));
    }

    private static void write(File file, JSONObject data) throws Exception {
        String contents = data.toString();
        if (contents.length() > MAX_FILE_BYTES)
            throw new IOException("Relatório de toque acima do limite");
        File pending = new File(file.getParentFile(), file.getName() + ".pending");
        Files.write(pending.toPath(), contents.getBytes(StandardCharsets.UTF_8));
        Files.move(pending.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /** Explicit one-use authorization, expires even if the user never opens Play. */
    public static synchronized String arm(Context context) throws Exception {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        File old = reportFile(context);
        Files.deleteIfExists(old.toPath());
        write(requestFile(context), new JSONObject().put("id", id)
                .put("armedAt", now).put("expiresAt", now + ARM_WINDOW_MS));
        return "Monitoramento autorizado somente para o próximo Play Rook, por até 10 minutos. " +
                "Abra o Play LOCAL do player Rook pelo editor, toque na tela " +
                "depois de carregar e volte aqui para conferir. " +
                "Não registra coordenadas e não comprova cliques nas GUIs.";
    }

    /** Called by the actual Rook NativeActivity when it starts. The request
     * is consumed even if invalid, so it cannot silently arm later sessions. */
    public static synchronized String begin(Context context) {
        File request = requestFile(context);
        try {
            JSONObject body = read(request);
            Files.deleteIfExists(request.toPath());
            if (body == null) return null;
            String id = body.optString("id", "");
            long now = System.currentTimeMillis();
            long armed = body.optLong("armedAt", -1);
            long expires = body.optLong("expiresAt", -1);
            if (!id.matches("[0-9a-f-]{36}") || armed <= 0 || now < armed ||
                    now - armed > ARM_WINDOW_MS || expires < now ||
                    expires - armed != ARM_WINDOW_MS)
                return null;
            writeReport(context, id, false, 0, 0, 0, false);
            return id;
        } catch (Exception invalid) {
            try { Files.deleteIfExists(request.toPath()); } catch (IOException ignored) {}
            return null;
        }
    }

    /** State counters are passed from the Activity after its ordinary input
     * dispatch. Never stores pixel coordinates, pointer IDs or raw gestures. */
    public static synchronized void writeReport(Context context, String id, boolean ready,
            int down, int up, int cancel, boolean stopped) {
        if (id == null || !id.matches("[0-9a-f-]{36}")) return;
        try {
            JSONObject report = new JSONObject().put("id", id)
                    .put("ready", ready).put("down", Math.max(0, Math.min(down, 100000)))
                    .put("up", Math.max(0, Math.min(up, 100000)))
                    .put("cancel", Math.max(0, Math.min(cancel, 100000)))
                    .put("stopped", stopped)
                    .put("recordedAt", System.currentTimeMillis())
                    .put("source", "NativeActivity.dispatchTouchEvent");
            // A Java MotionEvent can arrive after a native winit event.
            // Preserve independent native evidence across both write routes.
            JSONObject prior = read(reportFile(context));
            if (prior != null && id.equals(prior.optString("id", "")) &&
                    "Rook.winit.WindowEvent.Touch".equals(prior.optString("nativeSource", "")) &&
                    consistentTouchTotals(prior.optInt("nativeDown", -1),
                            prior.optInt("nativeUp", -1), prior.optInt("nativeCancel", -1))) {
                report.put("nativeSource", "Rook.winit.WindowEvent.Touch")
                        .put("nativeDown", prior.getInt("nativeDown"))
                        .put("nativeUp", prior.getInt("nativeUp"))
                        .put("nativeCancel", prior.getInt("nativeCancel"));
            }
            write(reportFile(context), report);
        } catch (Exception ignored) {
            // Never interfere with gameplay because a diagnostic file failed.
        }
    }

    /** Only accepts bounded aggregate counts from the Rook winit event loop.
     * Reuses the one-shot session token; cannot arm or attach to another Play. */
    public static synchronized void writeNativeCounts(Context context, String id,
            int down, int up, int cancel) {
        if (id == null || !id.matches("[0-9a-f-]{36}") ||
                !consistentTouchTotals(down, up, cancel)) return;
        try {
            JSONObject report = read(reportFile(context));
            if (report == null || !id.equals(report.optString("id", "")) ||
                    !report.optBoolean("ready", false) ||
                    report.optBoolean("stopped", false)) return;
            report.put("nativeSource", "Rook.winit.WindowEvent.Touch")
                    .put("nativeDown", down).put("nativeUp", up)
                    .put("nativeCancel", cancel)
                    .put("recordedAt", System.currentTimeMillis());
            write(reportFile(context), report);
        } catch (Exception ignored) {
            // Diagnostics must never interfere with the native event loop.
        }
    }

    public static synchronized String readSummary(Context context) {
        try {
            JSONObject report = read(reportFile(context));
            if (report == null) {
                JSONObject pending = read(requestFile(context));
                if (pending != null && pending.optLong("expiresAt", 0) >= System.currentTimeMillis())
                    return "Monitoramento aguardando o próximo Play Rook local. " +
                            "Nenhum toque capturado ainda.";
                return "Nenhum relatório de toque local disponível.";
            }
            if (!"NativeActivity.dispatchTouchEvent".equals(
                    report.optString("source", "")) ||
                    !report.optString("id", "").matches("[0-9a-f-]{36}"))
                return "Relatório de toque com origem desconhecida: ignorado.";
            boolean nativeReported = "Rook.winit.WindowEvent.Touch".equals(
                    report.optString("nativeSource", ""));
            if (report.has("nativeSource") && !nativeReported)
                return "Relatório com origem nativa desconhecida: ignorado.";
            return formatTouchEvidence(report.optBoolean("ready", false),
                    report.optInt("down", -1), report.optInt("up", -1),
                    report.optInt("cancel", -1),
                    report.optBoolean("stopped", false),
                    nativeReported, report.optInt("nativeDown", -1),
                    report.optInt("nativeUp", -1), report.optInt("nativeCancel", -1));
        } catch (Exception ignored) {
            return "Não foi possível ler o relatório local de toques. " +
                    "Nenhum resultado foi presumido.";
        }
    }


    /** Pure Java format contract; no access to Android windows or user data. */
    static boolean consistentTouchTotals(int down, int up, int cancel) {
        return down >= 0 && up >= 0 && cancel >= 0 &&
                down <= 100000 && up <= down && cancel <= down &&
                (long) up + cancel <= down;
    }

    static String formatTouchEvidence(boolean ready, int downs, int ups,
            int canceled, boolean stopped) {
        return formatTouchEvidence(ready, downs, ups, canceled, stopped,
                false, -1, -1, -1);
    }

    static String formatTouchEvidence(boolean ready, int downs, int ups,
            int canceled, boolean stopped, boolean nativeReported,
            int nativeDown, int nativeUp, int nativeCancel) {
        if (!consistentTouchTotals(downs, ups, canceled) ||
                (nativeReported && !consistentTouchTotals(
                        nativeDown, nativeUp, nativeCancel)))
            return "Relatório de toque inconsistente: não confirmado.";
        StringBuilder out = new StringBuilder("PLAY ROOK • ENTRADA ANDROID\n")
                .append("Player informou pronto: ").append(ready ? "sim" : "não").append("\n")
                .append("MotionEvent ACTION_DOWN: ").append(downs).append("\n")
                .append("MotionEvent ACTION_UP: ").append(ups).append("\n")
                .append("MotionEvent ACTION_CANCEL: ").append(canceled).append("\n")
                .append("Player encerrou: ")
                .append(stopped ? "registrado" : "não confirmado").append("\n");
        if (ready && downs > 0 && ups > 0) {
            out.append("EVIDÊNCIA: a rota Java da Activity recebeu eventos " +
                    "touchscreen durante o Play.\n");
        } else {
            out.append("INCONCLUSIVO: não foi confirmado um ciclo de toque " +
                    "na rota Java do player. O input nativo pode não passar " +
                    "por dispatchTouchEvent.\n");
        }
        if (nativeReported) {
            out.append("Rook/winit Touch Started: ").append(nativeDown).append("\n")
                    .append("Rook/winit Touch Ended: ").append(nativeUp).append("\n")
                    .append("Rook/winit Touch Cancelled: ").append(nativeCancel).append("\n");
            if (ready && nativeDown > 0 && nativeUp > 0)
                out.append("EVIDÊNCIA: eventos de toque chegaram ao loop winit do Rook.\n");
            else
                out.append("INCONCLUSIVO: o loop winit não confirmou um ciclo de toque.\n");
        } else {
            out.append("Rook/winit: sem evidência nativa nesta sessão. " +
                    "Não comprova entrada no motor nativo.\n");
        }
        out.append("Mesmo com winit, não comprova GuiButton.Activated, " +
                "MouseButton1Click, Tool.Activated, callback Luau ou " +
                "efeito visual. Sem coordenadas, gravação de tela ou envio à IA.");
        return out.toString();
    }

    /** Clear authorization and local evidence without changing the saved game. */
    public static synchronized void clear(Context context) {
        try { Files.deleteIfExists(requestFile(context).toPath()); }
        catch (Exception ignored) {}
        try { Files.deleteIfExists(reportFile(context).toPath()); }
        catch (Exception ignored) {}
    }
}
