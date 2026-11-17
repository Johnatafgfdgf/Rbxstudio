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
    'PartChangeProposal change = validatePartChangeProposal(',
    'decision, lastInspectedPath, lastInspectedOutput)',
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
    'if (!move && !resize && !recolor && !rotate)',
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


# Absolute XYZ rotation is optional on both newly-created and existing Parts.
# The model returns numbers only; Java constructs fixed CFrame Luau and guards
# against an existing Part rotating between inspection and confirmation.
for required in [
    r'\"rotation\":[0,0,0]',
    r'\"rotation\":[0,90,0]',
    'partVector(input, "rotation", -180, 180)',
    'partVector(decision, "rotation", -180, 180)',
    'boolean rotate = decision.has("rotation")',
    'if (!move && !resize && !recolor && !rotate)',
    'private static double[] inspectedRotation(JSONObject properties)',
    'if (values.length != 9)',
    'beforeRotation = inspectedRotation(original)',
    'this.beforeRotation = beforeRotation',
    'this.rotation = rotation',
    'private static String luauRadians(double[] degrees)',
    'private static String luauNumbers(double[] values)',
    'CFrame.Angles(',
    'local oldRotation = {select(4, p.CFrame:GetComponents())}',
    'local expectedRotation = {',
    'oldRotation[i] - expectedRotation[i]',
    'if (part.rotation != null) {',
    'part.position == null ? "p.Position"',
    'Rotação XYZ (graus):',
    'Orientação atual (matriz 3x3):',
]:
    assert required in java, required
assert java.count('callMcp(paired, "execute_luau", args)') == 1
assert 'case "execute_luau"' not in java
assert 'callMcp(mcpPairing, action' not in java


# API-only remote model selection with verified free-pricing signals.
# No hard-coded promise of free OpenAI/Claude API usage.
for required in [
    'private static int modelTier(int p, JSONObject item, String id)',
    'id.endsWith(":free")',
    '"openrouter/free".equals(id)',
    'price.optString("prompt", "NaN")',
    'price.optString("completion", "NaN")',
    '"gemini-3.5-flash-lite"',
    '"gemini-3.8-flash"',
    'supportedGenerationMethods',
    'URLEncoder.encode(nextToken, "UTF-8")',
    'Collections.sort(available, (a, b) ->',
    'tier == 3 ? "GRÁTIS • "',
    'tier == 2 ? "FREE TIER* • "',
    'tier == 1 ? "PREÇO ZERO** • "',
    'onlyFree.setText("Mostrar somente gratuitos, free tier ou preço zero")',
    'autoFallback.setText(',
    'backupPicker = new Spinner(this)',
    'discoveredModels.addAll(available)',
    'renderModelList();',
    'selectedModelId()',
    'selectedBackupModel(model)',
    'entry.tier >= 2 && !entry.id.equals(primary)',
]:
    assert required in java, required

# Fallback is limited to one opt-in, free-tier-marked, same-provider model.
# 401/403 and malformed requests must never trigger automatic switching.
for required in [
    'private static final class ApiHttpException extends IOException',
    'code == 402 || code == 429 || code == 502 || code == 503',
    'code == 504 || code == 529',
    'route.switched ||',
    'route.backup.isEmpty() || route.backup.equals(route.active)',
    'route.requests >= maxRequests',
    'route.requests++',
    'route.switched = true',
    'route.active = backup',
    'requestModelWithFreeFallback(p, key, route,',
    'turns, allowProposals, epoch, MAX_AGENT_CALLS)',
    'turns, false, epoch, 2)',
    'final ModelRoute route = new ModelRoute(model, selectedBackupModel(model))',
    'throw new ApiHttpException(code',
    'if (code == 401 || code == 403) throw new ApiHttpException',
    'if (code == 402) throw new ApiHttpException',
    'if (code == 429) throw new ApiHttpException',
]:
    assert required in java, required
assert java.count('new ModelRoute(model, selectedBackupModel(model))') == 1
assert 'requestModelWithFreeFallback(p, key, route,' in java
assert 'case "multi_edit"' not in java
assert 'case "execute_luau"' not in java


# Compound 3D models stay bounded, app-generated, and confirmation-gated.
for required in [
    'COMPOSITE_PROTOCOL',
    'if ("propose_composite_create".equals(action))',
    'validateCompositeProposal(decision)',
    'pendingComposite',
    'confirmed',
    'private static CompositeProposal validateCompositeProposal(JSONObject data)',
    'entries.length() < 2 || entries.length() > 5',
    'part.name.equalsIgnoreCase',
    'validatePartProposal(safeCopy)',
    'private static String fixedCompositeLuau(CompositeProposal model)',
    'model = Instance.new',
    'local p = Instance.new',
    'p.Anchored = true',
    'p.CanCollide = true',
    'p.Parent = model',
    'model.Parent = ws',
    'private void ensureWorkspaceNameFree',
    'ensureWorkspaceNameFree(paired, model.name)',
    'private void confirmPendingComposite()',
    '.setPositiveButton("Sim, criar modelo",',
    'applyApprovedComposite(model)',
    'executeApproved3dCode(paired, fixedCompositeLuau(model))',
    'pendingComposite = includeProject.isChecked() && autoInspect.isChecked()',
]:
    if required == 'confirmed':
        continue  # acknowledgement comes from the actual Android AlertDialog, not this word
    if required == 'part.name.equalsIgnoreCase':
        required = 'prior.name.equalsIgnoreCase(part.name)'
    if required == 'model = Instance.new':
        required = 'Instance.new(\\"Model\\")'
    if required == 'local p = Instance.new':
        required = 'Instance.new(\\"Part\\")'
    assert required in java, required
assert java.count('callMcp(paired, "execute_luau", args)') == 1
assert 'case "execute_luau"' not in java
assert 'case "multi_edit"' not in java
assert 'callMcp(mcpPairing, action' not in java


# One opt-in BillboardGui label may be attached only after a real Part inspect.
# Text is quoted by Java and never injected as executable model-provided Luau.
for required in [
    'BILLBOARD_PROTOCOL',
    'proposeLabels.setChecked(false)',
    'proposeLabels.setText("Permitir propostas de etiquetas de texto 3D (BillboardGui)")',
    'if ("propose_billboard_create".equals(action))',
    'validateBillboardProposal(',
    'lastInspectedPath, lastInspectedOutput',
    'pendingBillboard',
    'private static JSONObject checkedBillboardTarget(',
    '!"Part".equals(obj.optString("className", ""))',
    'children.optInt("childrenCount", -1) != immediate.length()',
    'path + "." + name',
    'private static BillboardProposal validateBillboardProposal(',
    'path.matches("Workspace\\\\.',
    'safeField(data, "text", 80, true)',
    'optionalUpdateColor(data)',
    'height < 0.5 || height > 12',
    'private static String luauTextLiteral(String text)',
    'switch (ch) {',
    'if (ch < 0x20 || ch == 0x7f)',
    'String hex = Integer.toHexString(ch);',
    'escaped.append(hex);',
    'private static String fixedBillboardLuau(BillboardProposal label)',
    'Instance.new(\\"BillboardGui\\")',
    'Instance.new(\\"TextLabel\\")',
    'Instance.new(\\"UICorner\\")',
    'gui.Adornee = target',
    'textLabel.RichText = false',
    'luauTextLiteral(label.caption)',
    'gui.Parent = target',
    'private void confirmPendingBillboard()',
    '.setPositiveButton("Sim, criar etiqueta",',
    'private void applyApprovedBillboard(BillboardProposal label)',
    'readOnlyMcp(paired, "inspect_instance",',
    'label.originalId.equals(currentId)',
    'executeApproved3dCode(paired, fixedBillboardLuau(label))',
    'pendingBillboard = includeProject.isChecked() && autoInspect.isChecked()',
]:
    assert required in java, required
assert java.count('callMcp(paired, "execute_luau", args)') == 1
assert 'case "execute_luau"' not in java
assert 'callMcp(mcpPairing, action' not in java



# ScreenGui creation is limited to app-generated, responsive components.
# The only permitted optional interaction is an app-owned close action.
# A real MCP inspection, full child-name check, and user confirmation
# must all precede the one native editor write operation.
for required in [
    'SCREEN_GUI_PROTOCOL',
    'proposeScreens.setChecked(false)',
    'proposeScreens.setText("Permitir propostas de painéis ScreenGui para celular")',
    'if ("propose_screen_gui_create".equals(action))',
    'lastInspectedPath',
    'validateScreenGuiProposal(decision,',
    'private static JSONObject checkedScreenGuiRoot(',
    '"StarterGui".equals(root.optString("className", ""))',
    'children.optInt("childrenCount", -1) != immediate.length()',
    '("StarterGui." + name).equalsIgnoreCase(child.optString("path", ""))',
    'private static ScreenGuiProposal validateScreenGuiProposal(',
    'items.length() < 1 || items.length() > 3',
    'safeField(input, "gui_name", 40, true)',
    'safeField(input, "title", 60, true)',
    'safeField(new JSONObject().put("line", raw), "line", 90, true)',
    'optionalUpdateColor(new JSONObject().put("color", input.opt("accent")))',
    '"top".equals(position) || "center".equals(position)',
    'private static String fixedScreenGuiLuau(ScreenGuiProposal gui)',
    'Instance.new(\\"ScreenGui\\")',
    'Instance.new(\\"Frame\\")',
    'Instance.new(\\"UITextSizeConstraint\\")',
    'Instance.new(\\"UISizeConstraint\\")',
    'Instance.new(\\"UIListLayout\\")',
    'screen.ResetOnSpawn = false',
    'screen.IgnoreGuiInset = false',
    'title.RichText = false',
    'line.RichText = false',
    'luauTextLiteral(gui.title)',
    'luauTextLiteral(gui.lines.get(i))',
    'screen.Parent = service',
    'private void confirmPendingScreen()',
    '.setPositiveButton("Sim, criar painel",',
    'private void applyApprovedScreen(ScreenGuiProposal gui)',
    'readOnlyMcp(paired, "inspect_instance",',
    'checkedScreenGuiRoot(current, gui.name)',
    'gui.originalServiceId.equals(root.optString("uniqueId", ""))',
    'executeApproved3dCode(paired, fixedScreenGuiLuau(gui))',
    'pendingScreen = includeProject.isChecked() && autoInspect.isChecked()',
]:
    assert required in java, required

assert java.count('callMcp(paired, "execute_luau", args)') == 1
assert 'case "execute_luau"' not in java
assert 'callMcp(mcpPairing, action' not in java


# Close-button feature never grants model-authored scripts. The app generates
# the TextButton and attaches exactly its own small close-only LocalScript.
for required in [
    r'\"close_button\":true',
    r'\"button_label\":\"Fechar\"',
    'final boolean closeButton;',
    'this.closeButton = closeButton;',
    'boolean closeButton = false;',
    'if (input.has("close_button")) {',
    'requested instanceof Boolean',
    'if (!closeButton && input.has("button_label"))',
    'safeField(input, "button_label", 24, false)',
    'buttonLabel = "Fechar"',
    'int panelHeight = 92 + gui.lines.size() * 46 + (gui.closeButton ? 54 : 0);',
    'private static final String CLOSE_PANEL_SCRIPT',
    'button.Activated:Connect(function()',
    "FindFirstAncestorOfClass('ScreenGui')",
    'if panel then panel.Enabled = false end end)',
    'if (gui.closeButton) {',
    'Instance.new(\\"TextButton\\")',
    'close.AutoButtonColor = true',
    'close.Active = true',
    'close.TextScaled = true',
    'close.RichText = false',
    'close.Text = ',
    'luauTextLiteral(gui.buttonLabel)',
    'Instance.new(\\"LocalScript\\")',
    'closeLogic.Name = \\"ClosePanelClient\\"',
    'luauTextLiteral(CLOSE_PANEL_SCRIPT)',
    'if not sourceOk then error(\\"CLOSE_SCRIPT_SOURCE_DENIED\\") end',
    'closeLogic.Parent = close',
    'close.Parent = panel',
    'panel.Parent = screen',
    'gui.closeButton',
    'gui.buttonLabel',
    'executeApproved3dCode(paired, fixedScreenGuiLuau(gui))',
]:
    assert required in java, required
# The edit logic is still only reachable through the button and a fixed source;
# no new capability for the LLM to invoke arbitrary edit or execution tools.
assert java.count('callMcp(paired, "execute_luau", args)') == 1
assert 'case "execute_luau"' not in java
assert 'callMcp(mcpPairing, action' not in java


# A genuinely interactive item-receiving button creates a bounded 4-service
# system, not a decorative button, arbitrary model script or Toolbox import.
for required in [
    'RECEIVE_TOOL_PROTOCOL',
    'proposeKits.setChecked(false)',
    'Permitir propostas de sistema',
    'final boolean allowKits = agent && codeConsent && proposeKits.isChecked()',
    'if ("propose_receive_tool_create".equals(action))',
    'validateReceiveToolProposal(decision,',
    'private static ReceiveToolProposal validateReceiveToolProposal(',
    'safeField(input, "system_name", 25, true)',
    'system.matches("[A-Za-z][A-Za-z0-9_]{0,24}")',
    'safeField(input, "item_label", 30, true)',
    'safeField(input, "button_label", 30, false)',
    'checkedScreenGuiRoot(inspectedText, guiName)',
    'private static JSONObject checkedKitService(',
    'children.optInt("childrenCount", -1) != immediate.length()',
    'private void verifyKitDestinations(',
    '"StarterGui", "ReplicatedStorage"',
    '"ServerScriptService", "ServerStorage"',
    'kit.guiName(), kit.remoteName()',
    'kit.serverName(), kit.toolName()',
    'checkedKitService(text, services[i], names[i])',
    'private static String fixedReceiveToolServerScript(',
    'remote.OnServerEvent:Connect(function(player)',
    "player:IsA('Player')",
    "player:FindFirstChildOfClass('Backpack')",
    'template:Clone()',
    'item.Parent=bag',
    'players.PlayerRemoving:Connect(',
    'private static String fixedReceiveToolClientScript(',
    'button.Activated:Connect(function()',
    'remote:FireServer()',
    'private static String fixedReceiveToolLuau(',
    "Instance.new('RemoteEvent')",
    "Instance.new('Tool')",
    "Instance.new('PointLight')",
    "Instance.new('Script')",
    "Instance.new('ScreenGui')",
    "Instance.new('TextButton')",
    "Instance.new('LocalScript')",
    "handle.Name='Handle'",
    'tool.RequiresHandle=true',
    'tool.CanBeDropped=false',
    'luauTextLiteral(fixedReceiveToolServerScript(kit))',
    'luauTextLiteral(fixedReceiveToolClientScript(kit))',
    "if not serverOk then error('SERVER_SOURCE_DENIED') end",
    "if not clientOk then error('CLIENT_SOURCE_DENIED') end",
    'remote.Parent=rs',
    'tool.Parent=storage',
    'serverScript.Parent=server',
    'screen.Parent=starter',
    'private void confirmPendingReceiveTool()',
    '.setPositiveButton("Sim, criar sistema",',
    'private void applyApprovedReceiveTool(ReceiveToolProposal kit)',
    'verifyKitDestinations(paired, kit)',
    'executeApproved3dCode(paired, command)',
    'pendingKit = includeProject.isChecked() && includeCode.isChecked()',
]:
    assert required in java, required
# No direct arbitrary execution entry point for a model response.
assert java.count('callMcp(paired, "execute_luau", args)') == 1
assert 'case "execute_luau"' not in java
assert 'callMcp(mcpPairing, action' not in java
assert java.index('verifyKitDestinations(paired, kit)') < java.index(
    'executeApproved3dCode(paired, command)'
)

# A runtime collision is a hard error, not a misleading "success" string.
# Service parenting is guarded and all newly staged objects are destroyed on
# an attachment failure. No scripts are parented before Source is validated.
for required in [
    "then error('KIT_NAME_EXISTS') end",
    'local serverOk=pcall(function() serverScript.Source=',
    'local clientOk=pcall(function() logic.Source=',
    'local committed,commitError=pcall(function()',
    "error('KIT_COMMIT_FAILED: '..tostring(commitError))",
    'pcall(function() serverScript:Destroy() end)',
    'pcall(function() screen:Destroy() end)',
    'pcall(function() tool:Destroy() end)',
    'pcall(function() remote:Destroy() end)',
]:
    assert required in java, required
assert java.index('if not clientOk then error(') < java.index('remote.Parent=rs')
assert java.index('remote.Parent=rs') < java.index('serverScript.Parent=server')

assert 'id("mobile-ai-providers"' not in native_patch  # action uses compact_action
assert '"mobile-ai-providers"' in native_patch
assert 'open_ai_hub(&name, &pairing)' in native_patch
assert 'pub fn open_ai_hub(project: &str, pairing: &str)' in gpui_patch
print("RBX Studio AI contracts: approved fixed close button, ScreenGui, Parts and script tools passed (static Java; device tests pending).")
