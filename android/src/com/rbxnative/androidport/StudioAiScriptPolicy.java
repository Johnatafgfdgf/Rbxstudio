package com.rbxnative.androidport;

import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Static preflight; this is not a safety certificate for arbitrary Luau. */
final class StudioAiScriptPolicy {
  private static final Pattern ASSET = Pattern.compile("rbxassetid://([0-9]{1,20})");
  private static final Pattern REMOTE =
      Pattern.compile("\\bloadstring\\s*\\(|\\brequire\\s*\\(\\s*[0-9]|:HttpGet\\s*\\(");

  /** Conservative review signals; these do not certify server authority or runtime behavior. */
  static JSONArray reviewSignals(String code)throws Exception {
    JSONArray issues=new JSONArray();String executable=mask(code,true);
    if(Pattern.compile("\\.OnServerEvent\\s*:\\s*Connect\\s*\\(").matcher(executable).find()) {
      boolean clock=Pattern.compile("\\b(?:os\\.clock|os\\.time|time|tick)\\s*\\(").matcher(executable).find();
      boolean delegated=Pattern.compile("\\b(?:[A-Za-z_][A-Za-z0-9_]*(?:RateLimit|RateLimiter|Cooldown|Throttle)[A-Za-z0-9_]*|RateLimit|RateLimiter|Cooldown|Throttle)\\s*[.:]",Pattern.CASE_INSENSITIVE).matcher(executable).find();
      if(!clock&&!delegated)issues.put("OnServerEvent sem controle de frequência evidente. Implemente um limite por jogador no servidor, ou leia e comprove o limitador delegado. Limitar a quantidade por chamada não limita a frequência.");
    }
    return issues;
  }

  static void check(JSONArray operations, Set<String> ids, StudioAiAgent.Host host)
      throws Exception {
    for (int i = 0; i < operations.length(); i++) {
      JSONObject op = operations.getJSONObject(i);
      JSONObject values=op.optJSONObject("properties");
      if(values!=null)for(String field:Arrays.asList("Texture","TextureID","TextureId","MeshId","MeshID","SoundId","AnimationId","Image","ColorMap","NormalMap","RoughnessMap","MetalnessMap","SkyboxBk","SkyboxDn","SkyboxFt","SkyboxLf","SkyboxRt","SkyboxUp")) {
        Matcher asset=ASSET.matcher(values.optString(field));
        while(asset.find())if(!ids.contains(asset.group(1)))throw new StudioAiAgent.Refusal("ID de asset não confirmado em "+field+": "+asset.group(1)+". Consulte a Toolbox ou use um recurso local real.");
      }
      String code = op.optString("source", op.optString("new"));
      JSONObject props=op.optJSONObject("properties");
      if(props!=null && props.has("Source")) {
        if(op.has("source") && !code.equals(props.optString("Source")))
          throw new StudioAiAgent.Refusal("Source e source são diferentes. Envie um único código Luau.");
        code=props.optString("Source");
      }
      if (code.isEmpty()) continue;
      code = mask(code, false);
      Set<String> allowed = new HashSet<>(ids);
      if (Arrays.asList("script_replace", "edit_script").contains(op.optString("op"))) {
        // These operations compare old with the real stored source in the native preview.
        Matcher old = ASSET.matcher(op.optString("old"));
        while (old.find()) allowed.add(old.group(1));
      }
      Matcher found = ASSET.matcher(code);
      while (found.find())
        if (!allowed.contains(found.group(1)))
          throw new StudioAiAgent.Refusal(
              "ID de asset não confirmado no código: "
                  + found.group(1)
                  + ". Consulte a Toolbox ou use recursos locais reais.");
      if (REMOTE.matcher(mask(code, true)).find()
          && !host.approve(
              "unsafe_code",
              "O código proposto pode carregar código remoto. Revise origem e dependências."))
        throw new StudioAiAgent.Refusal("Carregamento de código remoto não autorizado.");
      inspectAssignments(code, host);
    }
  }

  private static void inspectAssignments(String code, StudioAiAgent.Host host) throws Exception {
    Pattern binding =
        Pattern.compile(
            "\\b([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*Instance\\.new\\s*\\(\\s*['\"]([A-Za-z][A-Za-z0-9_]*)['\"]");
    Map<String, String> classes = new LinkedHashMap<>();
    Set<String> ambiguous = new HashSet<>();
    String executable = mask(code, true);
    Matcher bindings = binding.matcher(code);
    while (bindings.find()) {
      if (executable.charAt(bindings.start()) == ' ') continue;
      String variable = bindings.group(1),
          kind = bindings.group(2),
          old = classes.put(variable, kind);
      if (old != null && !old.equals(kind)) ambiguous.add(variable);
    }
    Map<String, JSONObject> schemas = new HashMap<>();
    Set<String> derived =
        new HashSet<>(
            Arrays.asList(
                "Name",
                "Parent",
                "Position",
                "Orientation",
                "Rotation",
                "WorldPosition",
                "WorldCFrame",
                "WorldOrientation"));
    for (Map.Entry<String, String> entry : classes.entrySet()) {
      if (ambiguous.contains(entry.getKey())) continue;
      Matcher assignments =
          Pattern.compile(
                  "\\b" + Pattern.quote(entry.getKey()) + "\\.([A-Za-z_][A-Za-z0-9_]*)\\s*=(?!=)")
              .matcher(executable);
      while (assignments.find()) {
        String property = assignments.group(1);
        if (derived.contains(property)) continue;
        JSONObject schema = schemas.get(entry.getValue());
        if (schema == null) {
          if (schemas.size() >= 12)
            throw new StudioAiAgent.Refusal(
                "Divida o script para verificar suas classes em lotes menores.");
          schema =
              new JSONObject(
                  host.call(
                      StudioAiProjectEdit.SCHEMA, new JSONObject().put("class", entry.getValue())));
          schemas.put(entry.getValue(), schema);
        }
        JSONObject properties = schema.optJSONObject("properties");
        if (properties != null && !properties.has(property))
          throw new StudioAiAgent.Refusal(
              entry.getValue()
                  + "."
                  + property
                  + " não foi confirmado pelo schema do editor. Consulte editor_schema e adapte o"
                  + " código.");
      }
    }
  }

  /** Preserve offsets while excluding Luau comments and optionally quoted strings. */
  private static String mask(String code, boolean strings) {
    char[] out = code.toCharArray();
    for (int i = 0; i < code.length(); ) {
      boolean comment = code.startsWith("--", i);
      int start = i, at = comment ? i + 2 : i, end = at;
      int equals = 0;
      if (at < code.length() && code.charAt(at) == '[') {
        int p = at + 1;
        while (p < code.length() && code.charAt(p) == '=') {
          equals++;
          p++;
        }
        if (p < code.length() && code.charAt(p) == '[') {
          StringBuilder close = new StringBuilder("]");
          for (int j = 0; j < equals; j++) close.append('=');
          close.append(']');
          int found = code.indexOf(close.toString(), p + 1);
          end = found < 0 ? code.length() : found + close.length();
        }
      }
      if (end == at && comment) {
        end = code.indexOf('\n', at);
        if (end < 0) end = code.length();
      } else if (end == at
          && !comment
          && at < code.length()
          && (code.charAt(at) == '\'' || code.charAt(at) == '"')) {
        char quote = code.charAt(at);
        end = at + 1;
        while (end < code.length()) {
          char c = code.charAt(end++);
          if (c == '\\' && end < code.length()) end++;
          else if (c == quote) break;
        }
      }
      if (end > at || comment) {
        if (comment || strings)
          for (int j = start; j < end; j++) if (out[j] != '\n' && out[j] != '\r') out[j] = ' ';
        i = end;
      } else i++;
    }
    return new String(out);
  }
}
