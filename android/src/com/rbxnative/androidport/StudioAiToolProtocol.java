package com.rbxnative.androidport;

import java.util.*;
import org.json.*;

/**
 * Native per-operation provider tools. The legacy studio_action envelope is accepted only at the
 * boundary.
 */
final class StudioAiToolProtocol {
  private static final String WRAPPER = "studio_action";

  private static final class Exchange {
    final int index;
    final JSONObject original;
    final String id, name;

    Exchange(int i, JSONObject o, String id, String n) {
      index = i;
      original = o;
      this.id = id;
      name = n;
    }
  }

  private final List<Exchange> exchanges = new ArrayList<>();

  static boolean needsPlainFallback(int provider,JSONObject response){
    if(provider!=1)return false;
    JSONArray candidates=response.optJSONArray("candidates");
    if(candidates==null||candidates.length()==0)return false;
    JSONObject candidate=candidates.optJSONObject(0);
    String reason=candidate==null?"":candidate.optString("finishReason");
    return "MALFORMED_FUNCTION_CALL".equals(reason)||"UNEXPECTED_TOOL_CALL".equals(reason);
  }

  private static JSONObject type(String t) throws Exception {
    return new JSONObject().put("type", t);
  }

  private static JSONObject array(JSONObject t) throws Exception {
    return type("array").put("items", t);
  }

  private static JSONObject pathType() throws Exception {
    return new JSONObject()
        .put(
            "anyOf",
            new JSONArray()
                .put(type("string"))
                .put(array(type("string")))
                .put(
                    type("object")
                        .put("properties", new JSONObject().put("instance_id", type("integer")))
                        .put("required", new JSONArray().put("instance_id"))));
  }

  static JSONObject schema() throws Exception {
    JSONObject fields = new JSONObject();
    for (String f :
        new String[] {
          "path",
          "target_file",
          "class",
          "query",
          "keywords",
          "topic",
          "url",
          "asset_id",
          "parent",
          "answer",
          "display_name",
          "generator",
          "build_id",
          "repository"
        }) fields.put(f, type("string"));
    fields.put("path", type("string"));
    for (String f : new String[] {"group_id", "start_line", "max_depth"})
      fields.put(f, type("integer"));
    fields.put("type", type("string"));
    fields.put("spec", StudioProceduralCompiler.schema());
    fields.put(
        "generator",
        type("string").put("enum", new JSONArray(StudioProceduralCompiler.GENERATORS)));
    fields.put("paths", array(type("string")));
    fields.put("particle_budget", type("number"));
    fields.put(
        "placements",
        array(type("object")
            .put("properties",
                new JSONObject()
                    .put("path", type("string"))
                    .put("class", type("string").put("enum",
                        new JSONArray(Arrays.asList("Script","LocalScript","ModuleScript"))))
                    .put("mode", type("string").put("enum",
                        new JSONArray(Arrays.asList("create","edit"))))
                    .put("purpose", type("string")))
            .put("required", new JSONArray(Arrays.asList("path","class","mode","purpose")))
            .put("additionalProperties", false)));
    fields.put(
        "steps",
        array(
            type("object")
                .put(
                    "properties",
                    new JSONObject()
                        .put("id", type("string"))
                        .put("title", type("string"))
                        .put("role", type("string"))
                        .put("acceptance", type("string"))
                        .put("depends_on", array(type("string"))))
                .put("required", new JSONArray(Arrays.asList("id", "title", "acceptance")))));
    JSONObject op = new JSONObject();
    for (String f :
        new String[] {"op", "class", "name", "source", "old", "new", "type", "material", "replace"})
      op.put(f, type("string"));
    op.put("path", pathType())
        .put("parent", pathType())
        .put("properties", type("object").put("additionalProperties", true))
        .put("size", array(type("number")))
        .put("center", array(type("number")))
        .put("enabled", type("boolean"))
        .put("value", new JSONObject());
    String[][] forms = {
      {"create", "parent,class,name,properties?,source?"},
      {"ensure_service", "class"},
      {"set", "path,properties"},
      {"rename", "path,name"},
      {"move", "path,parent"},
      {"duplicate", "path,parent,name"},
      {"delete", "path"},
      {"edit_script", "path,old,new"},
      {"script_replace", "path,old,source"},
      {"attribute", "path,name,value,type?"},
      {"tag", "path,name,enabled"},
      {"terrain", "center,size,material,replace?"}
    };
    JSONArray variants = new JSONArray();
    for (String[] form : forms) {
      JSONObject props =
          new JSONObject().put("op", type("string").put("enum", new JSONArray().put(form[0])));
      JSONArray required = new JSONArray().put("op");
      for (String f : form[1].split(",")) {
        boolean need = !f.endsWith("?");
        String k = need ? f : f.substring(0, f.length() - 1);
        props.put(k, op.get(k));
        if (need) required.put(k);
      }
      variants.put(
          type("object")
              .put("properties", props)
              .put("required", required)
              .put("additionalProperties", false));
    }
    fields.put("operations", array(new JSONObject().put("anyOf", variants)));
    return type("object")
        .put(
            "properties",
            new JSONObject()
                .put(
                    "action",
                    type("string").put("enum", new JSONArray(StudioDeveloperEngine.ACTIONS)))
                .put("parameters", type("object").put("properties", fields)))
        .put("required", new JSONArray(Arrays.asList("action", "parameters")));
  }

  private static String fields(String name) {
    switch (name) {
      case "finish":
        return "answer";
      case "script_design":
        return "placements";
      case "project_edit":
        return "operations";
      case "editor_schema":
        return "class";
      case "project_select":
      case "inspect_instance":
      case "rig_inspect":
        return "path";
      case "search_game_tree":
        return "path?,max_depth?,keywords?";
      case "script_read":
        return "target_file,start_line?";
      case "script_search":
        return "keywords";
      case "script_grep":
      case "knowledge_search":
      case "resource_search":
        return "query";
      case "diagnostics_scan":
        return "path?";
      case "search_toolbox":
        return "query,type";
      case "toolbox_import":
        return "asset_id,parent";
      case "reference_lookup":
        return "topic";
      case "documentation_get":
        return "url,query?";
      case "publish_animation":
        return "path,display_name,group_id?";
      case "asset_capabilities":
        return "group_id?";
      case "task_plan":
        return "steps";
      case "procedural_generate":
        return "generator,spec";
      case "procedural_apply":
        return "build_id";
      case "quality_check":
        return "paths,particle_budget?";
      case "resource_inspect":
        return "repository";
      default:
        return "";
    }
  }

  void configure(int provider, JSONObject body) throws Exception {
    JSONObject all =
        schema()
            .getJSONObject("properties")
            .getJSONObject("parameters")
            .getJSONObject("properties");
    JSONArray tools = new JSONArray();
    for (String name : StudioDeveloperEngine.ACTIONS) {
      JSONObject props = new JSONObject();
      JSONArray required = new JSONArray();
      for (String f : fields(name).split(",")) {
        if (f.isEmpty()) continue;
        boolean need = !f.endsWith("?");
        String k = need ? f : f.substring(0, f.length() - 1);
        props.put(
            k, k.equals("path") && !name.equals("project_select") ? type("string") : all.get(k));
        if (need) required.put(k);
      }
      JSONObject params = type("object").put("properties", props).put("required", required);
      String help =
          name.equals("project_edit")
              ? "Apply explicit editor operations. op is create/set/etc. Procedural tools are"
                    + " separate functions, never operations."
              : name.equals("procedural_generate")
                  ? "Compile parameter-driven geometry or component preview. skills_list gives"
                        + " generator fields. Apply only with procedural_apply."
                  : "Execute "
                      + name
                      + " using actual editor protocol; one function per turn. Return factual"
                      + " observations.";
      JSONObject t = new JSONObject().put("name", name).put("description", help);
      if (provider == 0) t.put("type", "function").put("parameters", params).put("strict", false);
      else if (provider == 1) t.put("parametersJsonSchema", params);
      else if (provider == 2) t.put("input_schema", params);
      else t.put("parameters", params);
      tools.put(provider >= 3 ? new JSONObject().put("type", "function").put("function", t) : t);
    }
    if (provider == 0)
      body.put("tools", tools).put("tool_choice", "required").put("parallel_tool_calls", false);
    else if (provider == 1)
      body.put("tools", new JSONArray().put(new JSONObject().put("functionDeclarations", tools)))
          .put(
              "toolConfig",
              new JSONObject().put("functionCallingConfig", new JSONObject().put("mode", "ANY")));
    else if (provider == 2)
      body.put("tools", tools)
          .put("max_tokens", 8192)
          .put(
              "tool_choice",
              new JSONObject().put("type", "any").put("disable_parallel_tool_use", true));
    else body.put("tools", tools).put("tool_choice", "required").put("parallel_tool_calls", false);
    String key = provider == 0 ? "input" : provider == 1 ? "contents" : "messages";
    JSONArray turns = body.getJSONArray(key);
    if (provider == 0) {
      JSONArray result = new JSONArray();
      for (int i = 0; i < turns.length(); i++) {
        Exchange found = null;
        for (Exchange e : exchanges) if (e.index == i && i + 1 < turns.length()) found = e;
        if (found == null) {
          result.put(turns.get(i));
          continue;
        }
        JSONArray nativeItems = found.original.getJSONArray("_output");
        for (int j = 0; j < nativeItems.length(); j++) result.put(nativeItems.get(j));
        result.put(
            new JSONObject()
                .put("type", "function_call_output")
                .put("call_id", found.id)
                .put("output", turns.getJSONObject(++i).optString("content")));
      }
      body.put(key, result);
      return;
    }
    int offset =
        provider >= 3
                && turns.length() > 0
                && turns.getJSONObject(0).optString("role").equals("system")
            ? 1
            : 0;
    for (Exchange e : exchanges) {
      int at = e.index + offset;
      if (at + 1 >= turns.length()) continue;
      JSONObject observation = turns.getJSONObject(at + 1);
      String text =
          provider == 1
              ? observation.getJSONArray("parts").getJSONObject(0).getString("text")
              : observation.optString("content");
      if (provider == 1) {
        turns.put(at, e.original);
        JSONObject reply =
            new JSONObject()
                .put("name", e.name)
                .put("response", new JSONObject().put("result", text));
        JSONArray parts = e.original.getJSONArray("parts");
        for (int j = 0; j < parts.length(); j++) {
          JSONObject call = parts.getJSONObject(j).optJSONObject("functionCall");
          if (call != null && call.has("id")) reply.put("id", call.getString("id"));
        }
        turns.put(
            at + 1,
            new JSONObject()
                .put("role", "user")
                .put(
                    "parts", new JSONArray().put(new JSONObject().put("functionResponse", reply))));
      } else if (provider == 2) {
        turns.put(
            at,
            new JSONObject()
                .put("role", "assistant")
                .put("content", e.original.getJSONArray("content")));
        turns.put(
            at + 1,
            new JSONObject()
                .put("role", "user")
                .put(
                    "content",
                    new JSONArray()
                        .put(
                            new JSONObject()
                                .put("type", "tool_result")
                                .put("tool_use_id", e.id)
                                .put("content", text))));
      } else {
        turns.put(at, e.original);
        turns.put(
            at + 1,
            new JSONObject().put("role", "tool").put("tool_call_id", e.id).put("content", text));
      }
    }
  }

  private static boolean known(String n) {
    return WRAPPER.equals(n) || Arrays.asList(StudioDeveloperEngine.ACTIONS).contains(n);
  }

  String extract(int provider, JSONObject response, int nextIndex) throws Exception {
    JSONObject args = null, original = null;
    String name = "", id = "";
    int count = 0;
    if (provider == 0) {
      JSONArray list = response.optJSONArray("output");
      if (list != null)
        for (int i = 0; i < list.length(); i++) {
          JSONObject call = list.getJSONObject(i);
          if (!call.optString("type").equals("function_call")) continue;
          count++;
          if (known(call.optString("name"))) {
            name = call.getString("name");
            args = new JSONObject(call.getString("arguments"));
            original = new JSONObject().put("_output", list);
            id = call.getString("call_id");
          }
        }
    } else if (provider == 1) {
      JSONArray candidates = response.optJSONArray("candidates");
      if (candidates != null && candidates.length() > 0) {
        JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
        JSONArray parts = content == null ? null : content.optJSONArray("parts");
        if (parts != null)
          for (int i = 0; i < parts.length(); i++) {
            JSONObject call = parts.getJSONObject(i).optJSONObject("functionCall");
            if (call == null) continue;
            count++;
            if (known(call.optString("name"))) {
              name = call.getString("name");
              args = call.optJSONObject("args");
              if (args == null) args = new JSONObject();
              original = content;
            }
          }
      }
    } else if (provider == 2) {
      JSONArray list = response.optJSONArray("content");
      if (list != null)
        for (int i = 0; i < list.length(); i++) {
          JSONObject call = list.getJSONObject(i);
          if (!call.optString("type").equals("tool_use")) continue;
          count++;
          if (known(call.optString("name"))) {
            name = call.getString("name");
            args = call.getJSONObject("input");
            id = call.getString("id");
            original = new JSONObject().put("content", list);
          }
        }
    } else {
      JSONArray choices = response.optJSONArray("choices");
      JSONObject message =
          choices == null || choices.length() == 0
              ? null
              : choices.getJSONObject(0).optJSONObject("message");
      JSONArray calls = message == null ? null : message.optJSONArray("tool_calls");
      if (calls != null)
        for (int i = 0; i < calls.length(); i++) {
          JSONObject call = calls.getJSONObject(i), fn = call.getJSONObject("function");
          count++;
          if (known(fn.optString("name"))) {
            name = fn.getString("name");
            id = call.getString("id");
            args = new JSONObject(fn.getString("arguments"));
            original = message;
          }
        }
    }
    if (count == 0) return null;
    if (count != 1 || args == null) return "{\"action\":\"invalid_tool_selection\"}";
    exchanges.add(new Exchange(nextIndex, original, id, name));
    if (!name.equals(WRAPPER)) args = new JSONObject(args.toString()).put("action", name);
    return args.toString();
  }

  static JSONObject normalize(JSONObject input) throws Exception {
    JSONObject current = input;
    for (int depth = 0; depth < 3; depth++) {
      String name = current.optString("action", current.optString("name"));
      JSONObject nested = current.optJSONObject("parameters");
      if (nested == null) nested = current.optJSONObject("arguments");
      if (name.equals(WRAPPER)) {
        if (nested == null || !nested.has("action"))
          throw new StudioAiAgent.Refusal(
              "studio_action é o envelope; escolha uma operação do catálogo.");
        current = nested;
        continue;
      }
      if (nested != null && nested.has("action") && !name.equals(nested.optString("action")))
        throw new StudioAiAgent.Refusal("Envelope com ações contraditórias.");
      if (!known(name) || name.equals(WRAPPER))
        throw new StudioAiAgent.Refusal(
            "Operação desconhecida: escolha uma ferramenta do catálogo.");
      return new JSONObject((nested == null ? current : nested).toString()).put("action", name);
    }
    throw new StudioAiAgent.Refusal("Envelope excedeu o limite.");
  }
}
