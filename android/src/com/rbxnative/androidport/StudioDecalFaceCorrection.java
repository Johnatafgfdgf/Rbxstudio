package com.rbxnative.androidport;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase 17: turn UNTRUSTED visual AI text into a single bounded suggestion,
 * NEVER into executable code. Only one existing Decal.Face property may be
 * changed after owner approval and a fresh independent MCP read.
 *
 * This helper has no network, storage, Android, API key, or editor access.
 */
public final class StudioDecalFaceCorrection {
    private StudioDecalFaceCorrection() {}

    public static final int MAX_MODEL_REPLY = 12000;
    private static final Pattern LINE = Pattern.compile(
        "(?m)^DECAL_FACE_SUGGESTION: (Front|Back|Top|Bottom|Left|Right) \\| " +
        "([^\\r\\n|]{8,160})$");

    public static final class Suggestion {
        public final String face, reason;
        private Suggestion(String face, String reason) {
            this.face = face;
            this.reason = reason;
        }
    }

    /**
     * The AI may omit the line if it cannot see the decal. Ambiguity, multiple
     * recommendations, control characters, and same-face guesses are rejected.
     */
    public static Suggestion fromVisualReply(String reply, String currentFace)
            throws IOException {
        StudioRobloxDecalPlacement.validateFace(currentFace);
        if (reply == null || reply.length() > MAX_MODEL_REPLY)
            throw new IOException("Resposta visual vazia ou acima do limite.");
        Matcher m = LINE.matcher(reply);
        Suggestion only = null;
        while (m.find()) {
            if (only != null)
                throw new IOException("A IA sugeriu faces conflitantes; nenhuma aceita.");
            String face = StudioRobloxDecalPlacement.validateFace(m.group(1));
            String reason = m.group(2).trim();
            if (reason.length() < 8 || face.equals(currentFace))
                throw new IOException("Não há uma correção de face distinta e explicada.");
            if (reason.matches(".*(?i)(https?://|loadstring|require\\s*\\().*"))
                throw new IOException("Instrução externa recusada na justificativa.");
            only = new Suggestion(face, reason);
        }
        // No suggestion is a legitimate outcome: the screenshot may not show
        // the relevant Part or be too ambiguous for an orientation judgment.
        return only;
    }

    public static final class Prepared {
        public final StudioRobloxDecalPlacement.Proposal original;
        public final String newFace;
        public final String decalUniqueId;

        private Prepared(StudioRobloxDecalPlacement.Proposal original,
                String newFace, String decalUniqueId) {
            this.original = original;
            this.newFace = newFace;
            this.decalUniqueId = decalUniqueId;
        }

        public String expectedReceipt() {
            return "UPDATED_DECAL:" + original.decalName;
        }
    }

    public static Prepared prepare(StudioRobloxDecalPlacement.Proposal original,
            Suggestion suggestion, String decalUniqueId) throws IOException {
        if (original == null || suggestion == null)
            throw new IOException("Nenhuma correção contextualizada disponível.");
        StudioRobloxDecalPlacement.validateAssetId(original.assetId);
        StudioRobloxDecalPlacement.validateTargetPath(original.targetPath);
        StudioRobloxDecalPlacement.validateDecalName(original.decalName);
        StudioRobloxDecalPlacement.validateFace(original.face);
        StudioRobloxDecalPlacement.validateFace(suggestion.face);
        if (original.face.equals(suggestion.face))
            throw new IOException("A face proposta já é a face atual.");
        if (decalUniqueId == null || decalUniqueId.length() < 1 ||
                decalUniqueId.length() > 160)
            throw new IOException("A identidade do Decal não foi confirmada.");
        return new Prepared(original, suggestion.face, decalUniqueId);
    }

    /**
     * Fixed Luau: never interpolates text from AI except a validated six-value
     * face enum. The target and asset ID are from an authorized, read-back
     * confirmed creation, not arbitrary model-supplied paths.
     */
    public static String fixedLuau(Prepared p) throws IOException {
        if (p == null) throw new IOException("Proposta ausente.");
        StudioRobloxDecalPlacement.Proposal o = p.original;
        StudioRobloxDecalPlacement.validateAssetId(o.assetId);
        StudioRobloxDecalPlacement.validateTargetPath(o.targetPath);
        StudioRobloxDecalPlacement.validateDecalName(o.decalName);
        StudioRobloxDecalPlacement.validateFace(o.face);
        StudioRobloxDecalPlacement.validateFace(p.newFace);
        String[] segments = o.targetPath.split("\\.");
        StringBuilder code = new StringBuilder()
                .append("local ws = game:GetService(\"Workspace\")\n")
                .append("local parent = ws\n");
        if (segments.length == 3)
            code.append("parent = ws:FindFirstChild(\"")
                    .append(segments[1]).append("\")\n")
                    .append("if not parent or parent.ClassName ~= \"Model\" or ")
                    .append("parent.Parent ~= ws then return \"STALE_MODEL\" end\n");
        code.append("local part = parent:FindFirstChild(\"")
                .append(segments[segments.length - 1]).append("\")\n")
                .append("if not part or part.ClassName ~= \"Part\" or ")
                .append("part.Parent ~= parent then return \"STALE_PART\" end\n")
                .append("local decal = part:FindFirstChild(\"")
                .append(o.decalName).append("\")\n")
                .append("if not decal or decal.ClassName ~= \"Decal\" or ")
                .append("decal.Parent ~= part then return \"STALE_DECAL\" end\n")
                .append("if decal.Texture ~= \"rbxassetid://")
                .append(o.assetId).append("\" then return \"STALE_TEXTURE\" end\n")
                .append("if decal.Face ~= Enum.NormalId.")
                .append(o.face).append(" then return \"STALE_FACE\" end\n")
                .append("decal.Face = Enum.NormalId.").append(p.newFace).append("\n")
                .append("return \"").append(p.expectedReceipt()).append("\"");
        return code.toString();
    }
}
