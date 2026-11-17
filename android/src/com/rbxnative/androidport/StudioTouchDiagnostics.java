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
            write(reportFile(context), new JSONObject().put("id", id)
                    .put("ready", ready).put("down", Math.max(0, Math.min(down, 100000)))
                    .put("up", Math.max(0, Math.min(up, 100000)))
                    .put("cancel", Math.max(0, Math.min(cancel, 100000)))
                    .put("stopped", stopped)
                    .put("recordedAt", System.currentTimeMillis())
                    .put("source", "NativeActivity.dispatchTouchEvent"));
        } catch (Exception ignored) {
            // Never interfere with gameplay because a diagnostic file failed.
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
            int downs = report.optInt("down", -1);
            int ups = report.optInt("up", -1);
            int canceled = report.optInt("cancel", -1);
            if (downs < 0 || ups < 0 || canceled < 0 || ups > downs ||
                    canceled > downs || ups + canceled > downs)
                return "Relatório de toque inconsistente: não confirmado.";
            boolean ready = report.optBoolean("ready", false);
            StringBuilder out = new StringBuilder("PLAY ROOK • ENTRADA ANDROID\n")
                    .append("Player informou pronto: ").append(ready ? "sim" : "não").append("\n")
                    .append("MotionEvent ACTION_DOWN: ").append(downs).append("\n")
                    .append("MotionEvent ACTION_UP: ").append(ups).append("\n")
                    .append("MotionEvent ACTION_CANCEL: ").append(canceled).append("\n")
                    .append("Player encerrou: ")
                    .append(report.optBoolean("stopped", false) ? "registrado" : "não confirmado")
                    .append("\n");
            if (ready && downs > 0 && ups > 0) {
                out.append("EVIDÊNCIA: a rota Java da Activity recebeu eventos reais " +
                        "de tela touchscreen durante o Play.\n");
            } else {
                out.append("INCONCLUSIVO: não foi confirmado um ciclo de toque " +
                        "na rota Java do player. O input nativo pode não passar " +
                        "por dispatchTouchEvent.\n");
            }
            out.append("Não comprova entrada no motor nativo, GuiButton.Activated, " +
                    "MouseButton1Click, Tool.Activated, resposta de scripts ou " +
                    "efeito visual. Sem coordenadas, gravação de tela ou envio à IA.");
            return out.toString();
        } catch (Exception ignored) {
            return "Não foi possível ler o relatório local de toques. " +
                    "Nenhum resultado foi presumido.";
        }
    }

    /** Clear authorization and local evidence without changing the saved game. */
    public static synchronized void clear(Context context) {
        try { Files.deleteIfExists(requestFile(context).toPath()); }
        catch (Exception ignored) {}
        try { Files.deleteIfExists(reportFile(context).toPath()); }
        catch (Exception ignored) {}
    }
}
