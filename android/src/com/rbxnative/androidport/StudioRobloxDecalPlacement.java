package com.rbxnative.androidport;

import java.io.IOException;

/**
 * Fixed, narrowly scoped Luau generator for ONE reviewed Roblox decal.
 * No network requests, no user-defined scripts, no LLM output is executed.
 * Target uniqueness and identity are additionally verified by MCP before
 * calling execute_luau; the returned receipt must be checked and the new
 * Decal inspected in the actual Edit datamodel.
 */
public final class StudioRobloxDecalPlacement {
    private StudioRobloxDecalPlacement() {}

    public static final class Proposal {
        public final String assetId, targetPath, decalName, face;
        public final String originalUniqueId;

        private Proposal(String assetId, String targetPath,
                String decalName, String face, String originalUniqueId) {
            this.assetId = assetId;
            this.targetPath = targetPath;
            this.decalName = decalName;
            this.face = face;
            this.originalUniqueId = originalUniqueId;
        }

        public String decalPath() { return targetPath + "." + decalName; }
        public String textureUri() { return "rbxassetid://" + assetId; }
        public String receipt() { return "CREATED_DECAL:" + decalName; }
    }

    public static String validateAssetId(String assetId) throws IOException {
        if (assetId == null || !assetId.matches("[1-9][0-9]{0,18}"))
            throw new IOException("Asset ID não confirmado ou inválido.");
        try {
            if (Long.parseLong(assetId) <= 0) throw new NumberFormatException();
        } catch (NumberFormatException invalid) {
            throw new IOException("Asset ID fora do intervalo permitido.");
        }
        return assetId;
    }

    public static String validateTargetPath(String path) throws IOException {
        if (path == null || !path.matches(
                "Workspace\\.[A-Za-z][A-Za-z0-9_]{0,39}" +
                "(\\.[A-Za-z][A-Za-z0-9_]{0,39})?"))
            throw new IOException("Escolha uma Part em Workspace ou Workspace.Model.");
        return path;
    }

    public static String validateDecalName(String name) throws IOException {
        if (name == null || !name.matches("[A-Za-z][A-Za-z0-9_]{0,39}"))
            throw new IOException("Nome do decal deve usar somente letras, números e _.");
        return name;
    }

    public static String validateFace(String face) throws IOException {
        if (!("Front".equals(face) || "Back".equals(face) ||
                "Top".equals(face) || "Bottom".equals(face) ||
                "Left".equals(face) || "Right".equals(face)))
            throw new IOException("A face precisa ser uma das seis faces de uma Part.");
        return face;
    }

    public static Proposal reviewed(String assetId, String targetPath,
            String decalName, String face, String originalUniqueId)
            throws IOException {
        validateAssetId(assetId);
        validateTargetPath(targetPath);
        validateDecalName(decalName);
        validateFace(face);
        if (originalUniqueId == null || originalUniqueId.length() < 1 ||
                originalUniqueId.length() > 160)
            throw new IOException("MCP não confirmou a identidade da Part.");
        return new Proposal(assetId, targetPath, decalName, face, originalUniqueId);
    }

    public static String fixedLuau(Proposal proposal) throws IOException {
        if (proposal == null)
            throw new IOException("Proposta de decal ausente.");
        // Revalidate everything, even if an instance was passed from elsewhere.
        validateAssetId(proposal.assetId);
        validateTargetPath(proposal.targetPath);
        validateDecalName(proposal.decalName);
        validateFace(proposal.face);
        String[] segments = proposal.targetPath.split("\\.");
        StringBuilder luau = new StringBuilder()
                .append("local ws = game:GetService(\"Workspace\")\n")
                .append("local parent = ws\n");
        if (segments.length == 3) {
            luau.append("parent = ws:FindFirstChild(\"")
                    .append(segments[1]).append("\")\n")
                    .append("if not parent or parent.ClassName ~= \"Model\" or ")
                    .append("parent.Parent ~= ws then return \"INVALID_MODEL\" end\n");
        }
        luau.append("local target = parent:FindFirstChild(\"")
                .append(segments[segments.length - 1]).append("\")\n")
                .append("if not target or target.ClassName ~= \"Part\" or ")
                .append("target.Parent ~= parent then return \"INVALID_PART\" end\n")
                .append("if target:FindFirstChild(\"").append(proposal.decalName)
                .append("\") then return \"DECAL_EXISTS\" end\n")
                .append("local decal = Instance.new(\"Decal\")\n")
                .append("decal.Name = \"").append(proposal.decalName).append("\"\n")
                .append("decal.Texture = \"rbxassetid://")
                .append(proposal.assetId).append("\"\n")
                .append("decal.Face = Enum.NormalId.").append(proposal.face).append("\n")
                .append("decal.Parent = target\n")
                .append("return \"").append(proposal.receipt()).append("\"");
        return luau.toString();
    }
}
