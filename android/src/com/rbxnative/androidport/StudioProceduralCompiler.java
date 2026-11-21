package com.rbxnative.androidport;

import java.util.*;
import org.json.*;

/** Parameter-driven compilers; every result is an explicit, editable editor operation. */
final class StudioProceduralCompiler {
  static final String[] GENERATORS = {
    "terrain", "building", "scatter", "vfx", "ui", "animation", "camera", "audio"
  };
  private final JSONObject s;
  private final JSONArray ops = new JSONArray();
  private final String name, parent;
  private JSONArray ground;

  private StudioProceduralCompiler(JSONObject spec) throws Exception {
    s = spec;
    name = label(s, "name");
    parent = StudioDeveloperEngine.required(s, "parent", 400);
  }

  static JSONObject type(String t) throws Exception {
    return new JSONObject().put("type", t);
  }

  static JSONObject object(JSONObject p) throws Exception {
    return type("object").put("properties", p);
  }

  static JSONObject array(JSONObject i) throws Exception {
    return type("array").put("items", i);
  }

  static JSONObject schema() throws Exception {
    JSONObject p = new JSONObject();
    for (String k :
        new String[] {
          "name",
          "parent",
          "title",
          "material",
          "roof_material",
          "priority",
          "terrain_build_id",
          "rig_path"
        }) p.put(k, type("string"));
    for (String k :
        new String[] {
          "width",
          "depth",
          "height",
          "wall",
          "door_width",
          "spacing",
          "cell",
          "amplitude",
          "river_width",
          "water_level",
          "cell_height"
        }) p.put(k, type("number"));
    for (String k : new String[] {"seed", "grid", "count", "columns"}) p.put(k, type("integer"));
    for (String k : new String[] {"loop", "autoplay"}) p.put(k, type("boolean"));
    for (String k : new String[] {"origin", "color"})
      p.put(k, array(type("number")).put("minItems", 3).put("maxItems", 3));
    p.put(
        "layers",
        array(
            object(
                new JSONObject()
                    .put("name", type("string"))
                    .put("class", type("string"))
                    .put("properties", type("object").put("additionalProperties", true))
                    .put("start", array(type("number")))
                    .put("end", array(type("number"))))));
    p.put(
        "exclusions",
        array(
            object(
                new JSONObject()
                    .put("x", type("number"))
                    .put("z", type("number"))
                    .put("width", type("number"))
                    .put("depth", type("number")))));
    p.put(
        "items",
        array(
            object(
                new JSONObject()
                    .put("name", type("string"))
                    .put("text", type("string"))
                    .put("on_activate", type("string")))));
    JSONObject pose = new JSONObject();
    for (String k : new String[] {"bone", "cframe", "easing", "direction"})
      pose.put(k, type("string"));
    p.put(
        "frames",
        array(
            object(
                new JSONObject()
                    .put("time", type("number"))
                    .put("cframe", type("string"))
                    .put("fov", type("number"))
                    .put("poses", array(object(pose))))));
    JSONObject sound = new JSONObject();
    for (String k : new String[] {"name", "asset_id"}) sound.put(k, type("string"));
    for (String k : new String[] {"volume", "min_distance", "max_distance", "playback_speed"})
      sound.put(k, type("number"));
    sound.put("loop", type("boolean"));
    p.put("sounds", array(object(sound)));
    p.put("theme",object(new JSONObject().put("surface",type("string")).put("text",type("string")).put("accent",type("string")).put("font",type("string")).put("text_size",type("integer"))));
    p.put("elements",array(StudioAiVisualBuilder.elementSchema()));
    return object(p).put("required", new JSONArray().put("name").put("parent"));
  }

  static JSONObject describe(String g) throws Exception {
    String input;
    switch (g) {
      case "terrain":
        input =
            "name,parent=Workspace,origin:[x,y,z],grid:2..8,cell:4..24,height:4..128,amplitude:0..80,seed:integer,material,river_width:0..48,water_level"
                + " optional. Ruído fractal e suavização; usa o Terrain real. Padrões declarados na"
                + " prévia.";
        break;
      case "building":
        input =
            "name,parent,origin:[x,y,z],width:12..80,depth:12..80,height:8..40,wall:0.5..3,door_width:4..12,color:[r,g,b],material,roof_material"
                + " optional. Estrutura modular editável com entrada aberta.";
        break;
      case "scatter":
        input =
            "name,parent,origin:[x,y,z],width:8..192,depth:8..192,count:1..48,spacing:4..40,height:4..30,seed,terrain_build_id"
                + " optional,exclusions:[{x,z,width,depth}]. Sem terreno vinculado usa plano"
                + " origin.y; com vínculo evita água e posiciona sobre a superfície recém-criada.";
        break;
      case "vfx":
        input =
            "name,parent,origin,layers:[{name,class:ParticleEmitter/PointLight/SpotLight/Beam/Trail,properties:{...},start:[x,y,z],end:[x,y,z]}]."
                + " 1..8 camadas. Rate <=300 por camada e <=600 total. Beam/Trail exigem start/end."
                + " IDs precisam vir do usuário ou catálogo.";
        break;
      case "ui":
        input =
            "name,parent=StarterGui,title,columns:1..4,cell_height:44..120,items:[{name,text,on_activate:Luau"
                + " optional}]. Até 32 itens. Sem callback cria labels; com callback cria botões"
                + " conectados a LocalScript. Para AI Visual Builder personalizado, substitua items por elements:"
                + " [{class,name,properties:{...},on_activate:Luau opcional,children:[...]}]. theme opcional:"
                + " surface/text/accent em RGB 0–255, font, text_size. Pode compor imagens, painéis, layouts e constraints"
                + " editáveis; escolha nomes, cores, posições e proporções a partir do pedido atual. Não reutilize estilos sem motivo.";
        break;
      case "animation":
        input =
            "name,parent,rig_path,loop,priority,frames:[{time:0..120,poses:[{bone:HumanoidRootPart/Torso/Right"
                + " Arm,cframe:3 ou 12 números em texto,easing,direction}]}]. Até 24 frames, nomes"
                + " reais do rig e tempos crescentes. Produz KeyframeSequence editável; não"
                + " comprova reprodução.";
        break;
      case "camera":
        input =
            "name,parent=StarterPlayer.StarterPlayerScripts,autoplay:true,frames:[{time:0..120,cframe:string,fov:20..100}]."
                + " 2..24 frames, primeiro time=0. Timeline editável e controlador TweenService com"
                + " restauração da câmera.";
        break;
      case "audio":
        input =
            "name,parent,origin,sounds:[{name,asset_id,volume:0..1,loop,min_distance:0..100,max_distance:1..500,playback_speed:0.5..2}]."
                + " 1..8 sons espaciais, inicialmente parados. Nenhum upload implícito.";
        break;
      default:
        throw new StudioAiAgent.Refusal("Gerador desconhecido.");
    }
    return new JSONObject()
        .put("inputs", input)
        .put("output", "Prévia de operações reais; aplicar separadamente.");
  }

  static JSONObject normalize(String g, JSONObject input) throws Exception {
    JSONObject s = new JSONObject(input.toString());
    if (!s.has("origin") && !Arrays.asList("ui", "camera", "animation").contains(g))
      s.put("origin", new JSONArray("[0,0,0]"));
    if (g.equals("terrain")) {
      if (!s.has("grid") && s.opt("width") instanceof Number) {
        double w = StudioDeveloperEngine.bounded(s, "width", 8, 192);
        int grid = Math.max(2, Math.min(8, (int) Math.ceil(w / 8)));
        s.put("grid", grid).put("cell", w / grid);
      }
      defaults(
          s,
          new JSONObject()
              .put("grid", 6)
              .put("cell", 8)
              .put("height", 24)
              .put("amplitude", 8)
              .put("seed", 0)
              .put("material", "Grass"));
    } else if (g.equals("building"))
      defaults(
          s,
          new JSONObject()
              .put("width", 20)
              .put("depth", 20)
              .put("height", 12)
              .put("wall", 1)
              .put("door_width", 6)
              .put("color", new JSONArray("[160,160,160]"))
              .put("material", "SmoothPlastic"));
    else if (g.equals("scatter"))
      defaults(
          s,
          new JSONObject()
              .put("width", 48)
              .put("depth", 48)
              .put("spacing", 6)
              .put("height", 12)
              .put("seed", 0));
    else if (g.equals("ui")) defaults(s, new JSONObject().put("columns", 2).put("cell_height", 48));
    return s;
  }

  private static void defaults(JSONObject into, JSONObject d) throws Exception {
    for (Iterator<String> k = d.keys(); k.hasNext(); ) {
      String key = k.next();
      if (!into.has(key)) into.put(key, d.get(key));
    }
  }

  static JSONArray compile(String g, JSONObject spec) throws Exception {
    return compile(g, spec, null);
  }

  static JSONArray compile(String g, JSONObject spec, JSONArray ground) throws Exception {
    StudioProceduralCompiler c = new StudioProceduralCompiler(normalize(g, spec));
    c.ground = ground;
    switch (g) {
      case "terrain":
        c.terrain();
        break;
      case "building":
        c.building();
        break;
      case "scatter":
        c.scatter();
        break;
      case "vfx":
        c.vfx();
        break;
      case "ui":
        c.ui();
        break;
      case "animation":
        c.animation();
        break;
      case "camera":
        c.camera();
        break;
      case "audio":
        c.audio();
        break;
      default:
        throw new StudioAiAgent.Refusal("Gerador desconhecido.");
    }
    if (c.ops.length() > 256 || c.ops.toString().length() > 240000)
      throw new StudioAiAgent.Refusal("Prévia excede o orçamento; divida a tarefa.");
    return c.ops;
  }

  static String label(JSONObject o, String key) throws Exception {
    String n = StudioDeveloperEngine.required(o, key, 40);
    if (!n.matches("[A-Za-z_][A-Za-z0-9_]{0,39}"))
      throw new StudioAiAgent.Refusal("Nome simples inválido: " + key);
    return n;
  }

  double n(String key, double min, double max) throws Exception {
    return StudioDeveloperEngine.bounded(s, key, min, max);
  }

  int integer(String key, int min, int max) throws Exception {
    double v = n(key, min, max);
    if (v != Math.rint(v)) throw new StudioAiAgent.Refusal("Inteiro necessário: " + key);
    return (int) v;
  }

  static double[] vector(JSONObject o, String key) throws Exception {
    JSONArray a = o.optJSONArray(key);
    Object raw = o.opt(key);
    if (a == null && raw instanceof String) {
      String v = ((String) raw).trim();
      if (v.matches("\\[?[ +\\-0-9.eE,]+\\]?")) {
        try {
          a = new JSONArray(v.startsWith("[") ? v : "[" + v + "]");
        } catch (JSONException ignored) {
        }
      }
    }
    if (a == null || a.length() != 3)
      throw new StudioAiAgent.Refusal("Vetor de três números: " + key);
    double[] v = new double[3];
    for (int i = 0; i < 3; i++) {
      Object x = a.get(i);
      if (!(x instanceof Number)) throw new StudioAiAgent.Refusal("Vetor inválido.");
      v[i] = ((Number) x).doubleValue();
      if (!Double.isFinite(v[i]) || Math.abs(v[i]) > 16000)
        throw new StudioAiAgent.Refusal("Vetor fora do limite.");
    }
    return v;
  }

  static String triple(double a, double b, double c) {
    return String.format(Locale.ROOT, "%.4f, %.4f, %.4f", a, b, c);
  }

  static String vec(double[] v) {
    return triple(v[0], v[1], v[2]);
  }

  String root() {
    return parent + "." + name;
  }

  void create(String kind, String at, String item, JSONObject p) throws Exception {
    JSONObject op =
        new JSONObject().put("op", "create").put("class", kind).put("parent", at).put("name", item);
    if (p != null) op.put("properties", p);
    ops.put(op);
  }

  void source(String at, String item, String code) throws Exception {
    if (code.length() > 32000) throw new StudioAiAgent.Refusal("Código acima do orçamento.");
    create("LocalScript", at, item, null);
    ops.getJSONObject(ops.length() - 1).put("source", code);
  }

  void part(
      String at,
      String item,
      double[] o,
      double x,
      double y,
      double z,
      double w,
      double h,
      double d,
      String color,
      String material)
      throws Exception {
    create(
        "Part",
        at,
        item,
        new JSONObject()
            .put("Anchored", true)
            .put("CanCollide", true)
            .put("Size", triple(w, h, d))
            .put("CFrame", triple(o[0] + x, o[1] + y, o[2] + z))
            .put("Color", color)
            .put("Material", material));
  }

  void building() throws Exception {
    double[] o = vector(s, "origin"), rgb = vector(s, "color");
    for (double c : rgb)
      if (c < 0 || c > 255) throw new StudioAiAgent.Refusal("RGB fora do limite.");
    String c = vec(rgb), m = StudioDeveloperEngine.required(s, "material", 40);
    double w = n("width", 12, 80),
        d = n("depth", 12, 80),
        h = n("height", 8, 40),
        t = n("wall", .5, 3),
        door = n("door_width", 4, 12);
    if (door > w - 4) throw new StudioAiAgent.Refusal("Porta deixa paredes estreitas demais.");
    create("Model", parent, name, null);
    part(root(), "Floor", o, 0, 0, 0, w, 1, d, c, m);
    part(root(), "Back", o, 0, h / 2, -d / 2, w, h, t, c, m);
    part(root(), "Left", o, -w / 2, h / 2, 0, t, h, d, c, m);
    part(root(), "Right", o, w / 2, h / 2, 0, t, h, d, c, m);
    double side = (w - door) / 2, dh = Math.min(8, h - 1);
    part(root(), "FrontLeft", o, -door / 2 - side / 2, h / 2, d / 2, side, h, t, c, m);
    part(root(), "FrontRight", o, door / 2 + side / 2, h / 2, d / 2, side, h, t, c, m);
    part(root(), "DoorLintel", o, 0, dh + (h - dh) / 2, d / 2, door, h - dh, t, c, m);
    part(root(), "Roof", o, 0, h + .5, 0, w + 2, 1, d + 2, c, s.optString("roof_material", m));
  }

  static double hash(int x, int z, int seed) {
    int v = x * 374761393 + z * 668265263 + seed * 31;
    v = (v ^ (v >>> 13)) * 1274126177;
    return ((v ^ (v >>> 16)) & 0x7fffffff) / (double) 0x7fffffff;
  }

  static double noise(double x, double z, int seed) {
    int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
    double u = x - ix, v = z - iz;
    u = u * u * (3 - 2 * u);
    v = v * v * (3 - 2 * v);
    return (hash(ix, iz, seed) * (1 - u) + hash(ix + 1, iz, seed) * u) * (1 - v)
        + (hash(ix, iz + 1, seed) * (1 - u) + hash(ix + 1, iz + 1, seed) * u) * v;
  }

  void terrain() throws Exception {
    if (!parent.equals("Workspace"))
      throw new StudioAiAgent.Refusal("Terreno usa Workspace.Terrain.");
    double[] o = vector(s, "origin");
    int grid = integer("grid", 2, 8), seed = (int) n("seed", -2147483648d, 2147483647d);
    double cell = n("cell", 4, 24),
        height = n("height", 4, 128),
        amp = n("amplitude", 0, 80),
        river = s.has("river_width") ? n("river_width", 0, 48) : 0,
        water = s.has("water_level") ? n("water_level", 4, 128) : height * .5;
    double[][] heights = new double[grid][grid];
    for (int x = 0; x < grid; x++)
      for (int z = 0; z < grid; z++)
        heights[x][z] =
            Math.max(
                4,
                Math.min(
                    160,
                    height
                        + (noise(x * .24, z * .24, seed) - .5) * amp
                        + (noise(x * .63, z * .63, seed + 1) - .5) * amp * .3));
    for (int pass = 0; pass < 2; pass++)
      for (int x = 0; x < grid; x++)
        for (int z = 0; z < grid; z++) {
          int nx = Math.min(grid - 1, x + 1), nz = Math.min(grid - 1, z + 1);
          if (Math.abs(heights[x][z] - heights[nx][nz]) > cell) {
            double t = (heights[x][z] - heights[nx][nz]) * .15;
            heights[x][z] -= t;
            heights[nx][nz] += t;
          }
        }
    for (int x = 0; x < grid; x++)
      for (int z = 0; z < grid; z++) {
        double px = (x - (grid - 1) / 2d) * cell,
            pz = (z - (grid - 1) / 2d) * cell,
            h = heights[x][z];
        boolean channel =
            river > 0
                && Math.abs(px - Math.sin(pz / (grid * cell) * Math.PI * 2) * grid * cell * .15)
                    < river / 2;
        if (channel) h = Math.max(4, water - 4);
        region(
            o[0] + px,
            o[1] + h / 2,
            o[2] + pz,
            cell,
            h,
            cell,
            channel ? "Sand" : s.optString("material", "Grass"));
        if (channel && water - h >= 4)
          region(o[0] + px, o[1] + h + (water - h) / 2, o[2] + pz, cell, water - h, cell, "Water");
      }
  }

  void region(double x, double y, double z, double w, double h, double d, String material)
      throws Exception {
    ops.put(
        new JSONObject()
            .put("op", "terrain")
            .put("center", new JSONArray(new double[] {x, y, z}))
            .put("size", new JSONArray(new double[] {w, h, d}))
            .put("material", material));
  }

  double groundHeight(double x, double z) throws Exception {
    double top = Double.NEGATIVE_INFINITY;
    boolean water = false;
    for (int i = 0; i < ground.length(); i++) {
      JSONObject col = ground.getJSONObject(i);
      double[] c = vector(col, "center"), d = vector(col, "size");
      if (Math.abs(x - c[0]) <= d[0] / 2
          && Math.abs(z - c[2]) <= d[2] / 2
          && c[1] + d[1] / 2 > top) {
        top = c[1] + d[1] / 2;
        water = "Water".equals(col.getString("material"));
      }
    }
    return water ? Double.NaN : top;
  }

  void scatter() throws Exception {
    double[] o = vector(s, "origin");
    double w = n("width", 8, 192),
        d = n("depth", 8, 192),
        spacing = n("spacing", 4, 40),
        height = n("height", 4, 30);
    int count = integer("count", 1, 48);
    Random random = new Random((long) n("seed", -2147483648d, 2147483647d));
    List<double[]> points = new ArrayList<>();
    JSONArray excluded = s.optJSONArray("exclusions");
    if (excluded != null && excluded.length() > 32)
      throw new StudioAiAgent.Refusal("Muitas áreas reservadas.");
    for (int attempt = 0; attempt < count * 80 && points.size() < count; attempt++) {
      double x = (random.nextDouble() - .5) * w, z = (random.nextDouble() - .5) * d;
      boolean free = ground == null || Double.isFinite(groundHeight(o[0] + x, o[2] + z));
      for (double[] p : points) if (Math.hypot(x - p[0], z - p[1]) < spacing) free = false;
      if (excluded != null)
        for (int i = 0; i < excluded.length(); i++) {
          JSONObject e = excluded.getJSONObject(i);
          double ex = StudioDeveloperEngine.bounded(e, "x", -200, 200),
              ez = StudioDeveloperEngine.bounded(e, "z", -200, 200),
              ew = StudioDeveloperEngine.bounded(e, "width", 1, 400),
              ed = StudioDeveloperEngine.bounded(e, "depth", 1, 400);
          if (Math.abs(x - ex) < ew / 2 + spacing / 2 && Math.abs(z - ez) < ed / 2 + spacing / 2)
            free = false;
        }
      if (free) points.add(new double[] {x, z});
    }
    if (points.size() != count)
      throw new StudioAiAgent.Refusal(
          "Área insuficiente para quantidade e espaçamento; nenhuma edição gerada.");
    create("Model", parent, name, null);
    for (int i = 0; i < points.size(); i++) {
      double[] p = points.get(i), base = o.clone();
      String tree = "Tree_" + (i + 1);
      create("Model", root(), tree, null);
      double h = height * (.8 + random.nextDouble() * .4);
      if (ground != null) base[1] = groundHeight(o[0] + p[0], o[2] + p[1]);
      part(
          root() + "." + tree,
          "Trunk",
          base,
          p[0],
          h * .35,
          p[1],
          1.2,
          h * .7,
          1.2,
          "100,73,48",
          "Wood");
      part(
          root() + "." + tree,
          "Crown",
          base,
          p[0],
          h * .8,
          p[1],
          h * .55,
          h * .6,
          h * .55,
          "53,116,72",
          "Grass");
    }
  }

  void vfx() throws Exception {
    double[] o = vector(s, "origin");
    create("Model", parent, name, null);
    create(
        "Part",
        root(),
        "Emitter",
        new JSONObject()
            .put("Size", "1,1,1")
            .put("CFrame", vec(o))
            .put("Anchored", true)
            .put("CanCollide", false)
            .put("CanTouch", false)
            .put("Transparency", 1));
    JSONArray layers = s.getJSONArray("layers");
    if (layers.length() < 1 || layers.length() > 8)
      throw new StudioAiAgent.Refusal("Use de 1 a 8 camadas.");
    Set<String> names = new HashSet<>();
    double rate = 0;
    for (int i = 0; i < layers.length(); i++) {
      JSONObject l = layers.getJSONObject(i);
      String n = label(l, "name"), kind = StudioDeveloperEngine.required(l, "class", 40);
      if (!names.add(n)
          || !Arrays.asList("ParticleEmitter", "PointLight", "SpotLight", "Beam", "Trail")
              .contains(kind)) throw new StudioAiAgent.Refusal("Camada inválida ou repetida.");
      JSONObject p = new JSONObject(l.getJSONObject("properties").toString());
      if (kind.equals("ParticleEmitter")) {
        rate += StudioDeveloperEngine.bounded(p, "Rate", 0, 300);
        if (rate > 600) throw new StudioAiAgent.Refusal("Rate total acima de 600.");
      }
      if (kind.equals("Beam") || kind.equals("Trail")) {
        create(
            "Attachment",
            root() + ".Emitter",
            n + "Start",
            new JSONObject().put("CFrame", vec(vector(l, "start"))));
        create(
            "Attachment",
            root() + ".Emitter",
            n + "End",
            new JSONObject().put("CFrame", vec(vector(l, "end"))));
        p.put("Attachment0", new JSONObject().put("ref", root() + ".Emitter." + n + "Start"))
            .put("Attachment1", new JSONObject().put("ref", root() + ".Emitter." + n + "End"));
      }
      create(kind, root() + ".Emitter", n, p);
    }
  }

  void ui() throws Exception {
    if(s.has("elements")) {
      JSONArray built=StudioAiVisualBuilder.compile(s);
      for(int i=0;i<built.length();i++)ops.put(built.getJSONObject(i));
      return;
    }
    if (!parent.equals("StarterGui"))
      throw new StudioAiAgent.Refusal("GUI persistente usa StarterGui.");
    int columns = integer("columns", 1, 4);
    double h = n("cell_height", 44, 120);
    JSONArray items = s.getJSONArray("items");
    if (items.length() < 1 || items.length() > 32)
      throw new StudioAiAgent.Refusal("Use de 1 a 32 itens.");
    create("ScreenGui", parent, name, new JSONObject().put("ResetOnSpawn", false));
    create(
        "Frame",
        root(),
        "Panel",
        new JSONObject()
            .put("AnchorPoint", "0.5,0.5")
            .put("Position", "0.5,0,0.5,0")
            .put("Size", "0.85,0,0.8,0")
            .put("BackgroundColor3", "17,30,48"));
    create(
        "TextLabel",
        root() + ".Panel",
        "Title",
        new JSONObject()
            .put("Size", "1,0,0,44")
            .put("Text", StudioDeveloperEngine.required(s, "title", 100))
            .put("TextSize", 20)
            .put("TextColor3", "238,245,255")
            .put("BackgroundTransparency", 1));
    create(
        "ScrollingFrame",
        root() + ".Panel",
        "Items",
        new JSONObject()
            .put("Position", "0,12,0,48")
            .put("Size", "1,-24,1,-60")
            .put("AutomaticCanvasSize", "Y")
            .put("CanvasSize", "0,0,0,0")
            .put("BackgroundTransparency", 1));
    create(
        "UIGridLayout",
        root() + ".Panel.Items",
        "Layout",
        new JSONObject()
            .put("CellSize", (1d / columns) + ",-8,0," + h)
            .put("CellPadding", "0,8,0,8")
            .put("SortOrder", "LayoutOrder"));
    StringBuilder code = new StringBuilder("local items = script.Parent.Panel.Items\n");
    Set<String> names = new HashSet<>();
    boolean actions = false;
    for (int i = 0; i < items.length(); i++) {
      JSONObject item = items.getJSONObject(i);
      String n = label(item, "name"), callback = item.optString("on_activate");
      if (!names.add(n) || callback.length() > 3000)
        throw new StudioAiAgent.Refusal("Item repetido ou callback longo.");
      create(
          callback.isEmpty() ? "TextLabel" : "TextButton",
          root() + ".Panel.Items",
          n,
          new JSONObject()
              .put("Text", StudioDeveloperEngine.required(item, "text", 160))
              .put("TextSize", 16)
              .put("TextColor3", "238,245,255")
              .put("BackgroundColor3", "33,54,80")
              .put("LayoutOrder", i));
      if (!callback.isEmpty()) {
        actions = true;
        code.append("items.")
            .append(n)
            .append(".Activated:Connect(function()\n")
            .append(callback)
            .append("\nend)\n");
      }
    }
    if (actions) source(root(), "Actions", code.toString());
  }

  void animation() throws Exception {
    create(
        "KeyframeSequence",
        parent,
        name,
        new JSONObject()
            .put("Loop", s.optBoolean("loop", false))
            .put("Priority", s.optString("priority", "Action")));
    JSONArray frames = s.getJSONArray("frames");
    if (frames.length() < 1 || frames.length() > 24)
      throw new StudioAiAgent.Refusal("Use de 1 a 24 frames.");
    double previous = -1;
    for (int i = 0; i < frames.length(); i++) {
      JSONObject f = frames.getJSONObject(i);
      double time = StudioDeveloperEngine.bounded(f, "time", 0, 120);
      if (time <= previous) throw new StudioAiAgent.Refusal("Tempos precisam ser crescentes.");
      previous = time;
      String at = root() + ".Frame_" + i;
      create("Keyframe", root(), "Frame_" + i, new JSONObject().put("Time", time));
      JSONArray poses = f.getJSONArray("poses");
      if (poses.length() > 32) throw new StudioAiAgent.Refusal("Muitas poses.");
      Set<String> created = new HashSet<>(), assigned = new HashSet<>();
      for (int j = 0; j < poses.length(); j++) {
        JSONObject pose = poses.getJSONObject(j);
        String[] bones = StudioDeveloperEngine.required(pose, "bone", 240).split("/", -1);
        if (bones.length > 12) throw new StudioAiAgent.Refusal("Hierarquia longa.");
        String path = at;
        for (String bone : bones) {
          if (bone.isEmpty()
              || bone.length() > 80
              || !bone.equals(bone.trim())
              || !bone.matches("[A-Za-z_][A-Za-z0-9_ ]*"))
            throw new StudioAiAgent.Refusal("Nome de osso inválido.");
          String next = path + "." + bone;
          if (created.add(next)) create("Pose", path, bone, null);
          path = next;
        }
        if (!assigned.add(path)) throw new StudioAiAgent.Refusal("Pose repetida.");
        ops.put(
            new JSONObject()
                .put("op", "set")
                .put("path", path)
                .put(
                    "properties",
                    new JSONObject()
                        .put("CFrame", StudioDeveloperEngine.required(pose, "cframe", 300))
                        .put("EasingStyle", pose.optString("easing", "Linear"))
                        .put("EasingDirection", pose.optString("direction", "InOut"))));
      }
    }
  }

  void camera() throws Exception {
    if (!s.optBoolean("autoplay") || !parent.equals("StarterPlayer.StarterPlayerScripts"))
      throw new StudioAiAgent.Refusal(
          "Use autoplay=true e destino StarterPlayer.StarterPlayerScripts.");
    JSONArray frames = s.getJSONArray("frames");
    if (frames.length() < 2 || frames.length() > 24)
      throw new StudioAiAgent.Refusal("Use de 2 a 24 frames.");
    source(
        parent,
        name,
        "local TweenService=game:GetService('TweenService')\n"
            + "local c=workspace.CurrentCamera if not c then return end\n"
            + "local kind,subject,fov,cf=c.CameraType,c.CameraSubject,c.FieldOfView,c.CFrame\n"
            + "local frames={} for _,v in script:GetChildren() do if v:IsA('CFrameValue') then"
            + " table.insert(frames,v) end end\n"
            + "table.sort(frames,function(a,b) return a.Time.Value<b.Time.Value end)\n"
            + "local tween\n"
            + "local function restore() if tween then tween:Cancel() end c.CameraType=kind"
            + " c.CameraSubject=subject c.FieldOfView=fov c.CFrame=cf end\n"
            + "local cleanup=script.Destroying:Connect(restore)\n"
            + "local ok,err=pcall(function() c.CameraType=Enum.CameraType.Scriptable for i,v in"
            + " frames do if i==1 then c.CFrame=v.Value c.FieldOfView=v.Fov.Value else"
            + " tween=TweenService:Create(c,TweenInfo.new(v.Time.Value-frames[i-1].Time.Value,Enum.EasingStyle.Sine,Enum.EasingDirection.InOut),{CFrame=v.Value,FieldOfView=v.Fov.Value})"
            + " tween:Play() tween.Completed:Wait() end end end)\n"
            + "restore() cleanup:Disconnect() if not ok then warn(err) end");
    double previous = -1;
    for (int i = 0; i < frames.length(); i++) {
      JSONObject f = frames.getJSONObject(i);
      double time = StudioDeveloperEngine.bounded(f, "time", 0, 120);
      if (time <= previous || (i == 0 && time != 0))
        throw new StudioAiAgent.Refusal("Timeline deve começar em zero e avançar.");
      previous = time;
      String at = root() + ".Shot_" + i;
      create(
          "CFrameValue",
          root(),
          "Shot_" + i,
          new JSONObject().put("Value", StudioDeveloperEngine.required(f, "cframe", 300)));
      create("NumberValue", at, "Time", new JSONObject().put("Value", time));
      create(
          "NumberValue",
          at,
          "Fov",
          new JSONObject().put("Value", StudioDeveloperEngine.bounded(f, "fov", 20, 100)));
    }
  }

  void audio() throws Exception {
    create(
        "Part",
        parent,
        name,
        new JSONObject()
            .put("CFrame", vec(vector(s, "origin")))
            .put("Size", "1,1,1")
            .put("Anchored", true)
            .put("CanCollide", false)
            .put("Transparency", 1));
    JSONArray sounds = s.getJSONArray("sounds");
    if (sounds.length() < 1 || sounds.length() > 8)
      throw new StudioAiAgent.Refusal("Use de 1 a 8 sons.");
    for (int i = 0; i < sounds.length(); i++) {
      JSONObject a = sounds.getJSONObject(i);
      String id = StudioDeveloperEngine.required(a, "asset_id", 20);
      if (!id.matches("[0-9]{5,20}")) throw new StudioAiAgent.Refusal("ID inválido.");
      double min = StudioDeveloperEngine.bounded(a, "min_distance", 0, 100),
          max = StudioDeveloperEngine.bounded(a, "max_distance", 1, 500);
      if (max <= min) throw new StudioAiAgent.Refusal("Distâncias incompatíveis.");
      create(
          "Sound",
          root(),
          label(a, "name"),
          new JSONObject()
              .put("SoundId", "rbxassetid://" + id)
              .put("Volume", StudioDeveloperEngine.bounded(a, "volume", 0, 1))
              .put("Looped", a.optBoolean("loop"))
              .put("PlaybackSpeed", StudioDeveloperEngine.bounded(a, "playback_speed", .5, 2))
              .put("RollOffMinDistance", min)
              .put("RollOffMaxDistance", max)
              .put("Playing", false));
    }
  }
}
