#!/usr/bin/env python3
"""AI hub source contracts: no simulated replies or silent editing permissions.

This is a static smoke test; only device tests and real API credentials can
prove provider access and task execution. Never use real keys in CI.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
java = (ROOT / "android/src/com/rbxnative/androidport/StudioAiHub.java").read_text()
gemini = (ROOT / "android/src/com/rbxnative/androidport/StudioGeminiModels.java").read_text()
manifest = (ROOT / "android/StudioManifest.xml").read_text()
native_patch = (ROOT / "patches/rbx-native--android-port.patch").read_text()
gpui_patch = (ROOT / "patches/gpui-mobile--android-port.patch").read_text()
engine_kit = (ROOT / "core/tests/ai_receive_tool.rs").read_text()

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
    assert path in java or path in gemini, path
for safety in [
    'StudioServices.credential(this, "write"',
    'StudioServices.credential(this, "read"',
    'StudioServices.credential(this, "delete"',
    "conn.setInstanceFollowRedirects(false)",
    'socket.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 3000)',
    'put("name", tool)',
    'includeProject.setChecked(false)',
    'final String message = (include',
    'MAX_RESPONSE_BYTES',
]:
    assert safety in java, safety
assert 'callMcp(paired, "execute_luau", args)' in java, "Fixed Part creation must call real MCP"
assert java.count('callMcp(paired, "execute_luau", args)') == 1
assert 'case "execute_luau"' not in java, "Model cannot dispatch arbitrary Luau"
assert 'String command = create != null ? fixedPartCreateLuau(create) :' in java
assert 'fixedPartUpdateLuau(update)' in java
assert 'JSONObject args = new JSONObject().put("datamodel_type", "Edit")' in java
agent = (ROOT / "android/src/com/rbxnative/androidport/StudioAiAgent.java").read_text()
assert '"toolbox_import".equals(name) && run.assets' in agent
assert '.put("strip_agent_asset", true)' in agent
assert 'StudioAiWeather' not in java and 'proposeWeather' not in java
assert '"save_place"' not in java, "Chat must not persist projects without approval"
assert 'host.approve("publish"' in agent
assert '.put("agent_approved",true)' in agent
assert 'approved_publish' in (ROOT / "upstream/rbx-native/crates/rbx_studio/src/shell/mcp.rs").read_text()
# Read-only agent: only a fixed MCP tool can run, with opt-in project disclosure.
for required in [
    "MAX_AGENT_READS = 3",
    "MAX_AGENT_CALLS = MAX_AGENT_READS + 1",
    "autoInspect.setChecked(false)",
    "includeProject.isChecked()",
    'if (!(schemaLookup || toolboxSearch || "search_game_tree".equals(action)',
    "readOnlyMcp(mcpPairing, action, decision)",
    "readExplorerViaMcp(paired, 3)",
    "epoch != requestEpoch",
    "cancel.setOnClickListener",
    "tree = callMcp(mcpPairing, StudioAiProjectEdit.SCHEMA",
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
    'if (!codeConsent && ("script_read".equals(action) || "script_grep".equals(action)',
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
    ".setPositiveButton(edit.create ? \"Criar script\" : \"Aplicar edição\",",
    "(dialog, which) -> applyApprovedEdit(edit)",
    "if (busy || pendingEdit != edit || mcpPairing.isEmpty()) return;",
    "String latest = readOnlyMcp(paired, \"script_read\", read);",
    "if (!latest.contains(edit.oldText))",
    'put("replace_all", false)',
    'put("datamodel_type", "Edit")',
    'put("edits", new JSONArray().put(replacement))',
    'String outcome = callMcp(paired, "multi_edit", scriptEditArguments(edit));',
    "pendingEdit = includeProject.isChecked() && includeCode.isChecked() &&",
    "requestClose()",
]:
    assert required in java, required
# Exactly one native write entry point; no model-selected write tools or project publication.
assert java.count('callMcp(paired, "multi_edit", scriptEditArguments(edit))') == 1
assert java.index('String latest = readOnlyMcp(paired, "script_read", read);') < java.index(
    'String outcome = callMcp(paired, "multi_edit", scriptEditArguments(edit));'
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
    'callMcp(paired, "validate_script_edit", scriptEditArguments(edit))',
    'showScriptEditConfirmation(edit, folders.toString())',
    '"search_game_tree"',
    'if (edit.create) args.put("className", edit.className).put("require_new", true);',
    'applyEdit.setText(edit.create ?',
]:
    assert required in java, required

# The read-only model decision loop cannot directly call a write RPC.
assert java.count('callMcp(paired, "multi_edit", scriptEditArguments(edit))') == 1
assert java.index('callMcp(paired, "validate_script_edit", scriptEditArguments(edit))') < java.index(
    'String outcome = callMcp(paired, "multi_edit", scriptEditArguments(edit));'
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
    'code == 402 || code == 404 || code == 429',
    'code == 502 || code == 503',
    'code == 504 || code == 529',
    'route.switched ||',
    'route.backup.isEmpty() || route.backup.equals(route.active)',
    'route.requests >= maxRequests',
    'route.requests++',
    'route.switched = true',
    'route.active = backup',
    'requestModelWithFreeFallback(p, key, route,',
    'turns, allowProposals || editorStep, epoch, agentCallLimit)',
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


# The engine regression must cover real source persistence, client/server
# RemoteEvents, Backpack duplicate checks and actual Android-style touch.
for required in [
    'ai_receive_tool_edit_luau_creates_source_and_tool_instances',
    'ai_receive_tool_play_delivers_once_to_backpack',
    'ai_receive_tool_mobile_tap_activated_invokes_local_script_and_server',
    'use rook_engine::{InputEvent, TouchPhase};',
    'TouchPhase::Begin',
    'TouchPhase::End',
    'play.viewport(960, 480)',
    'r:FireServer()',
    "script.Parent.Activated:Connect(function()",
    "RemoteEvent",
    "Tool",
    'GrantCount',
]:
    assert required in engine_kit, required


# All verified editor writes must reject MCP transport-level 'success'
# messages which actually contain Luau error or collision status strings.
for required in [
    'private static String checkedEditReceipt(String raw, String expected)',
    'if (raw == null || !expected.equals(raw.trim()))',
    'status.startsWith("NAME_EXISTS")',
    'status.startsWith("STALE_PART")',
    'status.startsWith("KIT_NAME_EXISTS")',
    'executeApproved3dCode(paired, command),',
    '"CREATED_RECEIVE_TOOL:" + kit.system',
    '"CREATED_SCREEN_GUI:" + gui.name',
    '"CREATED_BILLBOARD:" + label.name',
    '"CREATED_MODEL:" + model.name',
    '"UPDATED:" + change.path.substring("Workspace.".length())',
    '"CREATED:" + part.name',
]:
    assert required in java, required
# A separately consented Decal edit also requires the same exact MCP receipt.
assert java.count('checkedEditReceipt(') == 11  # Phase17 user-approved Decal face change
assert 'p.receipt()' in java
assert 'verifyPlacedRobloxDecal(paired, p)' in java
assert 'StudioDecalFaceCorrection.fixedLuau(prepared)' in java
assert 'checkedDecalFaceState(old, before, old.face, prepared.decalUniqueId)' in java
assert 'checkedDecalFaceState(old, after, prepared.newFace,' in java
assert 'setPositiveButton("Aprovar mudança da Face"' in java
assert 'p.textureUri().equals(props.optString("Texture", ""))' in java
assert 'p.originalUniqueId.equals(latest.optString("uniqueId", ""))' in java
for required in [
    'Testar conexão com editor (MCP local)',
    'private void diagnoseLocalEditor()',
    'readOnlyMcp(paired, "get_studio_state", new JSONObject())',
    'readOnlyMcp(paired, "inspect_instance",',
    '"StarterGui".equals(target.optString("className", ""))',
    'Nenhuma chave ou dado enviado ao provedor de IA.',
]:
    assert required in java, required
assert 'new JSONObject().put("code", "' not in java

# Every approved creation now distinguishes a successful MCP return from
# confirmed presence in the actual Edit datamodel. An uncertain read-back
# must not automatically execute the write a second time.
for required in [
    'private boolean verifiedEditorObject(String paired, String path, String expectedClass)',
    'private boolean verifiedScriptWrite(String paired, EditProposal edit)',
    'private static String readBackNotice(boolean verified, String path)',
    'readOnlyMcp(paired, "inspect_instance",',
    'name.equals(found.optString("name", ""))',
    'expectedClass.equals(found.optString("className", ""))',
    'verifiedEditorObject(paired, objectPath, "Part")',
    'verifiedEditorObject(paired, objectPath, "Model")',
    'verifiedEditorObject(paired, objectPath, "ScreenGui")',
    'verifiedEditorObject(paired, objectPath, "BillboardGui")',
    'verifiedEditorObject(paired, "ReplicatedStorage." + kit.remoteName(), "RemoteEvent")',
    'verifiedEditorObject(paired, "ServerScriptService." + kit.serverName(), "Script")',
    'verifiedEditorObject(paired, "ServerStorage." + kit.toolName(), "Tool")',
    'callMcp(paired, "verify_script_edit", args)',
    'verifiedScriptWrite(paired, edit)',
    'private boolean verifiedPartUpdate(String paired, PartChangeProposal change)',
    'verifiedPartUpdate(paired, change)',
    'private static double[] expectedAnglesMatrix(double[] degrees)',
    'a operação pode já ter sido aplicada.',
]:
    assert required in java, required
assert java.count('readBackNotice(') == 12  # includes approved Decal Face correction read-back
assert java.index('runApprovedPartCommand(paired, part, null)') < java.index(
    'verifiedEditorObject(paired, objectPath, "Part")')
assert java.index('String outcome = callMcp(paired, "multi_edit", scriptEditArguments(edit));') < java.index(
    'verifiedScriptWrite(paired, edit)')
assert 'retryApprovedWrite' not in java

# Project memory is explicitly opt-in, limited to metadata from actual
# verified MCP writes, AES-GCM protected through the existing Keystore service.
# Saved events are not user prompts, API keys, source code or screenshots.
for required in [
    'private String projectMemoryService() throws Exception',
    'MessageDigest.getInstance("SHA-256")',
    'StudioServices.credential(this, "write", projectMemoryService(), payload, "")',
    'StudioServices.credential(\n                this, "read", projectMemoryService(), "", "")',
    'private void recordEditorOutcome(String kind, String path, boolean verified)',
    'if (!verified || rememberVerified == null || !rememberVerified.isChecked()) return;',
    'new JSONObject().put("kind", kind).put("path", path)',
    'while (events.length() > MAX_PROJECT_MEMORY) events.remove(0)',
    'private String memoryForPrompt() throws Exception',
    'includeProject.isChecked() && includeMemory.isChecked()',
    'includeMemory.setChecked(false)',
    'StudioServices.credential(this, "delete",',
    'revokeSharedContext(); // Also cancels active model reads.',
    'revokeSharedContext(); // Project data must not remain in stale turns.',
    'revokeSharedContext(); // Do not resend approved memory from old turns.',
    'recordEditorOutcome("CREATE_PART", objectPath, verified)',
    'recordEditorOutcome("CREATE_MODEL", objectPath, allPartsVerified)',
    'recordEditorOutcome("UPDATE_PART", change.path, verified)',
    'recordEditorOutcome("CREATE_SCREEN_GUI", objectPath, verified)',
    'recordEditorOutcome("CREATE_BILLBOARD", objectPath, verified)',
    'recordEditorOutcome("CREATE_TOOL_KIT", kit.system, verified)',
    'recordEditorOutcome(edit.create ? "CREATE_SCRIPT" : "UPDATE_SCRIPT",',
]:
    assert required in java, required
assert java.count('recordEditorOutcome(') == 13  # includes correction write audit
assert java.count('saveProjectMemory(') == 2  # helper + metadata-only recorder
assert java.index('if (!verified || rememberVerified == null || !rememberVerified.isChecked()) return;') < java.index('saveProjectMemory(events);')
assert 'MAX_PROJECT_MEMORY = 16' in java
request_method = java[java.index('private JSONObject requestModelWithFreeFallback('):
                      java.index('private static String messageUrl(')]
assert 'if (epoch != requestEpoch) throw new IOException("Cancelado");' in request_method
assert request_method.index('if (epoch != requestEpoch)') < request_method.index('route.requests++;')
# A consent withdrawal also invalidates any ongoing model read and prevents
# a late editor response from repopulating revoked context.
for required in [
    'private void revokeSharedContext()',
    'if (busy && !applyingEdit)',
    'requestEpoch++;',
    'abortInFlight();',
    'if (includeProject != null && includeProject.isChecked()) {',
]:
    assert required in java, required
# Explicit continuation keeps app/model tool history consistent and does not
# start chargeable work without a button press and session consent.
for required in [
    'continueTask = button("Continuar tarefa anterior • usa a API")',
    'continueTask.setOnClickListener(v -> continueLastTask())',
    'private void continueLastTask()',
    'if (!autoInspect.isChecked() || !includeProject.isChecked())',
    'Nenhuma tarefa anterior nesta sessão.',
    'NÃO repita operações cuja gravação ficou inconclusiva.',
    'String toolReceipt = "[Resultado real do editor] " + evidence;',
    '"assistant".equals(history.get(history.size() - 1)[0])',
    'previous[1] = previous[1] + "\\n\\n" + toolReceipt;',
]:
    assert required in java, required
assert 'prompt.getText().toString()' not in java[java.index('private void recordEditorOutcome('):java.index('private void clearProjectMemory()')]

# The iterative task planner cannot become a second writer or silently
# bypass the existing per-write human confirmation + read-back pipeline.
for required in [
    'private static final String TASK_PLAN_PROTOCOL',
    '"propose_task_plan".equals(action)',
    'private static TaskPlan validateTaskPlan(JSONObject data, int reads,',
    'if (reads < 1) throw new IOException("O plano exige inspeção MCP real prévia")',
    'items.length() < 2 || items.length() > 5',
    '"SCRIPT".equals(step.kind) && allowScripts',
    '"PART".equals(step.kind) && allowParts',
    '"GUI".equals(step.kind) && (allowLabels || allowScreens)',
    '"ITEM".equals(step.kind) && allowItems',
    '"LIGHTING".equals(step.kind) && allowLighting',
    'private static boolean actionMatchesPlannedKind(String action, String kind)',
    'if (executingPlanStep && action.startsWith("propose_")',
    'private static boolean planStepMatches(String step, String kind)',
    'private void approvePendingTaskPlan()',
    'private void executeNextPlanStep()',
    'private void clearActivePlan()',
    'nextPlanStep = button("Executar próxima etapa • usa API")',
    'togglePlan = button("Pausar plano")',
    'cancelPlan = button("Cancelar plano")',
    'planStepOutstanding = true;',
    'planPaused = true;',
    'if (verified && planStepMatches(step.kind, kind))',
    'activeTaskPlan.completed++;',
    'pendingTaskPlan != null',
    'renderPlanState();',
    'A aprovação NÃO executa nenhuma etapa.',
    'requer uma nova inspeção do mapa',
]:
    assert required in java, required
assert java.count('callMcp(paired, "multi_edit", scriptEditArguments(edit))') == 1
assert java.count('callMcp(paired, "execute_luau", args)') == 1
assert 'callMcp(paired, action' not in java
assert 'MAX_AGENT_READS = 3' in java
assert 'MAX_AGENT_CALLS = MAX_AGENT_READS + 1' in java
assert 'private boolean planPaused, planStepOutstanding, submittingPlanStep;' in java
assert 'if (planStepOutstanding && !submittingPlanStep)' in java
assert 'submittingPlanStep = true;' in java
assert 'submittingPlanStep = false;' in java
assert 'if (executingPlanStep)\n                    throw new IOException("A etapa atual não pode criar outro plano")' in java
assert 'nenhum objeto será desfeito automaticamente.' in java

# Encrypted checkpoints only contain a bounded plan and verified progress.
# Recovering them is an explicit, non-network operation and never grants MCP
# permissions or authorization to execute a pending write.
for required in [
    'MAX_PLAN_CHECKPOINT_BYTES = 4600',
    'private String projectPlanService() throws Exception',
    'return projectMemoryService() + ".task-plan-checkpoint.v1";',
    'private static JSONObject encodePlanCheckpoint(TaskPlan plan, String title)',
    'private static TaskPlan decodePlanCheckpoint(JSONObject data, String title)',
    'private static TaskPlan parseTaskPlanShape(JSONObject data)',
    'data.optInt("version", -1) != 1',
    '!title.equals(data.optString("project", ""))',
    'completed < 0 || completed > plan.steps.size()',
    'plain.length > MAX_PLAN_CHECKPOINT_BYTES',
    'StudioServices.credential(this, "write", projectPlanService(), value, "")',
    'private void saveCurrentPlanCheckpoint()',
    'private void restoreSavedPlan()',
    'private void eraseSavedPlan()',
    'restoring', # This tag is replaced by a real UI-recovery assertion below.
    'persistPlanCheckpoints.setChecked(false)',
    'restorePlanCheckpoint = button("Revisar e recuperar checkpoint salvo")',
    'erasePlanCheckpoint = button("Apagar checkpoint salvo")',
    'saveCurrentPlanCheckpoint();',
    'pausePlanAfterFailedWrite();',
]:
    if required == 'restoring':
        required = 'setPositiveButton("Restaurar pausado",'
    assert required in java, required
assert java.count('pausePlanAfterFailedWrite();') >= 12
assert java.index('activeTaskPlan.completed++;') < java.index(
    'saveCurrentPlanCheckpoint();', java.index('activeTaskPlan.completed++;'))
restorer = java[java.index('private void restoreSavedPlan()'):java.index('private void eraseSavedPlan()')]
assert 'planPaused = true;' in restorer
assert 'planStepOutstanding = false;' in restorer
assert 'history.clear();' in restorer
assert 'callMcp(' not in restorer and 'requestModelWithFreeFallback(' not in restorer
assert 'Nenhum checkpoint criptografado salvo' in restorer


# Toolbox pre-import review is purely local. It never imports a model,
# executes Luau, forwards source to an AI provider or silently labels safe.
for required in [
    'TOOLBOX_XML_PICK_REQUEST = 7604',
    'MAX_TOOLBOX_XML_BYTES = 2 * 1024 * 1024',
    'MAX_TOOLBOX_XML_ITEMS = 1200',
    'MAX_TOOLBOX_XML_SCRIPTS = 100',
    'private static int sourceRiskMask(String source)',
    'private static String analyzeToolboxRbxmx(InputStream input)',
    'Xml.newPullParser()',
    'private void selectToolboxFile()',
    'Intent.ACTION_OPEN_DOCUMENT',
    'startActivityForResult(choose, TOOLBOX_XML_PICK_REQUEST)',
    'TOOLBOX_XML_PICK_REQUEST',
    'getContentResolver().openInputStream(selected)',
    'Nenhuma conclusão de segurança foi obtida.',
    'Selecionar e analisar modelo .rbxmx',
    'não prova segurança',
    'SEM GARANTIA DE SEGURANÇA.',
    'código ofuscado e dependências externas podem escapar',
    'Não executa nada nem envia conteúdo à API de IA.',
]:
    assert required in java, required
# Scope the pre-import scanner separately from the user-authorized Play
# controller: they share an Activity, but not permissions or tool paths.
scanner = java[java.index('private static int sourceRiskMask('):
               java.index('    /** Accept only an exact shell receipt')]
assert 'callMcp(' not in scanner
assert 'requestModelWithFreeFallback(' not in scanner
assert 'multi_edit' not in scanner
assert 'execute_luau' not in scanner
assert 'insert_asset' not in scanner
assert 'ByteArrayOutputStream' in scanner
assert 'sourceRiskMask(item.source)' in scanner
assert 'sourceChars > MAX_TOOLBOX_XML_BYTES' in scanner
assert 'lowered.contains("<!doctype")' in scanner
assert 'lowered.contains("<!entity")' in scanner


# Auditing already-inserted Models never creates or modifies objects. Unlike
# the general AI agent, it needs no API Key; a separate consent grants only
# local MCP reads, and report sharing is an independent, off-by-default opt-in.
for required in [
    'MAX_MODEL_AUDIT_TREES = 12',
    'MAX_MODEL_AUDIT_SCRIPTS = 16',
    'MAX_MODEL_AUDIT_PAGES = 2',
    'private static String checkedModelAuditPath(String path)',
    'private static AuditScriptPage parseMcpScriptPage(String raw, int startLine)',
    'private static JSONArray parseMcpModelTree(String raw, String expected)',
    'private String auditModelInEditor(String paired, String target, long epoch)',
    'private void startInsertedModelAudit()',
    'private void ensureModelAuditCurrent(long epoch)',
    'readOnlyMcp(paired, "inspect_instance"',
    'readOnlyMcp(paired, "search_game_tree"',
    'readOnlyMcp(paired, "script_read"',
    '!target.equals(root.optString("path", ""))',
    'children.optInt("totalDescendants", -1)',
    'if (nodes.size() != totalDescendants + 1)',
    'raw.startsWith("Note: Output limited")',
    'if (incomplete) break;',
    'Set<String> sameBatch = new HashSet<>();',
    'if (source.length() + parsed.source.length() > 24000)',
    'boolean complete = !incomplete && unreadable == 0',
    'Não é possível aprovar a segurança do modelo.',
    'checkedModelAuditPath(modelAuditPath.getText().toString())',
    'allowLocalModelAudit.setChecked(false)',
    'shareModelAudit.setChecked(false)',
    'localModelAuditSummary = ""',
    'shareModelAudit != null && shareModelAudit.isChecked()',
    'if (text.isEmpty() && "script_read".equals(tool)) return "";',
]:
    assert required in java, required
# Combine only the local audit implementation and its entry point. The
# separately authorized Play smoke test is intentionally not part of auditing.
auditor = (java[java.index('private String auditModelInEditor('):
                java.index('    /** Accept only an exact shell receipt')] +
           java[java.index('private void startInsertedModelAudit()'):
                java.index('    /** Only the exact native MCP status fields')])
for forbidden in ['executeApproved3dCode(', 'callMcp(', 'multi_edit',
                  'insert_asset', 'requestModelWithFreeFallback(',
                  'StudioServices.credential(', 'save_place',
                  'getSharedPreferences(']:
    assert forbidden not in auditor, forbidden
assert 'auditingInsertedModel = true;' in auditor
assert 'shareModelAudit.setChecked(false);' in auditor
assert 'allowLocalModelAudit.isChecked()' in java
assert 'includeProject.isChecked() &&\n                shareModelAudit != null' in java
assert 'auditInsertedModel.setOnClickListener(v -> startInsertedModelAudit());' in java
assert java.count('auditInsertedModel.setOnClickListener(') == 1

# Dependencies are inspected locally from the bounded code already read by
# the Model audit; external assets are identified, never fetched or executed.
for required in [
    'private static final class ScriptDependencies',
    'private static ScriptDependencies scanScriptDependencies(String source)',
    'private static String localModulePath(String scriptPath, String expression,',
    'code = source.length() > 24000 ? source.substring(0, 24000) : source',
    'if (++count > 40)',
    'if (target.size() >= 12)',
    'externalAssets.add',
]:
    # The add is performed through a bounded helper, not through direct add.
    if required == 'externalAssets.add':
        required = 'addBoundedDependency(result.externalAssets, expr, result);'
    assert required in java, required
for required in [
    'ScriptDependencies deps = scanScriptDependencies(source.toString());',
    'String module = localModulePath(entry.getKey(), expression, target);',
    '"ModuleScript".equals(nodes.get(module))',
    'resolvedModules++;',
    'unknownModules++;',
    'dynamicRequires += deps.dynamicRequires;',
    'boolean dependencyCoverageComplete = unknownModules == 0',
    'FONTES LOCAIS LIDAS, MAS DEPENDÊNCIAS SEM VERIFICAÇÃO',
    'INCOMPLETO. Não é possível aprovar a segurança do modelo.',
    'Nenhum asset importado, nenhum Luau executado e nenhum código enviado à IA.',
]:
    assert required in java, required
deps = java[java.index('private static final class ScriptDependencies'):
            java.index('private static final class ToolboxXmlItem')]
for forbidden in ['callMcp(', 'readOnlyMcp(', 'requestModelWithFreeFallback(',
                  'StudioServices.credential(', 'executeApproved3dCode(']:
    assert forbidden not in deps, forbidden


# Lighting is a narrowly bounded real editor write, not generic model Luau.
# Android uses the private typed writer with an MCP reinspection and read-back.
for required in [
    'private static final String LIGHTING_PROTOCOL',
    'private static final class LightingProposal',
    'private static LightingProposal validateLightingProposal(',
    'private static JSONObject lightingEditArguments(LightingProposal change)',
    'private static boolean lightingMatches(JSONObject props, LightingProposal change)',
    'private void confirmPendingLighting()',
    'private void applyApprovedLighting(LightingProposal change)',
    'proposeLighting.setChecked(false)',
    'proposeLighting.setOnCheckedChangeListener(',
    'proposeLighting.isChecked()',
    'LIGHTING".equals(kind)',
    'case "LIGHTING": return "UPDATE_LIGHTING".equals(kind);',
    '"propose_lighting_update".equals(action)',
    'new JSONObject().put("path", "Lighting")',
    'checkedEditReceipt(',
    '"UPDATED_LIGHTING:Lighting"',
    'recordEditorOutcome("UPDATE_LIGHTING", "Lighting", result);',
    'pendingLighting = includeProject.isChecked()',
]:
    assert required in java, required
source = java[java.index('private static double inspectedLightingNumber('):
              java.index('private static String fixedPartUpdateLuau(')]
assert 'callMcp(paired, "android_update_lighting", lightingEditArguments(change))' in source
assert 'readOnlyMcp(paired,' in source
assert 'return lightingMatches' not in source or 'lightingMatches(' in source
assert 'model.opt(' in source
assert 'if (!Double.isFinite(value)' in source
assert 'applyApprovedLighting(change))' in source
assert 'callMcp(paired, action' not in source


# Native MCP Part style change is typed and individually approved: the model
# never provides executable Luau or arbitrary property names to the editor.
for required in [
    'private static final String PART_STYLE_PROTOCOL',
    'private static final class PartStyleProposal',
    'private static PartStyleProposal validatePartStyleProposal(',
    'private static boolean allowedPartMaterial(String value)',
    'private static String inspectedPartMaterial(JSONObject props)',
    'private static double inspectedPartTransparency(JSONObject props)',
    'private static boolean inspectedPartBoolean(JSONObject props, String key)',
    'private static Boolean optionalPartBoolean(JSONObject input, String key)',
    'private static boolean partStyleMatches(JSONObject props, PartStyleProposal style)',
    'private static String fixedPartStyleLuau(PartStyleProposal style)',
    'private void confirmPendingPartStyle()',
    'private void applyApprovedPartStyle(PartStyleProposal style)',
    'propose_part_style_update',
    'proposeParts.isChecked()',
    'pendingPartStyle = includeProject.isChecked()',
    'case "PART": return "CREATE_PART".equals(kind)',
    '"UPDATE_PART_STYLE".equals(kind)',
    'UPDATED_STYLE:',
    'STALE_PART_STYLE',
    'recordEditorOutcome("UPDATE_PART_STYLE", style.path, confirmed);',
    'pendingPartStyle = null',
]:
    assert required in java, required
style_section = java[java.index('private static boolean allowedPartMaterial('):
                     java.index('private void confirmPendingPartChange()')]
# Nested Part edits are limited to a direct Workspace Model and retain user approval.
assert 'private static boolean allowedStylePath(String path)' in java
assert 'private static boolean allowedStyleClassName(String value)' in java
assert 'private static JSONObject verifiedStyleTarget(String path, String text)' in java
assert 'result.has("matches")' in java
assert 'styleIdentityMatches(freshTarget, style)' in java
assert 'styleIdentityMatches(afterTarget, style)' in java
assert 'p.ClassName ~= ' in style_section
assert 'inspectedTarget.getString("className")' in style_section
assert 'inspectedTarget.optString("uniqueId", "")' in style_section
assert 'allowedStyleClassName(style.targetClass)' in style_section
assert 'allowedStylePath(path)' in style_section
assert 'parent:IsA(\\\"Model\\\")' in style_section
assert 'p.Parent ~= parent' in style_section
assert 'verifiedEditorObject(paired, parentPath, "Model")' in style_section
assert 'path.substring(path.lastIndexOf(\'.\') + 1)' in java
assert 'executeApproved3dCode(paired, fixedPartStyleLuau(style))' in style_section
assert 'readOnlyMcp(paired,' in style_section
assert 'checkedEditReceipt(' in style_section
assert 'inspectedPartBoolean(before, "Anchored")' in style_section
assert 'inspectedPartBoolean(before, "CanCollide")' in style_section
assert 'fixedPartStyleLuau(style)' in style_section
assert 'return "STALE_PART_STYLE"' in style_section or 'STALE_PART_STYLE' in style_section
assert 'callMcp(paired, action' not in style_section
assert 'modelSuppliedLuau' not in style_section

assert 'id("mobile-ai-providers"' not in native_patch  # action uses compact_action
assert '"mobile-ai-providers"' in native_patch
assert 'open_ai_hub(&name, &pairing, &self.path.to_string_lossy())' in native_patch
assert 'pub fn open_ai_hub(project: &str, pairing: &str, map_identity: &str)' in gpui_patch


# Phase 14: local Play status and Output evidence, never simulated or sent to an AI.
for required in [
    'private static String checkedLocalPlayMode(String state)',
    'private static String buildLocalPlayReport(String state, String output)',
    'private void inspectLocalPlayOutput()',
    'allowLocalPlayProbe.setChecked(false)',
    'if (allowLocalPlayProbe == null || !allowLocalPlayProbe.isChecked())',
    'readOnlyMcp(paired, "get_studio_state", new JSONObject())',
    'readOnlyMcp(paired, "get_console_output", new JSONObject())',
    'playProbeResult.setText(result)',
    'Nenhum dado enviado à API de IA.',
    'probingLocalPlay = false',
    'if (inspectLocalPlay != null) inspectLocalPlay.setEnabled(!waiting)',
]:
    assert required in java, required
play_section = java[java.index('private static String checkedLocalPlayMode('):
                    java.index('private void selectToolboxFile()')]
for forbidden in ['executeApproved3dCode(', 'requestModelWithFreeFallback(',
                  'multi_edit', 'start_stop_play', 'publish_animation']:
    assert forbidden not in play_section, forbidden
assert play_section.count('readOnlyMcp(paired, "get_studio_state"') == 1
assert play_section.count('readOnlyMcp(paired, "get_console_output"') == 1


# Phase 15: a single opt-in *local* Rook smoke test; no AI-controlled Play.
smoke_section = java[java.index('private static String checkedLocalPlayReceipt('):
                     java.index('private void startInsertedModelAudit()')]
for required in [
    'private static String buildPlaySmokeSummary(',
    'private void confirmLocalPlaySmoke()',
    'private void runLocalPlaySmoke()',
    'private static String boundedLocalActivateLuau(String playerName, String toolPath)',
    'private boolean confirmProgrammaticActivated(String paired, String playerName,',
    'found.Activated:Connect(function()',
    'found:Activate()',
    'found:Deactivate()',
    'activatedListener:Disconnect()',
    '"ACTIVATED_EVENT".equals(receipt == null ? "" : receipt.trim())',
    'activateRequested = checkLocalToolActivate != null &&',
    'activatedEvent = confirmProgrammaticActivated(paired, scene.playerName,',
    '(!activateRequested || activatedEvent)',
    'checkLocalToolActivate.isChecked()',
    'NÃO equivale a um toque',
    'Evento real Tool.Activated no Client:',
    'private static String boundedLocalEquipLuau(String playerName, String toolPath)',
    'private String confirmLiveEquippedTool(String paired, String playerName,',
    'private static boolean readBackEquippedTool(String raw, String playerName,',
    'private boolean verifyLocalEquipParent(String paired, String playerName,',
    'String receipt = confirmLiveEquippedTool(paired, scene.playerName,',
    'equipReadBack = verifyLocalEquipParent(paired, scene.playerName,',
    'found.Equipped:Connect(function()',
    'equippedListener:Disconnect()',
    'equippedObserved = true',
    '"EQUIPPED_EVENT".equals(receipt)',
    '(!equipRequested || (equipReceipt && equipReadBack && equippedEvent))',
    'Evento real Tool.Equipped no Client:',
    'allowLocalPlaySmoke.isChecked()',
    'checkLocalToolEquip.isChecked()',
    '"Workspace." + playerName',
    r'return equippedObserved and \"EQUIPPED_EVENT\" or \"EQUIPPED_NO_EVENT\"',
    '"Client").put("path", root)',
    '"EquipTool, se solicitado, altera apenas a sessão Play local',
    '(!equipRequested || (equipReceipt && equipReadBack && equippedEvent))',
    'private LiveToolGuiAudit inspectLiveToolGui(String paired, String playerName)',
    'private static Boolean strictMcpFlag(JSONObject node, String property)',
    'private static JSONObject checkedLiveInstance(String raw, String path, String className)',
    'private static JSONArray strictLiveTree(',
    'private static String[] selectToolGuiCandidates(',
    'private static boolean hasDirectToolHandle(JSONArray backpack, String toolPath)',
    'checkLocalToolGui != null &&',
    'toolGui = inspectLiveToolGui(paired, scene.playerName)',
    '.put("datamodel_type", "Client").put("path", backpackRoot)',
    '.put("datamodel_type", "Client").put("path", guiRoot)',
    '.put("max_depth", 2).put("head_limit", 48)',
    '.put("max_depth", 4).put("head_limit", 64)',
    'toolGui != null && toolGui.complete()',

    'private static LocalPlayerScenario evaluateLocalPlayerScenario(',
    'private LocalPlayerScenario inspectLocalPlayerGameplay(String paired)',
    'checkLocalGameplayScene != null &&',
    'scene = inspectLocalPlayerGameplay(paired)',
    '.put("datamodel_type", "Client").put("path", "Players")',
    '.put("datamodel_type", "Client").put("path", "Workspace")',
    '.put("instance_type", "Humanoid")',
    '.put("max_depth", 2).put("head_limit", 48)',
    '.put("max_depth", 3).put("head_limit", 24)',
    'path + ".Backpack"',
    'path + ".PlayerGui"',
    '"Workspace." + name + ".Humanoid"',
    '(scene != null && scene.complete())',
    'private boolean inspectBoundedPlayRoot(String paired, String side, String path)',
    'serverWorkspace = inspectBoundedPlayRoot(paired, "Server", "Workspace")',
    'clientPlayers = inspectBoundedPlayRoot(paired, "Client", "Players")',
    'new JSONArray(raw)',
    'tree.length() > 10',
    'serverWorkspace && clientPlayers &&',
    'allowLocalPlaySmoke == null || !allowLocalPlaySmoke.isChecked()',
    'new android.app.AlertDialog.Builder(this)',
    '.setPositiveButton("Autorizar iniciar e parar Play"',
    '"EDIT".equals(checkedLocalPlayMode(before))',
    'checkedLocalPlayReceipt(callMcp(paired, "start_stop_play"',
    'new JSONObject().put("is_start", true)',
    'new JSONObject().put("is_start", false)',
    '"PLAY_LOCAL_CONFIRMADO".equals(checkedLocalPlayMode(playState))',
    '"get_console_output"',
    'finally {',
    '"EDIT".equals(checkedLocalPlayMode(finalState))',
    'NÃO CONFIRMADO',
]:
    assert required in smoke_section, required
assert smoke_section.count('callMcp(paired, "start_stop_play"') == 2
assert smoke_section.count('callMcp(paired, "search_game_tree"') == 6
assert smoke_section.count('callMcp(paired, "inspect_instance"') == 3
assert smoke_section.count('callMcp(paired, "execute_luau"') == 2
assert 'new JSONObject()\n                .put("datamodel_type", "Client").put("code", luau)' in smoke_section
assert '"EQUIPPED_EVENT".equals(receipt)' in smoke_section
assert '"EQUIPPED_NO_EVENT".equals(receipt)' in smoke_section
assert 'new JSONObject().put("is_start", false)' in smoke_section
assert smoke_section.count('new JSONObject().put("is_start", true)') == 1
assert smoke_section.count('new JSONObject().put("is_start", false)') == 1
for forbidden in ['requestModelWithFreeFallback(', 'executeApproved3dCode(',
                  'multi_edit', 'save_place', 'publish_animation', 'user_mouse_input']:
    assert forbidden not in smoke_section, forbidden
assert 'allowLocalPlaySmoke.setChecked(false)' in java
assert 'checkLocalGameplayScene.setChecked(false)' in java
assert 'checkLocalToolGui.setChecked(false)' in java
assert 'checkLocalToolEquip.setChecked(false)' in java
assert 'checkLocalToolActivate.setChecked(false)' in java
assert 'if (checkLocalToolActivate != null) checkLocalToolActivate.setEnabled(!waiting)' in java
assert 'if (checkLocalToolEquip != null) checkLocalToolEquip.setEnabled(!waiting)' in java
assert 'if (checkLocalToolGui != null) checkLocalToolGui.setEnabled(!waiting)' in java
assert 'if (checkLocalGameplayScene != null) checkLocalGameplayScene.setEnabled(!waiting)' in java
assert 'runningLocalPlaySmoke = false' in java
assert 'if (runLocalPlaySmoke != null) runLocalPlaySmoke.setEnabled(!waiting)' in java
assert 'args.optBoolean("is_start", false)) ||' in java
assert '"publish_animation".equals(tool)) ? 210000' in java

print("RBX Studio AI contracts: approved fixed close button, ScreenGui, Parts and script tools passed (static Java; device tests pending).")
