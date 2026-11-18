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

    /** Explicit one-use authorization, expires even if the user never opens Play.
     * Inspecting button paths requires an additional, separately unchecked
     * UI permission; existing uses of arm(context) never enable that. */
    public static synchronized String arm(Context context) throws Exception {
        return arm(context, false);
    }

    public static synchronized String arm(Context context, boolean guiAuthorized)
            throws Exception {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        File old = reportFile(context);
        Files.deleteIfExists(old.toPath());
        write(requestFile(context), new JSONObject().put("id", id)
                .put("armedAt", now).put("expiresAt", now + ARM_WINDOW_MS)
                .put("guiConsent", guiAuthorized));
        return "Monitoramento autorizado somente para o próximo Play Rook, por até 10 minutos. " +
                "Abra o Play LOCAL do player Rook pelo editor, toque na tela " +
                "depois de carregar e volte aqui para conferir. " +
                (guiAuthorized ? "Com consentimento separado, registra até 200 " +
                        "sinais GuiButton.Activated e o caminho do último botão " +
                        "somente neste aparelho. Não comprova execução do callback Luau."
                    : "Não registra nomes de botões e não comprova cliques nas GUIs.") +
                " Nunca registra coordenadas.";
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
            if (body.optBoolean("guiConsent", false)) {
                JSONObject report = read(reportFile(context));
                if (report != null) {
                    report.put("guiConsent", true);
                    write(reportFile(context), report);
                }
            }
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
                    prior.optBoolean("guiConsent", false)) {
                report.put("guiConsent", true);
                if ("Rook.touch.GuiButton.Activated".equals(prior.optString("guiSource", ""))
                        && prior.optInt("guiActivated", -1) >= 0
                        && prior.optInt("guiActivated", -1) <= 200
                        && prior.optString("lastGuiButton", "").length() <= 240) {
                    report.put("guiSource", "Rook.touch.GuiButton.Activated")
                          .put("guiActivated", prior.getInt("guiActivated"))
                          .put("lastGuiButton", prior.optString("lastGuiButton", ""))
                          .put("handlersQueued", prior.optInt("handlersQueued", 0))
                          .put("handlersAttempted", prior.optInt("handlersAttempted", 0))
                          .put("handlerErrors", prior.optInt("handlerErrors", 0))
                          .put("handlersCompleted", prior.optInt("handlersCompleted", 0))
                          .put("handlersUnresolved", prior.optInt("handlersUnresolved", 0))
                          .put("handlersFailed", prior.optInt("handlersFailed", 0))
                          .put("journalChanges", prior.optInt("journalChanges", 0))
                          .put("guiWithJournalChanges", prior.optInt("guiWithJournalChanges", 0))
                          .put("handlerDomChanges", prior.optInt("handlerDomChanges", 0))
                          .put("handlersWithChanges", prior.optInt("handlersWithChanges", 0));
                }
            }
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

    public static synchronized boolean guiConsent(Context context, String id) {
        if (id == null || !id.matches("[0-9a-f-]{36}")) return false;
        try {
            JSONObject report = read(reportFile(context));
            return report != null && id.equals(report.optString("id", "")) &&
                    report.optBoolean("guiConsent", false) &&
                    !report.optBoolean("stopped", false);
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Bounded, local-only signal evidence from the actual Rook touch path.
     * Not called by APIs or mouse paths; never accepts remote AI inputs. */
    public static synchronized void noteGuiActivation(Context context, String id,
            String path, int queued, int attempted, int completed,
            int unresolved, int failed, int errors, int journalChanges,
            int handlerDomChanges, int handlersWithChanges) {
        if (path == null || path.length() < 1 || path.length() > 240 ||
                queued < 0 || queued > 1000 || attempted < 0 || attempted > queued ||
                completed < 0 || unresolved < 0 || failed < 0 ||
                (long) completed + unresolved + failed > attempted ||
                errors < 0 || errors > 1000 || journalChanges < 0 ||
                journalChanges > 1000 || handlerDomChanges < 0 ||
                handlerDomChanges > 1000 || handlersWithChanges < 0 ||
                handlersWithChanges > attempted ||
                handlersWithChanges > handlerDomChanges ||
                !guiConsent(context, id)) return;
        String safePath = path.replaceAll("[\\p{Cntrl}]", " ").trim();
        if (safePath.isEmpty()) return;
        try {
            JSONObject report = read(reportFile(context));
            if (report == null || !report.optBoolean("ready", false)) return;
            int total = report.optInt("guiActivated", 0);
            if (total < 0 || total >= 200) return;
            int priorQueued = report.optInt("handlersQueued", 0);
            int priorAttempted = report.optInt("handlersAttempted", 0);
            int priorErrors = report.optInt("handlerErrors", 0);
            int priorCompleted = report.optInt("handlersCompleted", 0);
            int priorUnresolved = report.optInt("handlersUnresolved", 0);
            int priorFailed = report.optInt("handlersFailed", 0);
            int priorJournal = report.optInt("journalChanges", 0);
            int priorGuiChanges = report.optInt("guiWithJournalChanges", 0);
            int priorHandlerJournal = report.optInt("handlerDomChanges", 0);
            int priorHandlersWithChanges = report.optInt("handlersWithChanges", 0);
            if (priorHandlerJournal < 0 || priorHandlerJournal > 200000 ||
                    priorHandlersWithChanges < 0 ||
                    priorHandlersWithChanges > priorAttempted ||
                    priorHandlersWithChanges > priorHandlerJournal ||
                    priorJournal < 0 || priorJournal > 200000 ||
                    priorGuiChanges < 0 || priorGuiChanges > total ||
                    priorGuiChanges > priorJournal ||
                    priorQueued < 0 || priorQueued > 200000 ||
                    priorAttempted < 0 || priorAttempted > priorQueued ||
                    priorErrors < 0 || priorErrors > 200000 ||
                    priorCompleted < 0 || priorUnresolved < 0 || priorFailed < 0 ||
                    (long) priorCompleted + priorUnresolved + priorFailed >
                            priorAttempted) return;
            report.put("guiSource", "Rook.touch.GuiButton.Activated")
                    .put("guiActivated", total + 1)
                    .put("lastGuiButton", safePath)
                    .put("handlersQueued", Math.min(200000, priorQueued + queued))
                    .put("handlersAttempted", Math.min(200000, priorAttempted + attempted))
                    .put("handlerErrors", Math.min(200000, priorErrors + errors))
                    .put("handlersCompleted", Math.min(200000, priorCompleted + completed))
                    .put("handlersUnresolved", Math.min(200000, priorUnresolved + unresolved))
                    .put("handlersFailed", Math.min(200000, priorFailed + failed))
                    .put("journalChanges", Math.min(200000, priorJournal + journalChanges))
                    .put("guiWithJournalChanges",
                            priorGuiChanges + (journalChanges > 0 ? 1 : 0))
                    .put("handlerDomChanges",
                            Math.min(200000, priorHandlerJournal + handlerDomChanges))
                    .put("handlersWithChanges",
                            Math.min(200000, priorHandlersWithChanges + handlersWithChanges))
                    .put("recordedAt", System.currentTimeMillis());
            write(reportFile(context), report);
        } catch (Exception ignored) {
            // A diagnostic must never disrupt the player's touch callback.
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
            boolean signalReported = "Rook.touch.GuiButton.Activated".equals(
                    report.optString("guiSource", ""));
            if (report.has("guiSource") && !signalReported)
                return "Relatório de GUI com origem desconhecida: ignorado.";
            int count = signalReported ? report.optInt("guiActivated", -1) : 0;
            String path = signalReported ? report.optString("lastGuiButton", "") : "";
            if (signalReported && !report.optBoolean("guiConsent", false))
                return "Sinal de GUI sem permissão específica: ignorado.";
            return formatTouchEvidence(report.optBoolean("ready", false),
                    report.optInt("down", -1), report.optInt("up", -1),
                    report.optInt("cancel", -1),
                    report.optBoolean("stopped", false),
                    nativeReported, report.optInt("nativeDown", -1),
                    report.optInt("nativeUp", -1), report.optInt("nativeCancel", -1),
                    count, path,
                    report.optInt("handlersQueued", 0),
                    report.optInt("handlersAttempted", 0),
                    report.optInt("handlerErrors", 0),
                    report.optInt("handlersCompleted", 0),
                    report.optInt("handlersUnresolved", 0),
                    report.optInt("handlersFailed", 0),
                    report.optInt("journalChanges", 0),
                    report.optInt("guiWithJournalChanges", 0),
                    report.optInt("handlerDomChanges", 0),
                    report.optInt("handlersWithChanges", 0));
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
                false, -1, -1, -1, 0, "", 0, 0, 0);
    }

    static String formatTouchEvidence(boolean ready, int downs, int ups,
            int canceled, boolean stopped, boolean nativeReported,
            int nativeDown, int nativeUp, int nativeCancel) {
        return formatTouchEvidence(ready, downs, ups, canceled, stopped,
                nativeReported, nativeDown, nativeUp, nativeCancel, 0, "", 0, 0, 0);
    }

    static String formatTouchEvidence(boolean ready, int downs, int ups,
            int canceled, boolean stopped, boolean nativeReported,
            int nativeDown, int nativeUp, int nativeCancel,
            int guiActivated, String buttonPath) {
        return formatTouchEvidence(ready, downs, ups, canceled, stopped,
                nativeReported, nativeDown, nativeUp, nativeCancel,
                guiActivated, buttonPath, 0, 0, 0, 0, 0, 0);
    }

    static String formatTouchEvidence(boolean ready, int downs, int ups,
            int canceled, boolean stopped, boolean nativeReported,
            int nativeDown, int nativeUp, int nativeCancel,
            int guiActivated, String buttonPath,
            int handlersQueued, int handlersAttempted, int handlerErrors) {
        return formatTouchEvidence(ready, downs, ups, canceled, stopped, nativeReported,
                nativeDown, nativeUp, nativeCancel, guiActivated, buttonPath,
                handlersQueued, handlersAttempted, handlerErrors, 0, 0, 0);
    }

    static String formatTouchEvidence(boolean ready, int downs, int ups,
            int canceled, boolean stopped, boolean nativeReported,
            int nativeDown, int nativeUp, int nativeCancel,
            int guiActivated, String buttonPath,
            int handlersQueued, int handlersAttempted, int handlerErrors,
            int completed, int unresolved, int failed) {
        return formatTouchEvidence(ready, downs, ups, canceled, stopped,
                nativeReported, nativeDown, nativeUp, nativeCancel,
                guiActivated, buttonPath, handlersQueued, handlersAttempted,
                handlerErrors, completed, unresolved, failed, 0, 0);
    }

    static String formatTouchEvidence(boolean ready, int downs, int ups,
            int canceled, boolean stopped, boolean nativeReported,
            int nativeDown, int nativeUp, int nativeCancel,
            int guiActivated, String buttonPath,
            int handlersQueued, int handlersAttempted, int handlerErrors,
            int completed, int unresolved, int failed,
            int journalChanges, int guiWithJournalChanges) {
        return formatTouchEvidence(ready, downs, ups, canceled, stopped,
                nativeReported, nativeDown, nativeUp, nativeCancel,
                guiActivated, buttonPath, handlersQueued, handlersAttempted,
                handlerErrors, completed, unresolved, failed,
                journalChanges, guiWithJournalChanges, 0, 0);
    }

    static String formatTouchEvidence(boolean ready, int downs, int ups,
            int canceled, boolean stopped, boolean nativeReported,
            int nativeDown, int nativeUp, int nativeCancel,
            int guiActivated, String buttonPath,
            int handlersQueued, int handlersAttempted, int handlerErrors,
            int completed, int unresolved, int failed,
            int journalChanges, int guiWithJournalChanges,
            int handlerDomChanges, int handlersWithChanges) {
        if (!consistentTouchTotals(downs, ups, canceled) ||
                (nativeReported && !consistentTouchTotals(
                        nativeDown, nativeUp, nativeCancel)) ||
                guiActivated < 0 || guiActivated > 200 ||
                handlersQueued < 0 || handlersQueued > 200000 ||
                handlersAttempted < 0 || handlersAttempted > handlersQueued ||
                handlerErrors < 0 || handlerErrors > 200000 ||
                completed < 0 || unresolved < 0 || failed < 0 ||
                (long) completed + unresolved + failed > handlersAttempted ||
                journalChanges < 0 || journalChanges > 200000 ||
                guiWithJournalChanges < 0 || guiWithJournalChanges > guiActivated ||
                guiWithJournalChanges > journalChanges ||
                handlerDomChanges < 0 || handlerDomChanges > 200000 ||
                handlersWithChanges < 0 || handlersWithChanges > handlersAttempted ||
                handlersWithChanges > handlerDomChanges ||
                (guiActivated > 0 && (buttonPath == null || buttonPath.isEmpty() ||
                        buttonPath.length() > 240)))
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
        if (ready && guiActivated > 0) {
            out.append("SINAL REAL GuiButton.Activated emitido após toque: ")
                    .append(guiActivated).append("\n")
                    .append("Último botão (caminho local): ").append(buttonPath).append("\n")
                    .append("Handlers Luau encaminhados: ").append(handlersQueued).append("\n")
                    .append("Tentativas de iniciar handlers: ").append(handlersAttempted).append("\n")
                    .append("Erros registrados durante o despacho: ").append(handlerErrors).append("\n")
                    .append("Handlers concluídos na primeira retomada sem erro: ").append(completed).append("\n")
                    .append("Handlers sem conclusão observada na primeira retomada: ").append(unresolved).append("\n")
                    .append("Handlers com erro durante a tentativa: ").append(failed).append("\n")
                    .append("Handlers não acompanhados fora do sinal (ex.: Deferred): ")
                    .append(handlersQueued - handlersAttempted).append("\n")
                    .append("Registros de mudança no DOM durante Activated: ")
                    .append(journalChanges).append("\n")
                    .append("Sinais Activated com mudança DOM observada: ")
                    .append(guiWithJournalChanges).append("\n")
                    .append("Registros DOM durante handlers Luau observados: ")
                    .append(handlerDomChanges).append("\n")
                    .append("Handlers com mutações DOM na primeira retomada: ")
                    .append(handlersWithChanges).append("\n");
        } else {
            out.append("GuiButton.Activated por toque: não confirmado.\n");
        }
        out.append("Conclusão síncrona indica fim da primeira retomada sem erro, " +
                "não confirma efeitos finais no jogo. Encaminhamento e tentativa " +
                "de execução não comprovam conclusão do callback Luau, " +
                "mudanças no jogo ou efeito visual. " +
                "O diário DOM registra alterações na mesma janela síncrona, " +
                "inclusive durante handlers observados, mas não prova que " +
                "determinado script as causou, nem " +
                "que houve resultado visual ou persistência depois. " +
                "Zero registros não prova ausência de efeitos, inclusive se " +
                "o diário estiver desativado. " +
                "Sem coordenadas, gravação de tela ou envio à IA.");
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
