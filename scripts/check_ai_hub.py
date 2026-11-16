#!/usr/bin/env python3
"""AI hub source contracts: no simulated replies or silent editing permissions.

This is a static smoke test; only device tests and real API credentials can
prove provider access and task execution. Never use real keys in CI.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
java = (ROOT / "android/src/com/rbxnative/androidport/StudioAiHub.java").read_text()
manifest = (ROOT / "android/StudioManifest.xml").read_text()
native_patch = (ROOT / "patches/rbx-native--android-port.patch").read_text()
gpui_patch = (ROOT / "patches/gpui-mobile--android-port.patch").read_text()

assert 'android:name="com.rbxnative.androidport.StudioAiHub" android:exported="false"' in manifest
for provider, endpoint in [
    ("OpenAI", "https://api.openai.com/v1/models"),
    ("Gemini", "https://generativelanguage.googleapis.com/v1beta/models"),
    ("Claude", "https://api.anthropic.com/v1/models"),
    ("OpenRouter", "https://openrouter.ai/api/v1/models"),
]:
    assert provider in java and endpoint in java
for path in [
    "https://api.openai.com/v1/responses",
    "https://api.anthropic.com/v1/messages",
    "https://openrouter.ai/api/v1/chat/completions",
    ":generateContent",
]:
    assert path in java, path
for safety in [
    'StudioServices.credential(this, "write"',
    'StudioServices.credential(this, "read"',
    'StudioServices.credential(this, "delete"',
    "conn.setInstanceFollowRedirects(false)",
    'socket.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 3000)',
    'put("name", tool)',
    'includeProject.setChecked(false)',
    'String message = include',
    'MAX_RESPONSE_BYTES',
]:
    assert safety in java, safety
assert 'callMcp(paired, "execute_luau", args)' in java, "Fixed Part creation must call real MCP"
assert java.count('callMcp(paired, "execute_luau", args)') == 1
assert 'case "execute_luau"' not in java, "Model cannot dispatch arbitrary Luau"
assert 'String command = create != null ? fixedPartCreateLuau(create) :' in java
assert 'fixedPartUpdateLuau(update)' in java
assert 'JSONObject args = new JSONObject().put("datamodel_type", "Edit")' in java
assert '"insert_asset"' not in java, "Chat must not import Toolbox models without review"
assert '"save_place"' not in java, "Chat must not persist projects without approval"
assert '"publish_animation"' not in java, "Chat must not publish assets without approval"
# Read-only agent: only a fixed MCP tool can run, with opt-in project disclosure.
for required in [
    "MAX_AGENT_READS = 3",
    "MAX_AGENT_CALLS = MAX_AGENT_READS + 1",
    "autoInspect.setChecked(false)",
    "includeProject.isChecked()",
    'if (!("search_game_tree".equals(action)',
    "readOnlyMcp(mcpPairing, action, decision)",
    "readExplorerViaMcp(paired, 3)",
    "epoch != requestEpoch",
    "cancel.setOnClickListener",
    "String tree = readOnlyMcp",
    "AGENT_PROTOCOL",
    'case "search_game_tree"',
    'case "inspect_instance"',
    'case "script_search"',
    'case "get_studio_state"',
    'case "script_read"',
    'case "get_console_output"',
    "includeCode.setChecked(false)",
    "boolean codeConsent",
    "codeConsent = agent && includeProject.isChecked() && includeCode.isChecked()",
    'if (!codeConsent && ("script_read".equals(action) || "get_console_output".equals(action)))',
    '"should_read_entire_file", false',
    '"start_line_one_indexed", start',
    '"end_line_one_indexed_inclusive", Math.min(start + 119, 2000000)',
    "safeField(selection",
    "switch (tool)",
    "abortInFlight();",
    "activeRequest = conn",
    "activeSocket = socket",
    "socket.connect(new InetSocketAddress",

]:
    assert required in java, required
# Check explicit denial of all model-selected non-read actions.
assert 'throw new IOException("O modelo solicitou ferramenta não permitida")' in java
assert 'if (agent && (mcpPairing.isEmpty() || !includeProject.isChecked()))' in java

# Preview-only proposal path: the model must read real Luau before proposing one edit.
for required in [
    "proposeEdits.setChecked(false)",
    "boolean allowProposals",
    "if (\"propose_script_edit\".equals(action))",
    "validateProposal(decision, lastReadPath,",
    "lastScriptOutput = tree",
    "lastReadPath = safeField(decision",
    "lastReadOutput.contains(oldText)",
    "MAX_EDIT_OLD = 400",
    "MAX_EDIT_NEW = 2400",
    "proposed = outcome.proposal",
    "renderPendingEdit();",
    "confirmPendingEdit()",
    ".setPositiveButton(edit.create ? \"Sim, criar script\" : \"Sim, aplicar edição\",",
    "(dialog, which) -> applyApprovedEdit(edit)",
    "if (busy || pendingEdit != edit || mcpPairing.isEmpty()) return;",
    "String latest = readOnlyMcp(paired, \"script_read\", read);",
    "if (!latest.contains(edit.oldText))",
    'put("replace_all", false)',
    'put("datamodel_type", "Edit")',
    'put("edits", new JSONArray().put(replacement))',
    'String outcome = callMcp(paired, "multi_edit", args);',
    "pendingEdit = includeProject.isChecked() && includeCode.isChecked() &&",
    "requestClose()",
]:
    assert required in java, required
# Exactly one native write entry point; no model-selected write tools or project publication.
assert java.count('callMcp(paired, "multi_edit", args)') == 1
assert java.index('String latest = readOnlyMcp(paired, "script_read", read);') < java.index(
    'String outcome = callMcp(paired, "multi_edit", args);'
)

# 3D Part creation is typed and bounded; the model only returns a proposal.
for required in [
    'proposeParts.setChecked(false)',
    'PART_PROTOCOL',
    'if ("propose_part_create".equals(action))',
    'lastSearchedParent',
    'validatePartProposal(decision)',
    'private static PartProposal validatePartProposal(JSONObject input)',
    'partVector(input, "position", -256, 256)',
    'partVector(input, "size", 0.2, 64)',
    'name.matches("[A-Za-z][A-Za-z0-9_]{0,39}")',
    'value != Math.floor(value)',
    'private static String fixedPartCreateLuau(PartProposal part)',
    'local p = Instance.new',
    'p.Anchored = true',
    'p.CanCollide = true',
    'p.Parent = ws',
    'private void ensurePartNameFree',
    'if ("No instances found.".equals(result)) return;',
    'confirmPendingPart()',
    '.setPositiveButton("Sim, criar peça", (dialog, which) -> applyApprovedPart(part))',
    'ensurePartNameFree(paired, part)',
    'runApprovedPartCommand(paired, part, null)',
    'callMcp(paired, "execute_luau", args)',
    'proposedPart = outcome.part',
    'pendingPart = includeProject.isChecked()',
]:
    assert required in java, required
assert java.count('callMcp(paired, "execute_luau", args)') == 1
assert java.index('ensurePartNameFree(paired, part)') < java.index(
    'runApprovedPartCommand(paired, part, null)'
)
assert java.index('runApprovedPartCommand(paired, part, null)') < java.index(
    'callMcp(paired, "execute_luau", args)'
)
assert 'case "execute_luau"' not in java

assert 'case "multi_edit"' not in java
assert '"replace_all", true' not in java
assert 'callMcp(mcpPairing, action' not in java
# Only the app-built and user-confirmed Part template may invoke execute_luau.
assert 'case "execute_luau"' not in java
assert '"publish_place"' not in java


# Complete Luau script creation is a proposal until user confirmation.
for required in [
    "CREATE_PROTOCOL",
    'if ("propose_script_create".equals(action))',
    "validateCreateProposal(decision, lastSearchedParent)",
    'lastSearchedParent = safeField(decision, "path", 180, false)',
    "MAX_SCRIPT_SOURCE = 10000",
    'codeField(data, "source", MAX_SCRIPT_SOURCE, false)',
    '("ServerScriptService".equals(parent) || "ReplicatedStorage".equals(parent)',
    '"StarterPlayer.StarterPlayerScripts".equals(parent)',
    '"StarterPlayer.StarterCharacterScripts".equals(parent)',
    'name.matches("[A-Za-z][A-Za-z0-9_]{0,39}")',
    '"Script".equals(className) || "LocalScript".equals(className)',
    '"ModuleScript".equals(className)',
    "private void ensureCreateDestinationFree(",
    '"search_game_tree"',
    'if ("No instances found.".equals(result)) return;',
    'edit.path.equalsIgnoreCase(child.optString("fullPath", ""))',
    'if (edit.create) {',
    "ensureCreateDestinationFree(paired, edit);",
    'if (edit.create) args.put("className", edit.className);',
    'applyEdit.setText(edit.create ?',
]:
    assert required in java, required

# The read-only model decision loop cannot directly call a write RPC.
assert java.count('callMcp(paired, "multi_edit", args)') == 1
assert java.index('ensureCreateDestinationFree(paired, edit)') < java.index(
    'String outcome = callMcp(paired, "multi_edit", args);'
)
assert 'case "multi_edit"' not in java


# Existing Workspace Part edits require actual MCP inspect, a full original
# property snapshot, a visible review and a second inspection before write.
for required in [
    'PART_UPDATE_PROTOCOL',
    'if ("propose_part_update".equals(action))',
    'lastInspectedPath = safeField(decision, "path", 180, true);',
    'lastInspectedOutput = tree;',
    'validatePartChangeProposal(decision, lastInspectedPath, lastInspectedOutput)',
    'path.matches("Workspace\\\\.[A-Za-z][A-Za-z0-9_]{0,39}")',
    'verifiedPartInspection(path, inspectedText)',
    'if (!"Part".equals(result.optString("className", ""))',
    'result.optJSONObject("properties") == null',
    'inspectedComponents(original, "CFrame.Position"',
    'inspectedComponents(original, "Size"',
    'inspectedComponents(original, "Color"',
    'inspectedPartFields(original)',
    '"CFrame.Rotation"',
    'boolean move = decision.has("position")',
    'if (!move && !resize && !recolor)',
    'partVector(decision, "position", -256, 256)',
    'partVector(decision, "size", 0.2, 64)',
    'optionalUpdateColor(decision)',
    'pendingPartChange != null',
    'confirmPendingPartChange()',
    'verifyPartUnchanged(paired, change)',
    'Arrays.equals(inspectedPartFields(latest), proposal.beforeFields)',
    'fixedPartUpdateLuau(PartChangeProposal part)',
    'p:IsA(\\"Part\\")',
    'local oldColor = p.Color',
    'runApprovedPartCommand(paired, null, change)',
    'proposedPartChange = outcome.partChange',
    'pendingPartChange = includeProject.isChecked()',
]:
    assert required in java, required
assert java.count('callMcp(paired, "execute_luau", args)') == 1
assert java.index('verifyPartUnchanged(paired, change)') < java.index(
    'runApprovedPartCommand(paired, null, change)'
)
assert 'case "execute_luau"' not in java
assert 'callMcp(mcpPairing, action' not in java

assert 'id("mobile-ai-providers"' not in native_patch  # action uses compact_action
assert '"mobile-ai-providers"' in native_patch
assert 'open_ai_hub(&name, &pairing)' in native_patch
assert 'pub fn open_ai_hub(project: &str, pairing: &str)' in gpui_patch
print("Android AI hub, bounded Luau script and 3D Part create/update approval contracts passed (static; no device tests).")
