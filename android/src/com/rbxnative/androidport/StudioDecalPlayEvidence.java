package com.rbxnative.androidport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Strictly local, read-only, opt-in evidence of an already created decal.
 * It does not run Play, fetch pixels, inspect the editor, call MCP, or send
 * Output/asset data to the AI. All of those operations are owned by the UI.
 *
 * A matching diagnostic is NOT proof that an asset failed to render. Silence
 * is NOT proof that an asset loaded. The only actual visual proof available
 * to this app is an explicitly authorized PixelCopy from a real Play window.
 */
public final class StudioDecalPlayEvidence {
    private StudioDecalPlayEvidence() {}

    public static final int MAX_LOG_CHARS = 12000;
    public static final int MAX_MATCHING_LINES = 4;
    public static final int MAX_LINE_CHARS = 180;

    public enum Level { NOT_PLAY, NOT_OBSERVED, POSSIBLE_LOAD_FAILURE }

    public static final class Result {
        public final Level level;
        public final int matchingLines;
        public final int possibleFailures;
        public final String summary;

        private Result(Level level, int matchingLines, int possibleFailures,
                String summary) {
            this.level = level;
            this.matchingLines = matchingLines;
            this.possibleFailures = possibleFailures;
            this.summary = summary;
        }
    }

    /**
     * Scan at most one bounded MCP Output response. Only lines with an exact
     * numeric asset-id token can be attributed as potentially relevant.
     * This intentionally does NOT treat other Play errors as decal errors.
     */
    public static Result inspect(String mode, String output, String assetId,
            String decalPath) throws IOException {
        StudioRobloxDecalPlacement.validateAssetId(assetId);
        int finalDot = decalPath == null ? -1 : decalPath.lastIndexOf('.');
        if (finalDot < 1 || finalDot == decalPath.length() - 1)
            throw new IOException("Caminho do decal inválido.");
        StudioRobloxDecalPlacement.validateTargetPath(
                decalPath.substring(0, finalDot));
        StudioRobloxDecalPlacement.validateDecalName(
                decalPath.substring(finalDot + 1));
        String prefix = "DECAL • EVIDÊNCIA LOCAL DO OUTPUT\n" +
                "Objeto: " + decalPath + "\nAsset ID: " + assetId + "\n";
        if (!"PLAY_LOCAL_CONFIRMADO".equals(mode)) {
            return new Result(Level.NOT_PLAY, 0, 0,
                    prefix + "Play Client/Server não confirmado pelo MCP. " +
                    "Nenhuma conclusão sobre a textura.");
        }
        if (output == null || output.length() > MAX_LOG_CHARS)
            throw new IOException("Output local ausente ou maior que o limite permitido.");

        int matching = 0, failures = 0;
        List<String> relevant = new ArrayList<>();
        for (String line : output.split("\\r?\\n")) {
            if (!containsExactId(line, assetId)) continue;
            matching++;
            String normalized = line.toLowerCase(Locale.ROOT);
            boolean suspect = normalized.contains("failed to load") ||
                    normalized.contains("failed to fetch") ||
                    normalized.contains("asset failed") ||
                    normalized.contains("not authorized") ||
                    normalized.contains("not permitted") ||
                    normalized.contains("permission denied") ||
                    normalized.contains("asset is unavailable") ||
                    normalized.contains("content blocked") ||
                    normalized.contains("moderation");
            if (suspect) failures++;
            if (relevant.size() < MAX_MATCHING_LINES) {
                String safe = line.replaceAll("[\\x00-\\x1f\\x7f]", " ").trim();
                if (safe.length() > MAX_LINE_CHARS)
                    safe = safe.substring(0, MAX_LINE_CHARS) + "…";
                relevant.add((suspect ? "possível falha: " : "referência: ") + safe);
            }
        }
        Level level = failures > 0 ? Level.POSSIBLE_LOAD_FAILURE : Level.NOT_OBSERVED;
        StringBuilder report = new StringBuilder(prefix)
                .append("Estado: Play local indicado pelo MCP.\n")
                .append("Linhas que mencionam o Asset ID: ").append(matching).append('\n')
                .append("Indícios de carregamento recusado: ").append(failures).append('\n');
        for (String note : relevant) report.append("• ").append(note).append('\n');
        if (failures > 0) {
            report.append("Há sinais de possível erro do asset. Confira " +
                    "permissão, moderação, ID e Output no Roblox Studio.\n");
        } else if (matching == 0) {
            report.append("O Output não mencionou esse ID; ausência de logs " +
                    "NÃO confirma a textura carregada.\n");
        } else {
            report.append("O ID apareceu no Output, mas sem erro reconhecido. " +
                    "Isso NÃO confirma o carregamento visual.\n");
        }
        report.append("A leitura pode estar incompleta ou conter mensagens de " +
                "outros sistemas. Para conferir pixels, autorize uma captura " +
                "PixelCopy do Play Rook e revise visualmente a cena. " +
                "Nenhum dado foi enviado à IA e nenhuma edição foi feita.");
        return new Result(level, matching, failures, report.toString());
    }

    public static boolean containsExactId(String line, String id) {
        if (line == null || id == null || id.isEmpty()) return false;
        int start = 0;
        while ((start = line.indexOf(id, start)) >= 0) {
            int end = start + id.length();
            boolean left = start == 0 || !Character.isDigit(line.charAt(start - 1));
            boolean right = end == line.length() || !Character.isDigit(line.charAt(end));
            if (left && right) return true;
            start++;
        }
        return false;
    }
}
