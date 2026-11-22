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

  /** A concrete mobile input gap, not a claim that a touch binding works in Play. */
  static JSONArray mobileInputSignals(String code)throws Exception {
    JSONArray issues=new JSONArray();String executable=mask(code,true);
    boolean keyboard=Pattern.compile("\\b(?:InputBegan|IsKeyDown)\\b").matcher(executable).find()
        &&Pattern.compile("\\bEnum\\s*\\.\\s*KeyCode\\s*\\.").matcher(executable).find();
    boolean touch=Pattern.compile("\\.\\s*(?:Activated|MouseButton1Click|MouseButton1Down|TouchTap|TouchStarted|TouchMoved|TouchEnded)\\s*:").matcher(executable).find()
        ||Pattern.compile(":\\s*BindAction(?:AtPriority)?\\s*\\([^,]+,[\\s\\S]{0,1200}?,\\s*true\\s*,").matcher(executable).find();
    if(keyboard&&!touch)issues.put("Entrada apenas por teclado identificada (InputBegan/IsKeyDown e Enum.KeyCode), sem ligação de toque confirmada neste script. O alvo é Android: adicione botões funcionais com Activated/MouseButton1Click ou BindAction com createTouchButton=true, reutilizando a mesma lógica. Comentários e botões sem callback não resolvem essa lacuna. Esta análise estática não substitui Play.");
    return issues;
  }

  /** Literal local references only; runtime-created objects remain unknown. */
  static JSONArray missingLocalReferences(String scriptPath,String code,StudioAiAgent.Host host)throws Exception {
    JSONArray issues=new JSONArray();Map<String,String> aliases=new HashMap<>();aliases.put("script",scriptPath);
    String clean=mask(code.replaceAll("(?m)^\\s*\\d+→", ""),false);
    Pattern assign=Pattern.compile("^\\s*(?:local\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*([A-Za-z_][A-Za-z0-9_]*(?:\\.Parent)*)(.*)$");
    Pattern child=Pattern.compile("^\\s*:\\s*(?:WaitForChild|FindFirstChild)\\s*\\(\\s*['\"]([^'\"\\\\\\r\\n]{1,80})['\"]\\s*(?:,\\s*[0-9.]+\\s*)?\\)");
    Set<String> checked=new HashSet<>();int reads=0;
    for(String line:clean.split("\\n")) {
      Matcher a=assign.matcher(line);if(!a.matches())continue;
      String binding=a.group(1),base=a.group(2),tail=a.group(3);String[] parts=base.split("\\.");String path=aliases.get(parts[0]);
      if(path==null){aliases.remove(binding);continue;}
      for(int i=1;i<parts.length&&path!=null;i++){int dot=path.lastIndexOf('.');path=dot<0?null:path.substring(0,dot);}
      if(path==null){aliases.remove(binding);continue;}
      for(;;){Matcher c=child.matcher(tail);if(!c.find())break;path+="."+c.group(1);tail=tail.substring(c.end());
        if(checked.add(path)&&reads++<24) {
          try {
            JSONObject info=new JSONObject(host.call("inspect_instance",new JSONObject().put("path",path)));
            if(info.has("error")||info.has("found")&&!info.optBoolean("found"))issues.put("Referência local ausente no editor: "+path);
          } catch(Exception missing) {issues.put("Não foi possível confirmar a referência local "+path+". Inspecione a hierarquia e corrija o caminho ou crie o filho antes de concluir.");}
        }
      }
      if(tail.trim().isEmpty()||tail.trim().startsWith("::"))aliases.put(binding,path);else aliases.remove(binding);
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
