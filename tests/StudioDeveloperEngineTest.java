package com.rbxnative.androidport;

import java.nio.file.*;
import java.util.*;
import org.json.*;

public final class StudioDeveloperEngineTest {
  static final class PolicyHost implements StudioAiAgent.Host {
    int approvals, reads;

    public String model(List<String[]> m, int b) {
      throw new AssertionError();
    }

    public String call(String tool, JSONObject args) throws Exception {
      yes(tool.equals(StudioAiProjectEdit.SCHEMA));
      reads++;
      return "{\"properties\":{\"Rate\":{},\"Enabled\":{},\"SoundId\":{}}}";
    }

    public boolean approve(String c, String d) {
      yes(c.equals("unsafe_code"));
      approvals++;
      return false;
    }

    public void check() {}

    public void progress(String s) {}

    public void writing(boolean a) {}

    public void capture(List<String[]> m) {
      throw new AssertionError();
    }
  }

  static void policies() throws Exception {
    String document = "{\"goal\":\"continuar criação\",\"evidence\":\"3 operações\"}";
    String encoded = hex(document);
    yes(document.equals(StudioAiStoredDocument.read(encoded + ":")));
    yes(document.equals(StudioAiStoredDocument.read(hex(encoded) + ":")));
    yes(StudioAiStoredDocument.read(null).isEmpty());
    try {
      StudioAiStoredDocument.read("zz:");
      throw new AssertionError();
    } catch (java.io.IOException expected) {
    }
    PolicyHost h = new PolicyHost();
    Set<String> ids = new HashSet<>(Arrays.asList("123456789"));
    StudioAiScriptPolicy.check(
        new JSONArray()
            .put(
                new JSONObject()
                    .put(
                        "source",
                        "--[=[ loadstring('bad') rbxassetid://99999999 ]=]\n"
                            + "local p=Instance.new('ParticleEmitter')\n"
                            + "p.Rate=40\n"
                            + "local label=\"p.ShapeExtents=3 require(99999)\"")),
        ids,
        h);
    yes(h.reads == 1 && h.approvals == 0);
    for (String code :
        new String[] {
          "local p=Instance.new('ParticleEmitter') p.ShapeExtents=3",
          "local s=Instance.new('Sound') s.SoundId='rbxassetid://987654321'",
          "require(999999)"
        }) {
      try {
        StudioAiScriptPolicy.check(
            new JSONArray().put(new JSONObject().put("source", code)), ids, h);
        throw new AssertionError();
      } catch (StudioAiAgent.Refusal expected) {
      }
    }
    yes(h.approvals == 1);
    try{StudioAiScriptPolicy.check(new JSONArray("[{\"op\":\"set\",\"path\":\"Workspace.X\",\"properties\":{\"Texture\":\"rbxassetid://99999999\"}}]"),ids,h);throw new AssertionError();}catch(StudioAiAgent.Refusal expected){}
    yes(StudioAiScriptPolicy.reviewSignals("remote.OnServerEvent:Connect(function(player) return end)").length()==1);
    yes(StudioAiScriptPolicy.reviewSignals("remote.OnServerEvent:Connect(function(player) local now=os.clock() return end)").length()==0);

    yes(StudioAiRetry.provider(503, 0, 2, 16));
    yes(!StudioAiRetry.provider(503, 1, 2, 16));
    yes(!StudioAiRetry.provider(503, 0, 16, 16));
    for (int status : new int[] {400, 401, 403, 404, 408, 429, 500})
      yes(!StudioAiRetry.provider(status, 0, 2, 16));
  }

  static String hex(String value) {
    StringBuilder out = new StringBuilder();
    for (byte b : value.getBytes(java.nio.charset.StandardCharsets.UTF_8))
      out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
    return out.toString();
  }

  static void yes(boolean v) {
    if (!v) throw new AssertionError();
  }

  static JSONObject spec(String n, String p) throws Exception {
    return new JSONObject().put("name", n).put("parent", p).put("origin", new JSONArray("[0,0,0]"));
  }

  static void add(JSONArray list, String generator, JSONObject s) throws Exception {
    list.put(
        new JSONObject()
            .put("generator", generator)
            .put("spec", s)
            .put("operations", StudioProceduralCompiler.compile(generator, s)));
  }

  static JSONArray fixtures() throws Exception {
    JSONArray list = new JSONArray();
    add(
        list,
        "terrain",
        spec("Hills", "Workspace")
            .put("grid", 6)
            .put("cell", 8)
            .put("height", 24)
            .put("amplitude", 16)
            .put("seed", 91)
            .put("river_width", 12)
            .put("water_level", 16));
    add(
        list,
        "building",
        spec("House", "Workspace")
            .put("width", 24)
            .put("depth", 20)
            .put("height", 12)
            .put("wall", 1)
            .put("door_width", 6)
            .put("color", new JSONArray("[170,140,100]"))
            .put("material", "WoodPlanks"));
    add(
        list,
        "scatter",
        spec("Trees", "Workspace")
            .put("width", 80)
            .put("depth", 80)
            .put("count", 16)
            .put("spacing", 9)
            .put("height", 12)
            .put("seed", 120));
    add(
        list,
        "vfx",
        spec("Wind", "Workspace")
            .put(
                "layers",
                new JSONArray()
                    .put(
                        new JSONObject()
                            .put("name", "Ambient")
                            .put("class", "ParticleEmitter")
                            .put(
                                "properties",
                                new JSONObject()
                                    .put("Rate", 40)
                                    .put("Lifetime", "0.5,1")
                                    .put("Speed", "3,5")))
                    .put(
                        new JSONObject()
                            .put("name", "Glow")
                            .put("class", "PointLight")
                            .put(
                                "properties",
                                new JSONObject().put("Color", "60,120,255").put("Brightness", 1)))
                    .put(
                        new JSONObject()
                            .put("name", "Streak")
                            .put("class", "Beam")
                            .put("start", new JSONArray("[-1,0,0]"))
                            .put("end", new JSONArray("[1,0,0]"))
                            .put(
                                "properties",
                                new JSONObject().put("Width0", .2).put("Width1", .05)))));
    add(
        list,
        "ui",
        spec("Inventory", "StarterGui")
            .put("title", "Inventory")
            .put("columns", 2)
            .put("cell_height", 48)
            .put(
                "items",
                new JSONArray()
                    .put(new JSONObject().put("name", "Sword").put("text", "Sword"))
                    .put(
                        new JSONObject()
                            .put("name", "Close")
                            .put("text", "Close")
                            .put("on_activate", "script.Parent.Enabled = false"))));
    add(
        list,
        "animation",
        spec("Awaken", "Workspace")
            .put(
                "frames",
                new JSONArray()
                    .put(
                        new JSONObject()
                            .put("time", 0)
                            .put(
                                "poses",
                                new JSONArray()
                                    .put(
                                        new JSONObject()
                                            .put("bone", "HumanoidRootPart/Torso/Right Arm")
                                            .put("cframe", "0,0,0"))))
                    .put(
                        new JSONObject()
                            .put("time", .5)
                            .put(
                                "poses",
                                new JSONArray()
                                    .put(
                                        new JSONObject()
                                            .put("bone", "HumanoidRootPart/Torso/Right Arm")
                                            .put("cframe", "0,1,0"))))));
    add(
        list,
        "camera",
        spec("Intro", "StarterPlayer.StarterPlayerScripts")
            .put("autoplay", true)
            .put(
                "frames",
                new JSONArray()
                    .put(new JSONObject().put("time", 0).put("cframe", "0,10,30").put("fov", 60))
                    .put(new JSONObject().put("time", 2).put("cframe", "0,8,20").put("fov", 50))));
    add(
        list,
        "audio",
        spec("Ambience", "Workspace")
            .put(
                "sounds",
                new JSONArray()
                    .put(
                        new JSONObject()
                            .put("name", "Water")
                            .put("asset_id", "123456789")
                            .put("volume", .3)
                            .put("loop", true)
                            .put("min_distance", 5)
                            .put("max_distance", 80)
                            .put("playback_speed", 1))));
    return list;
  }

  public static void main(String[] args) throws Exception {
        yes(java.util.Arrays.equals(StudioAiPopupBounds.safePadding(0,0,720,1200,0,0,720,1128),new int[]{0,0,0,72}));
        yes(java.util.Arrays.equals(StudioAiPopupBounds.safePadding(0,0,1280,240,0,0,1280,240),new int[4]));
        yes(java.util.Arrays.equals(StudioAiPopupBounds.safePadding(0,0,1280,600,0,0,1280,240),new int[]{0,0,0,360}));
        yes(java.util.Arrays.equals(StudioAiPopupBounds.safePadding(0,36,720,1092,0,36,720,1128),new int[4]));
    if (args.length > 0 && args[0].equals("resources")) {
      JSONArray evidence = new JSONArray();
      for (String repo :
          new String[] {
            "Roblox/react-luau", "Roblox/testez", "dphfox/Fusion", "JohnnyMorganz/luau-lsp"
          }) {
        JSONObject r =
            StudioResourceHub.request("resource_inspect", new JSONObject().put("repository", repo));
        r.remove("license_text");
        r.remove("readme");
        evidence.put(r);
      }
      System.out.println(evidence.toString(2));
      return;
    }
    policies();
    JSONObject visual=new JSONObject("{\"name\":\"MercadoPersonalizado\",\"parent\":\"StarterGui\",\"theme\":{\"surface\":\"50,32,18\",\"accent\":\"118,77,30\",\"text\":\"255,245,220\"},\"elements\":[{\"class\":\"Frame\",\"name\":\"Panel\",\"properties\":{\"Size\":\"0.8,0,0.8,0\",\"Position\":\"0.1,0,0.1,0\"},\"children\":[{\"class\":\"TextButton\",\"name\":\"Close\",\"properties\":{\"Text\":\"Fechar\",\"Size\":\"1,0,0,48\"},\"on_activate\":\"script.Parent.Enabled = false\"}]}]}");
    JSONArray visualOps=StudioProceduralCompiler.compile("ui",visual);
    yes(visualOps.length()==4&&visualOps.toString().contains("Activated:Connect"));
    JSONObject other=new JSONObject(visual.toString());other.getJSONObject("theme").put("surface","8,30,70");
    yes(!visualOps.toString().equals(StudioProceduralCompiler.compile("ui",other).toString()));
    yes(StudioAiVisualBuilder.review(visual).has("scope"));
    JSONObject misplaced=new JSONObject(visual.toString());misplaced.getJSONArray("elements").put(new JSONObject().put("class","UICorner").put("name","WrongCorner"));
    boolean rejectedParent=false;try{StudioAiVisualBuilder.compile(misplaced);}catch(StudioAiAgent.Refusal expected){rejectedParent=expected.getMessage().contains("children");}yes(rejectedParent);
    JSONObject nested=new JSONObject(visual.toString());nested.getJSONArray("elements").getJSONObject(0).getJSONArray("children").put(new JSONObject().put("class","UICorner").put("name","PanelCorner"));
    yes(StudioAiVisualBuilder.compile(nested).toString().contains("StarterGui.MercadoPersonalizado.Panel"));

    JSONObject n =
        StudioAiToolProtocol.normalize(
            new JSONObject(
                "{\"action\":\"studio_action\",\"parameters\":{\"action\":\"inspect_instance\",\"parameters\":{\"path\":\"Workspace\"}}}"));
    yes(
        n.getString("action").equals("inspect_instance")
            && n.getString("path").equals("Workspace"));
    for (String bad :
        new String[] {
          "{\"action\":\"studio_action\"}",
          "{\"action\":\"project_save\",\"parameters\":{\"action\":\"publish_animation\"}}"
        }) {
      try {
        StudioAiToolProtocol.normalize(new JSONObject(bad));
        throw new AssertionError();
      } catch (StudioAiAgent.Refusal expected) {
      }
    }
    Random random = new Random(11);
    for (int i = 0; i < 10000; i++) {
      int l = random.nextInt(100),
          t = random.nextInt(80),
          r = l + 80 + random.nextInt(1800),
          b = t + 100 + random.nextInt(900);
      StudioAiPopupBounds box =
          StudioAiPopupBounds.place(
              l,
              t,
              r,
              b,
              l + random.nextInt(r - l),
              t + random.nextInt(b - t),
              b - 10,
              460,
              800,
              8,
              4,
              random.nextBoolean());
      yes(box.x >= l && box.y >= t && box.x + box.width <= r && box.y + box.height <= b);
    }
    StudioAiPopupBounds plus =
        StudioAiPopupBounds.place(0, 0, 1536, 691, 105, 590, 662, 420, 300, 8, 4, true);
    yes(plus.y + plus.height <= 586);
    JSONArray data = fixtures();
    for (int i = 0; i < data.length(); i++) {
      JSONObject f = data.getJSONObject(i);
      yes(
          f.getJSONArray("operations")
              .toString()
              .equals(
                  StudioProceduralCompiler.compile(
                          f.getString("generator"), f.getJSONObject("spec"))
                      .toString()));
    }
    JSONObject forest =
        new JSONObject(data.getJSONObject(2).getJSONObject("spec").toString()).put("seed", 121);
    yes(
        !data.getJSONObject(2)
            .getJSONArray("operations")
            .toString()
            .equals(StudioProceduralCompiler.compile("scatter", forest).toString()));
    JSONObject bad =
        spec("Crowded", "Workspace")
            .put("width", 8)
            .put("depth", 8)
            .put("count", 48)
            .put("spacing", 20)
            .put("height", 12)
            .put("seed", 1);
    try {
      StudioProceduralCompiler.compile("scatter", bad);
      throw new AssertionError();
    } catch (StudioAiAgent.Refusal expected) {
    }
    StudioDeveloperEngine engine = new StudioDeveloperEngine();
    try {
      engine.planning(
          new JSONObject(
              "{\"steps\":[{\"id\":\"a\",\"title\":\"A\",\"acceptance\":\"No"
                  + " errors\",\"depends_on\":[\"a\"]}]}"));
      throw new AssertionError();
    } catch (StudioAiAgent.Refusal expected) {
    }
    yes(engine.knowledge().search("Motor6D timing animação").contains("animation-direction"));
    engine
        .knowledge()
        .add("custom", "https://github.com/user/repo", "MIT", "custom reusable architecture");
    StudioAiAgent.Run run = new StudioAiAgent.Run(16, true, false, false, false, "example");
    StudioDeveloperEngine restored = new StudioDeveloperEngine();
    restored.restore(engine.experience(run));
    yes(restored.knowledge().search("custom").contains("https://github.com/user/repo"));
    int[] calls = {0};
    try {
      StudioResourceHub.request(
          "resource_inspect",
          new JSONObject().put("repository", "../etc"),
          p -> {
            calls[0]++;
            return new JSONObject();
          });
      throw new AssertionError();
    } catch (StudioAiAgent.Refusal expected) {
      yes(calls[0] == 0);
    }
    data.put(new JSONObject().put("generator","visual_ui").put("spec",visual).put("operations",visualOps));
    if (args.length > 0) Files.writeString(Path.of(args[0]), data.toString());
    System.out.println(
        "Eight procedural compilers, recovery, retrieval, memory, resource guards and 10000 popup"
            + " bounds passed.");
  }
}
