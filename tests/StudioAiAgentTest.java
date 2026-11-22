package com.rbxnative.androidport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Unit contracts for orchestration and cancellation. Real DOM tests run separately. */
public final class StudioAiAgentTest {
    static final class Session implements StudioAiAgent.Host {
        final List<String> actions = new ArrayList<>();
        final List<String> calls = new ArrayList<>();
        int index, applied, saved, previews;
        boolean loseWriteReply, rejectFirst, approved, diagnosticErrors;
        boolean blankScript;
        String existingSource="";
        String goal,checkpoint="";
        final java.util.Set<String> missingPaths=new java.util.HashSet<>();
        public String loadTask(){return checkpoint;}
        public void saveTask(String data){checkpoint=data;}
        Session(String... sequence) {
            for (String action: sequence) actions.add(action);
        }
        public String model(List<String[]> messages, int budget) throws Exception {
            if (!messages.get(0)[1].startsWith(goal)) throw new AssertionError("User request was replaced");
            if (index >= actions.size()) throw new AssertionError("Unexpected extra model request");
            return actions.get(index++);
        }
        public String call(String tool, JSONObject args) throws Exception {
            calls.add(tool);
            if(!existingSource.isEmpty()&&"search_game_tree".equals(tool))return "[{\"fullPath\":\"StarterGui.Existing.Controller\",\"className\":\"LocalScript\"}]";
            if(!existingSource.isEmpty()&&"script_read".equals(tool))return existingSource;
            if(blankScript&&"script_read".equals(tool))throw new StudioAiAgent.Refusal("Script missing");
            if(blankScript&&"inspect_instance".equals(tool))return "{\"className\":\"LocalScript\",\"properties\":{}}";
            if("inspect_instance".equals(tool)&&missingPaths.contains(args.optString("path")))throw new IOException("Instance not found");
            if("inspect_instance".equals(tool)&&"StarterPlayer.StarterPlayerScripts".equals(args.optString("path")))
                return "{\"className\":\"StarterPlayerScripts\",\"name\":\"StarterPlayerScripts\"}";
            if (StudioAiProjectEdit.CONTEXT.equals(tool)) return "{\"revision\":\"1:2\",\"roots\":[]}";
            if (StudioAiProjectEdit.PREVIEW.equals(tool)) {
                previews++;
                if (rejectFirst && previews == 1) throw new StudioAiAgent.Refusal("Unknown property, inspect schema");
                return "{\"revision\":\"1:2\",\"review\":false,\"operations\":[\"create Workspace.Generated\"]}";
            }
            if (StudioAiProjectEdit.APPLY.equals(tool)) {
                applied++;
                if (loseWriteReply) throw new IOException("Socket closed after write");
                return "{\"applied\":true,\"changed\":true,\"operations\":[\"create Workspace.Generated\"]}";
            }
            if (StudioAiProjectEdit.SAVE.equals(tool)) { saved++; return "{\"saved\":true}"; }
            if (StudioAiProjectEdit.DIAGNOSTICS.equals(tool)) return diagnosticErrors ? "{\"errors\":[{\"path\":\"Test\",\"error\":\"Controlled syntax error\"}]}" : "{\"errors\":[]}";
            return "{}";
        }
        public boolean approve(String category, String details) { return approved; }
        public void check() {}
        public void progress(String message) {}
        public void writing(boolean active) {}
        public void capture(List<String[]> messages) { throw new AssertionError("Unexpected image"); }
    }

    private static final String READ = "{\"action\":\"search_game_tree\",\"path\":\"Workspace\"}";
    private static final String EDIT = "{\"action\":\"project_edit\",\"operations\":[{\"op\":\"create\",\"parent\":\"Workspace\",\"class\":\"Part\",\"name\":\"Generated\"}]}";
    private static final String DONE = "{\"action\":\"finish\",\"answer\":\"Edit completed; Play not tested\"}";

    static String execute(Session host, String goal, StudioAiAgent.Run run) throws Exception {
        host.goal = goal;
        List<String[]> messages = new ArrayList<>();
        messages.add(new String[]{"user",goal});
        return StudioAiAgent.run(host,run,messages);
    }
    static void yes(boolean value) { if (!value) throw new AssertionError(); }

    static void providerProtocol(int provider) throws Exception {
        StudioAiToolProtocol wire = new StudioAiToolProtocol();
        JSONObject args = new JSONObject();
        JSONObject response;
        if (provider == 0) response = new JSONObject().put("output",new JSONArray().put(new JSONObject()
                .put("type","function_call").put("call_id","c1").put("name","get_studio_state").put("arguments",args.toString())));
        else if (provider == 1) response = new JSONObject().put("candidates",new JSONArray().put(new JSONObject()
                .put("content",new JSONObject().put("role","model").put("parts",new JSONArray().put(new JSONObject()
                        .put("thoughtSignature","opaque-signature").put("functionCall",new JSONObject()
                                .put("name","get_studio_state").put("args",args)))))));
        else if (provider == 2) response = new JSONObject().put("content",new JSONArray().put(new JSONObject()
                .put("type","tool_use").put("id","c1").put("name","get_studio_state").put("input",args)));
        else response = new JSONObject().put("choices",new JSONArray().put(new JSONObject()
                .put("message",new JSONObject().put("role","assistant").put("tool_calls",new JSONArray().put(new JSONObject()
                        .put("id","c1").put("type","function").put("function",new JSONObject()
                                .put("name","get_studio_state").put("arguments",args.toString())))))));
        yes("get_studio_state".equals(new JSONObject(wire.extract(provider,response,1)).getString("action")));
        JSONArray turns = new JSONArray();
        for (int i = 0; i < 3; i++) {
            JSONObject msg = new JSONObject().put("role",i == 1 ? (provider == 1 ? "model" : "assistant") : "user");
            if (provider == 1) msg.put("parts",new JSONArray().put(new JSONObject().put("text","real result")));
            else msg.put("content","real result");
            turns.put(msg);
        }
        JSONObject body = new JSONObject().put(provider == 0 ? "input" : provider == 1 ? "contents" : "messages",turns);
        StudioAiConversation.configure(provider,body);
        wire.configure(provider,body);
        turns=body.getJSONArray(provider==0?"input":provider==1?"contents":"messages");
        yes(body.getJSONArray("tools").length() == (provider==1?1:StudioDeveloperEngine.ACTIONS.length));
        JSONObject observed = turns.getJSONObject(provider==3?3:2);
        if (provider == 0) yes("function_call_output".equals(observed.getString("type")));
        else if (provider == 1) {
            yes(observed.getJSONArray("parts").getJSONObject(0).has("functionResponse"));
            yes("opaque-signature".equals(turns.getJSONObject(1).getJSONArray("parts").getJSONObject(0).getString("thoughtSignature")));
        } else if (provider == 2) yes("tool_result".equals(observed.getJSONArray("content").getJSONObject(0).getString("type")));
        else yes("tool".equals(observed.getString("role")));
    }

    public static void main(String[] args) throws Exception {
        String keyboardOnly="local UIS=game:GetService('UserInputService')\nUIS.InputBegan:Connect(function(input) if input.KeyCode == Enum.KeyCode.F then collect() end end)";
        yes(StudioAiScriptPolicy.mobileInputSignals(keyboardOnly).length()==1);
        yes(StudioAiScriptPolicy.mobileInputSignals(keyboardOnly+"\n-- button.Activated:Connect(collect)").length()==1);
        yes(StudioAiScriptPolicy.mobileInputSignals(keyboardOnly+"\nbutton.Activated:Connect(collect)").length()==0);
        yes(StudioAiScriptPolicy.mobileInputSignals(keyboardOnly+"\nCAS:BindAction('Collect', collect, true, Enum.KeyCode.F)").length()==0);
        yes(StudioAiScriptPolicy.mobileInputSignals("local text='UIS.InputBegan Enum.KeyCode.F' -- no input").length()==0);
        Session mobileGap=new Session(EDIT,DONE,DONE,DONE,DONE,DONE,DONE,DONE);mobileGap.existingSource=keyboardOnly;
        StudioAiAgent.Run mobileGapRun=new StudioAiAgent.Run(8,true,false,false,false,"sistema mobile");
        String blocked=execute(mobileGap,"sistema mobile",mobileGapRun);yes(!mobileGapRun.completed&&blocked.contains("limite"));
        Session blank=new Session();blank.blankScript=true;yes(StudioAiAgent.readScript(blank,new JSONObject().put("target_file","StarterGui.Empty")).isEmpty());
        JSONObject sourceAlias=new JSONObject("{\"operations\":[{\"op\":\"set\",\"path\":\"ServerScriptService.Test\",\"properties\":{\"source\":\"print(1)\"}}]}");
        JSONArray normalized=StudioAiProjectEdit.operations(sourceAlias);yes(normalized.getJSONObject(0).getJSONObject("properties").getString("Source").equals("print(1)"));yes(sourceAlias.getJSONArray("operations").getJSONObject(0).getJSONObject("properties").has("source"));
        JSONArray scaffold=StudioAiProjectEdit.operations(new JSONObject("{\"operations\":[{\"op\":\"create\",\"parent\":\"ServerScriptService\",\"name\":\"Empty\",\"class\":\"Script\"}]}"));yes(scaffold.getJSONObject(0).has("source"));
        StudioAiAgent.Run scaffoldRun=new StudioAiAgent.Run(8,true,false,false,false,"scaffold");scaffoldRun.trackScripts(scaffold);yes(scaffoldRun.unfinishedScripts.contains("ServerScriptService.Empty"));scaffoldRun.trackScripts(normalized);
        boolean conflict=false;try{StudioAiProjectEdit.operations(new JSONObject("{\"operations\":[{\"op\":\"set\",\"path\":\"S\",\"properties\":{\"source\":\"a\",\"Source\":\"b\"}}]}"));}catch(StudioAiAgent.Refusal expected){conflict=true;}yes(conflict);
        yes(new JSONObject(StudioAiProjectEdit.toolSchema("{\"class\":\"Script\",\"properties\":{}}" )).getJSONObject("properties").has("Source"));
        yes(!new JSONObject(StudioAiProjectEdit.toolSchema("{\"class\":\"Part\",\"properties\":{}}" )).getJSONObject("properties").has("Source"));
        Session references=new Session();references.missingPaths.add("StarterGui.Shop.Panel.Missing");
        String source="     1→local gui = script.Parent\n     2→local frame = gui:WaitForChild(\"Panel\") :: Frame\n     3→local button = frame:WaitForChild(\"Missing\") :: TextButton\n     4→local playerGui = player:WaitForChild(\"PlayerGui\")\n     5→-- local ignored = gui:WaitForChild(\"Comment\")";
        JSONArray unresolved=StudioAiScriptPolicy.missingLocalReferences("StarterGui.Shop.Controller",source,references);
        yes(unresolved.length()==1&&unresolved.getString(0).contains("StarterGui.Shop.Panel.Missing"));
        references.missingPaths.clear();yes(StudioAiScriptPolicy.missingLocalReferences("StarterGui.Shop.Controller",source,references).length()==0);
        yes(StudioAiToolProtocol.needsPlainFallback(1,new JSONObject("{\"candidates\":[{\"finishReason\":\"MALFORMED_FUNCTION_CALL\"}]}")));
        yes(!StudioAiToolProtocol.needsPlainFallback(1,new JSONObject("{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}")));
        StudioAiProjectEdit unchanged=StudioAiProjectEdit.checkedPreview(new JSONObject(EDIT).getJSONArray("operations"),"{\"revision\":\"1:2\",\"review\":false,\"operations\":[\"create Workspace.Generated\"]}");
        unchanged.checkedReceipt("{\"applied\":true,\"changed\":false,\"operations\":[\"create Workspace.Generated\"]}");
        yes(unchanged.appliedCount==0);StudioAiAgent.Run noMutation=new StudioAiAgent.Run(8,true,false,false,false,"no change");noMutation.record(0,"unchanged");yes(noMutation.operations==0&&noMutation.batches==0);
        // Context-first script authoring: a global tree does NOT permit an uninspected script destination.
        String weatherScript = "{\"action\":\"project_edit\",\"operations\":[{\"op\":\"create\",\"parent\":\"StarterPlayer.StarterPlayerScripts\",\"class\":\"LocalScript\",\"name\":\"Weather\",\"source\":\"print('rain')\"}]}";
        String inspectWeather = "{\"action\":\"inspect_instance\",\"path\":\"StarterPlayer.StarterPlayerScripts\"}";
        Session firstInspect = new Session(weatherScript,inspectWeather,weatherScript,DONE,DONE,DONE,DONE);
        StudioAiAgent.Run firstInspectRun=new StudioAiAgent.Run(12,true,false,false,false,"sistema de chuva");
        execute(firstInspect,"sistema de chuva",firstInspectRun);
        yes(firstInspect.applied==1 && firstInspect.previews==1 && firstInspectRun.completed);
        // A new folder created in the same batch is grounded by inspecting its existing ancestor.
        JSONArray nested = StudioAiProjectEdit.operations(new JSONObject("{\"operations\":[{\"op\":\"create\",\"parent\":\"Workspace\",\"class\":\"Folder\",\"name\":\"Storm\"},{\"op\":\"create\",\"parent\":\"Workspace.Storm\",\"class\":\"Script\",\"name\":\"Controller\",\"source\":\"print(1)\"}]}"));
        yes(StudioAiScriptPolicy.requiredScriptScopes(nested).contains("Workspace"));
        yes(StudioAiScriptPolicy.requiredScriptScopes(nested).size()==1);
        // Invalid Luau runtime properties must be rejected even though the editor's spatial
        // aliases intentionally skip generic reflection for BaseParts.
        String invalidEmitterPosition = "local e = Instance.new('ParticleEmitter')\ne.Position = Vector3.new(0,50,0)";
        String invalidEmitterParent = "local f = Instance.new('Folder')\nlocal e = Instance.new('ParticleEmitter')\ne.Parent = f";
        for (String sourceCode : new String[]{invalidEmitterPosition, invalidEmitterParent}) {
            JSONArray ops=new JSONArray().put(new JSONObject().put("op","create")
                .put("class","LocalScript").put("parent","StarterPlayer.StarterPlayerScripts")
                .put("name","BadRain").put("source",sourceCode));
            boolean refused=false;
            try { StudioAiScriptPolicy.check(ops,new java.util.HashSet<>(),new Session()); }
            catch(StudioAiAgent.Refusal expected){refused=true;}
            yes(refused);
        }
        JSONArray validEmitter=new JSONArray().put(new JSONObject().put("op","create")
            .put("class","LocalScript").put("parent","StarterPlayer.StarterPlayerScripts")
            .put("name","ValidRain").put("source",
                "local e = Instance.new('ParticleEmitter')\nlocal a = Instance.new('Attachment')\ne.Parent = a"));
        StudioAiScriptPolicy.check(validEmitter,new java.util.HashSet<>(),new Session());
        Session automatic=new Session(EDIT,EDIT,DONE,DONE);
        StudioAiAgent.Run automaticRun=new StudioAiAgent.Run(8,true,false,false,false,"duas edições sem inspeção manual");
        execute(automatic,"duas edições sem inspeção manual",automaticRun);
        yes(automaticRun.completed&&automaticRun.operations==2&&java.util.Collections.frequency(automatic.calls,"search_game_tree")==5);
        Session invalidFinish=new Session(READ,EDIT,DONE,DONE,DONE,DONE,DONE,DONE);
        invalidFinish.diagnosticErrors=true;
        String incomplete=execute(invalidFinish,"não ocultar erros",new StudioAiAgent.Run(8,true,false,false,false,"não ocultar erros"));
        yes(incomplete.contains("limite")&&!incomplete.equals("Edit completed; Play not tested"));
        for (String goal : new String[]{"chuva simples","chuva cinematográfica","chuva apenas no modelo selecionado"}) {
            Session host = new Session(READ,EDIT,DONE,DONE);
            StudioAiAgent.Run run = new StudioAiAgent.Run(16,true,false,false,false,goal);
            execute(host,goal,run);
            yes(host.index == 4 && host.applied == 1 && host.saved == 1);
            yes(run.operations == 1 && run.batches == 1 && !run.writePending && run.saved);
        }
        Session longTask = new Session(READ,EDIT,READ,EDIT,DONE,DONE);
        StudioAiAgent.Run run = new StudioAiAgent.Run(16,true,false,false,false,"coordenar serviços");
        execute(longTask,"coordenar serviços",run);
        yes(longTask.applied == 2 && run.batches == 2 && longTask.saved == 2);
        Session correction = new Session(READ,EDIT,EDIT,DONE,DONE);
        correction.rejectFirst = true;
        execute(correction,"corrigir propriedade",new StudioAiAgent.Run(16,true,false,false,false,"corrigir propriedade"));
        yes(correction.previews == 2 && correction.applied == 1);
        Session uncertain = new Session(READ,EDIT,DONE);
        uncertain.loseWriteReply = true;
        StudioAiAgent.Run paused = new StudioAiAgent.Run(16,true,false,false,false,"gravar");
        try { execute(uncertain,"gravar",paused); throw new AssertionError("Lost reply was silently retried"); }
        catch (IOException expected) { yes(paused.writePending && uncertain.applied == 1 && uncertain.index == 2); }
        Session noChanges = new Session(DONE);
        String answer = execute(noChanges,"analisar",new StudioAiAgent.Run(8,false,false,false,false,"analisar"));
        yes(answer.startsWith("Nenhuma alteração"));
        yes(!new StudioAiAgent.Run(64,true,true,true,true,"asset 123456").assetIds.isEmpty());
        yes(new StudioAiAgent.Run(1024,true,true,true,true,"limite").budget == 128);
        StudioAiAgent.Run original=new StudioAiAgent.Run(16,true,false,false,false,"Preservar objetivo e recibos");
        original.record(2,"create Workspace.RealA\ncreate Workspace.RealB");original.saved=true;
        Session resumed=new Session(DONE,DONE);resumed.checkpoint=original.snapshot();
        StudioAiAgent.Run continuation=new StudioAiAgent.Run(16,true,false,false,false,"retomar");continuation.resume=true;
        execute(resumed,"retomar",continuation);
        yes(continuation.completed&&continuation.operations==2&&resumed.applied==0&&continuation.objective.equals(original.objective));
        StudioAiAgent.Run steering=new StudioAiAgent.Run(16,true,false,false,false,"Construir");
        steering.steer("Usar azul");yes(steering.takeSteering().contains("Usar azul")&&steering.objective.contains("Usar azul"));
        StudioAiAgent.Run uncertainResume=new StudioAiAgent.Run(16,true,false,false,false,"não repetir");uncertainResume.writePending=true;
        Session noReplay=new Session(DONE);noReplay.checkpoint=uncertainResume.snapshot();
        StudioAiAgent.Run reloaded=new StudioAiAgent.Run(16,true,false,false,false,"retomar");reloaded.resume=true;
        try{execute(noReplay,"retomar",reloaded);throw new AssertionError();}catch(IOException expected){yes(noReplay.index==0&&noReplay.applied==0&&reloaded.writePending);}
        for (int provider = 0; provider < 4; provider++) providerProtocol(provider);
        System.out.println("Agent loop and four provider wire contracts passed. API generation and device execution require separate integration tests.");
    }
}
