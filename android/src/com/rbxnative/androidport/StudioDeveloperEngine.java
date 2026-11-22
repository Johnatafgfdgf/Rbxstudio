package com.rbxnative.androidport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/**
 * Developer Intelligence Engine: planning, retrieval, procedural execution, evidence and recovery.
 */
final class StudioDeveloperEngine {
  static final String[] ACTIONS = {
    "finish",
    "script_design",
    "project_edit",
    "editor_schema",
    "project_context",
    "project_select",
    "project_save",
    "search_game_tree",
    "inspect_instance",
    "script_read",
    "script_search",
    "script_grep",
    "get_studio_state",
    "get_console_output",
    "diagnostics_scan",
    "search_toolbox",
    "toolbox_import",
    "viewport_capture",
    "play_test",
    "reference_lookup",
    "documentation_get",
    "publish_animation",
    "asset_capabilities",
    "skills_list",
    "task_plan",
    "procedural_generate",
    "procedural_apply",
    "quality_check",
    "resource_search",
    "resource_inspect",
    "knowledge_search",
    "rig_inspect"
  };
  static final String GUIDE =
      "\n"
          + "script_design(placements) prepara a arquitetura de scripts a partir do Explorer: "
          + " [{path,class,mode,purpose}] com modo create ou edit; só depois gere Luau e aplique Source. "
          + " Scripts novos precisam do caminho completo, e edição usa o caminho existente. "
          + " Este planejamento é baseado em inspeção REAL, nunca num snippet fixo. "
          + "Developer Intelligence Engine: skills_list mostra geradores e schemas. task_plan"
          + " recebe steps:[{id,title,role,acceptance,depends_on:[id]}]. Registra critérios, não"
          + " finge conclusão. procedural_generate com generator e spec prepara uma prévia"
          + " parametrizada; procedural_apply com build_id inspeciona o destino e aplica usando o"
          + " validador real do Explorer. Geradores: terrain, building, scatter, vfx, ui,"
          + " animation, camera, audio. Vincule scatter ao terrain_build_id recém-aplicado. Use"
          + " knowledge_search(query) para guias locais e"
          + " resource_search(query)/resource_inspect(repository owner/name) para metadados,"
          + " licença e README públicos. Não execute código remoto. rig_inspect(path) lê nomes e"
          + " Motor6D reais; animation usa rig_path. quality_check(paths,particle_budget opcional)"
          + " lê propriedades reais, sem inventar FPS ou qualidade visual. Para modelos menores use"
          + " um objetivo por lote, consulte skills_list e use geradores em vez de enumerar"
          + " centenas de instâncias. Exemplos não substituem o pedido. Use nomes e parâmetros"
          + " apropriados ao projeto atual. asset_capabilities consulta as permissões reais da chave Roblox instalada,"
          + " com group_id opcional. Sem asset_write confirmado, use assets existentes da Toolbox; não tente publicar."
          + " Permissão de upload não dispensa autorização específica de publicação.";

  static final class Build {
    final String id, generator;
    final JSONArray operations;
    int next;
    String revision = "";

    Build(String id, String generator, JSONArray ops) {
      this.id = id;
      this.generator = generator;
      operations = ops;
    }
  }

  final Map<String, Build> builds = new LinkedHashMap<>();
  JSONArray plan = new JSONArray();
  final JSONArray quality = new JSONArray();
  private StudioDeveloperKnowledge index;

  JSONObject checkpoint()throws Exception {
    JSONArray previews=new JSONArray();int bytes=0;
    for(Build b:builds.values()) {
      String operations=b.operations.toString();bytes+=operations.length();
      if(bytes>260000)break;
      previews.put(new JSONObject().put("id",b.id).put("generator",b.generator).put("operations",b.operations).put("next",b.next).put("revision",b.revision));
    }
    return new JSONObject().put("plan",plan).put("builds",previews).put("quality",quality);
  }
  void restoreCheckpoint(JSONObject j)throws Exception {
    if(j.optJSONArray("plan")!=null)plan=j.getJSONArray("plan");
    JSONArray a=j.optJSONArray("builds");if(a==null)return;
    for(int i=0;i<Math.min(8,a.length());i++){
      JSONObject b=a.getJSONObject(i);Build build=new Build(b.getString("id"),b.getString("generator"),b.getJSONArray("operations"));
      build.next=b.getInt("next");build.revision=b.optString("revision");
      if(build.next<0||build.next>build.operations.length())throw new IOException("Cursor de construção inválido.");
      builds.put(build.id,build);
    }
  }

  StudioDeveloperKnowledge knowledge() throws Exception {
    if (index == null) index = new StudioDeveloperKnowledge();
    return index;
  }

  void restore(String data) throws Exception {
    if (data == null || data.isEmpty()) return;
    if (data.length() > 180000) throw new StudioAiAgent.Refusal("Memória técnica acima do limite.");
    JSONObject j = new JSONObject(data);
    if (j.optInt("version") != 1)
      throw new StudioAiAgent.Refusal("Versão de memória incompatível.");
    knowledge().load(j.optJSONArray("documents"));
  }

  String experience(StudioAiAgent.Run run) throws Exception {
    return new JSONObject()
        .put("version", 1)
        .put("documents", knowledge().dump())
        .put(
            "last_run",
            new JSONObject()
                .put("operations", run.operations)
                .put("batches", run.batches)
                .put("saved", run.saved)
                .put("plan", plan)
                .put("generators", new JSONArray(builds.keySet()))
                .put("scope", "Recibos e verificações estáticas; não comprova gameplay."))
        .toString();
  }

  static JSONObject skills() throws Exception {
    JSONObject all = new JSONObject();
    for (String generator : StudioProceduralCompiler.GENERATORS)
      all.put(generator, StudioProceduralCompiler.describe(generator));
    return new JSONObject()
        .put("generators", all)
        .put("max_operations_per_build", 256)
        .put("batch_size", 32)
        .put(
            "roles",
            new JSONArray(
                Arrays.asList(
                    "Game Director",
                    "World Builder",
                    "3D Architect",
                    "VFX Artist",
                    "Animator",
                    "Gameplay Engineer",
                    "UI Designer",
                    "Sound Designer",
                    "Cinematic Director",
                    "QA Engineer",
                    "Performance Engineer")))
        .put(
            "scope",
            "Papéis organizam trabalho real; não são chamadas fictícias a outros modelos. Runtime e"
                + " qualidade visual exigem evidência própria.");
  }

  String planning(JSONObject action) throws Exception {
    JSONArray steps = action.optJSONArray("steps");
    if (steps == null || steps.length() < 1 || steps.length() > 16)
      throw new StudioAiAgent.Refusal("Use 1 a 16 etapas.");
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < steps.length(); i++) {
      JSONObject s = steps.getJSONObject(i);
      String id = required(s, "id", 40);
      if (!id.matches("[A-Za-z0-9_-]+") || ids.contains(id))
        throw new StudioAiAgent.Refusal("ID de etapa inválido ou repetido.");
      required(s, "title", 160);
      required(s, "acceptance", 240);
      if (!s.has("role")) s.put("role", "Game Director");
      required(s, "role", 60);
      JSONArray deps = s.optJSONArray("depends_on");
      if (deps != null)
        for (int j = 0; j < deps.length(); j++)
          if (!ids.contains(deps.getString(j)))
            throw new StudioAiAgent.Refusal("Dependência futura, ausente ou cíclica.");
      ids.add(id);
    }
    plan = new JSONArray(steps.toString());
    return new JSONObject()
        .put("recorded", true)
        .put("completed", false)
        .put("steps", plan)
        .toString();
  }

  String generate(JSONObject action, StudioAiAgent.Run run, StudioAiAgent.Host host)
      throws Exception {
    String generator = required(action, "generator", 30);
    JSONObject spec = action.optJSONObject("spec");
    if (spec == null) throw new StudioAiAgent.Refusal("Informe spec conforme skills_list.");
    spec = StudioProceduralCompiler.normalize(generator, spec);
    if (builds.size() >= 8)
      throw new StudioAiAgent.Refusal("Limite de prévias por tarefa atingido.");
    JSONArray ground = null;
    if (spec.has("terrain_build_id")) {
      Build terrain = builds.get(required(spec, "terrain_build_id", 80));
      if (terrain == null
          || !terrain.generator.equals("terrain")
          || terrain.next != terrain.operations.length())
        throw new StudioAiAgent.Refusal("Aplique o terreno antes de vincular a vegetação.");
      String revision =
          new JSONObject(host.call(StudioAiProjectEdit.CONTEXT, new JSONObject()))
              .getString("revision");
      if (!revision.equals(terrain.revision))
        throw new StudioAiAgent.Refusal(
            "O mapa mudou após o terreno. Inspecione a superfície atual antes de reutilizar.");
      ground = terrain.operations;
    }
    JSONArray ops;
    try {
      ops = StudioProceduralCompiler.compile(generator, spec, ground);
    } catch (StudioAiAgent.Refusal invalid) {
      throw new StudioAiAgent.Refusal(
          invalid.getMessage()
              + " Campos: "
              + StudioProceduralCompiler.describe(generator).getString("inputs"));
    }
    StudioAiScriptPolicy.check(ops, run.assetIds, host);
    if (generator.equals("animation")) {
      JSONObject rig = inspectRig(host, required(spec, "rig_path", 400));
      Set<String> bones = new HashSet<>();
      JSONArray b = rig.getJSONArray("bones");
      for (int i = 0; i < b.length(); i++) bones.add(b.getString(i));
      for (int i = 0; i < ops.length(); i++) {
        JSONObject o = ops.getJSONObject(i);
        if (o.optString("class").equals("Pose") && !bones.contains(o.getString("name")))
          throw new StudioAiAgent.Refusal("Pose não corresponde ao rig: " + o.getString("name"));
      }
    }
    for (int i = 0; i < ops.length(); i++) {
      JSONObject op = ops.getJSONObject(i);
      if (!run.scripts && op.has("source"))
        throw new StudioAiAgent.Refusal("Este gerador exige autorização de edição de scripts.");
      JSONObject props = op.optJSONObject("properties");
      if (props == null) continue;
      for (String field : Arrays.asList("Texture", "SoundId", "AnimationId", "Image", "MeshId")) {
        String v = props.optString(field);
        if (v.isEmpty() || v.startsWith("rbxasset://")) continue;
        String id = v.replaceFirst("^rbxassetid://", "");
        if (!run.assetIds.contains(id))
          throw new StudioAiAgent.Refusal(
              "Asset não confirmado: " + field + ". Consulte o catálogo real.");
      }
    }
    byte[] hash =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(ops.toString().getBytes(StandardCharsets.UTF_8));
    StringBuilder hex = new StringBuilder();
    for (byte b : hash) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
    String id = "build_" + hex.substring(0, 16);
    if (!builds.containsKey(id)) builds.put(id, new Build(id, generator, ops));
    JSONArray preview = new JSONArray();
    for (int i = 0; i < Math.min(8, ops.length()); i++) {
      JSONObject o = new JSONObject(ops.getJSONObject(i).toString());
      o.remove("source");
      preview.put(o);
    }
    return new JSONObject()
        .put("build_id", id)
        .put("generator", generator)
        .put("operations", ops.length())
        .put("normalized_spec", spec)
        .put("layout_review",generator.equals("ui")&&spec.has("elements")?StudioAiVisualBuilder.review(spec):JSONObject.NULL)
        .put("preview", preview)
        .put("applied", false)
        .put("next", "Confira os parâmetros e use procedural_apply com build_id.")
        .toString();
  }

  String apply(String id, StudioAiAgent.Host host, StudioAiAgent.Run run) throws Exception {
    Build build = builds.get(id);
    if (build == null) throw new StudioAiAgent.Refusal("Prévia inexistente nesta tarefa.");
    if (build.next == build.operations.length())
      throw new StudioAiAgent.Refusal("Prévia já aplicada; inspecione o resultado.");
    if (run.writePending) throw new IOException("Gravação anterior inconclusiva; não repetir.");
    JSONObject first = build.operations.getJSONObject(0);
    host.call(
        "inspect_instance",
        new JSONObject().put("path", first.optString("parent", "Workspace.Terrain")));
    while (build.next < build.operations.length()) {
      host.check();
      JSONArray batch = new JSONArray();
      boolean terrain =
          build.operations.getJSONObject(build.next).optString("op").equals("terrain");
      for (int i = build.next;
          i < Math.min(build.next + (terrain ? 1 : 32), build.operations.length());
          i++) {
        JSONObject op = build.operations.getJSONObject(i);
        if (i > build.next && op.optString("op").equals("terrain")) break;
        batch.put(op);
      }
      try {
        StudioAiProjectEdit.operations(new JSONObject().put("operations", batch));
      } catch (IOException e) {
        throw new StudioAiAgent.Refusal(e.getMessage());
      }
      host.progress(
          "Construindo " + build.generator + " • " + build.next + "/" + build.operations.length());
      StudioAiProjectEdit edit =
          StudioAiProjectEdit.checkedPreview(
              batch,
              host.call(
                  StudioAiProjectEdit.PREVIEW,
                  new JSONObject().put("operations", batch).put("allow_scripts", run.scripts)));
      if (edit.review && !host.approve(edit.highImpact?"delete_bulk":"delete", edit.details))
        throw new StudioAiAgent.Refusal(
            "Lote recusado; mudanças anteriores permanecem registradas.");
      host.check();
      run.ensureSnapshot(host);
      run.writePending = true;
      run.pending=new JSONObject().put("tool",StudioAiProjectEdit.APPLY).put("arguments",edit.arguments(run.scripts,edit.review));
      run.persist(host,"apply_procedural_batch");
      host.writing(true);
      try {
        String receipt =
            edit.checkedReceipt(
                host.call(StudioAiProjectEdit.APPLY, edit.arguments(run.scripts, edit.review)));
        run.record(edit.appliedCount, receipt);
        run.writePending = false;
        build.next += batch.length();
        run.trackScripts(batch);run.pending=null;
        StudioAiAgent.save(host,run);
        run.persist(host,"continue_procedural_build");
      } catch (StudioAiAgent.Refusal refused) {
        run.writePending = false;
        throw refused;
      } finally {
        host.writing(false);
      }
    }
    build.revision =
        new JSONObject(host.call(StudioAiProjectEdit.CONTEXT, new JSONObject()))
            .getString("revision");
    return new JSONObject()
        .put("build_id", id)
        .put("confirmed_operations", build.next)
        .put(
            "scope",
            "Tipos, DOM e sintaxe validados pelo editor; aparência e comportamento precisam de"
                + " teste.")
        .toString();
  }

  String check(JSONObject action, StudioAiAgent.Host host) throws Exception {
    JSONArray paths = action.optJSONArray("paths");
    if (paths == null || paths.length() < 1 || paths.length() > 24)
      throw new StudioAiAgent.Refusal("Informe de 1 a 24 caminhos reais.");
    JSONArray observations = new JSONArray(), issues = new JSONArray();
    double rate = 0;
    for (int i = 0; i < paths.length(); i++) {
      String path = paths.getString(i);
      if (path.length() > 500) throw new StudioAiAgent.Refusal("Caminho longo.");
      JSONObject actual =
          new JSONObject(host.call("inspect_instance", new JSONObject().put("path", path)));
      observations.put(actual);
      JSONObject p = actual.optJSONObject("properties");
      if (p == null) continue;
      String kind = actual.optString("className");
      if (kind.equals("ParticleEmitter")
          && !"false".equalsIgnoreCase(String.valueOf(p.opt("Enabled")))) {
        double n = number(p.opt("Rate"));
        if (Double.isFinite(n)) rate += n;
      }
      if (kind.equals("Sound")
          && (p.optString("SoundId").isEmpty() || p.optString("SoundId").equals("0")))
        issues.put(path + ": SoundId não confirmado.");
      if ((kind.equals("Beam") || kind.equals("Trail"))
          && (p.isNull("Attachment0")
              || p.isNull("Attachment1")
              || !p.has("Attachment0")
              || !p.has("Attachment1"))) issues.put(path + ": attachments não confirmados.");
      if ((kind.equals("TextLabel") || kind.equals("TextButton")) && number(p.opt("TextSize")) < 14)
        issues.put(path + ": texto pequeno para celular.");
    }
    double budget =
        action.has("particle_budget") ? bounded(action, "particle_budget", 0, 2000) : 300;
    if (rate > budget)
      issues.put("Rate agregado " + rate + " excede orçamento " + budget + "; isto não é FPS.");
    JSONObject report =
        new JSONObject()
            .put("observations", observations)
            .put("issues", issues)
            .put("particle_rate_observed", rate)
            .put(
                "scope",
                "Inspeção estática somente dos objetos fornecidos; sem nota visual ou FPS.");
    quality.put(report);
    return report.toString();
  }

  static JSONObject inspectRig(StudioAiAgent.Host host, String path) throws Exception {
    String raw =
        host.call(
            "search_game_tree",
            new JSONObject().put("path", path).put("max_depth", 8).put("head_limit", 200));
    JSONArray tree;
    try {
      tree = new JSONArray(raw);
    } catch (JSONException e) {
      throw new StudioAiAgent.Refusal("Árvore do rig incompleta ou truncada.");
    }
    JSONArray bones = new JSONArray(), joints = new JSONArray();
    for (int i = 0; i < tree.length(); i++) {
      JSONObject node = tree.getJSONObject(i);
      String k = node.optString("className");
      if (Arrays.asList("Part", "MeshPart", "Bone").contains(k)) bones.put(node.getString("name"));
      if (k.equals("Motor6D")) {
        if (joints.length() >= 40) throw new StudioAiAgent.Refusal("Rig acima do orçamento.");
        joints.put(
            new JSONObject(
                host.call(
                    "inspect_instance", new JSONObject().put("path", node.getString("fullPath")))));
      }
      if (node.has("childSummary"))
        throw new StudioAiAgent.Refusal("Rig tem descendentes não visitados.");
    }
    if (bones.length() == 0 || joints.length() == 0)
      throw new StudioAiAgent.Refusal("Rig Motor6D não confirmado.");
    return new JSONObject()
        .put("path", path)
        .put("bones", bones)
        .put("joints", joints)
        .put("scope", "Estrutura real; reprodução e retargeting não verificados.");
  }

  static String required(JSONObject o, String key, int max) throws StudioAiAgent.Refusal {
    Object v = o.opt(key);
    if (!(v instanceof String)
        || ((String) v).trim().isEmpty()
        || ((String) v).length() > max
        || ((String) v).indexOf('\0') >= 0)
      throw new StudioAiAgent.Refusal("Campo inválido: " + key);
    return ((String) v).trim();
  }

  static double bounded(JSONObject o, String key, double min, double max)
      throws StudioAiAgent.Refusal {
    Object v = o.opt(key);
    if (!(v instanceof Number)) throw new StudioAiAgent.Refusal("Número necessário: " + key);
    double n = ((Number) v).doubleValue();
    if (!Double.isFinite(n) || n < min || n > max)
      throw new StudioAiAgent.Refusal("Número fora do limite: " + key);
    return n;
  }

  static double number(Object v) {
    try {
      return Double.parseDouble(String.valueOf(v));
    } catch (Exception e) {
      return Double.NaN;
    }
  }
}
