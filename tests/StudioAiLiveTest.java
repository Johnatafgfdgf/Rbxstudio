package com.rbxnative.androidport;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** Opt-in live provider + production headless DOM test; no renderer, Android MCP or runtime. */
public final class StudioAiLiveTest implements StudioAiAgent.Host, AutoCloseable {
  private final String secret, model;
  private final Process bridge;
  private final BufferedWriter input;
  private final BufferedReader output;
  private StudioAiToolProtocol wire = new StudioAiToolProtocol();
  private int requests, inTokens, outTokens, retries,rateRetries;
  private boolean plainTools;
  private final JSONArray trace = new JSONArray();

  StudioAiLiveTest(String secret, String model, String file) throws Exception {
    this.secret = secret;
    this.model = model;
    ProcessBuilder p =
        new ProcessBuilder("python3", "scripts/test_ai_project_native.py", "--bridge");
    p.environment().put("RBX_LIVE_PLACE", file);
    p.environment().put("CARGO_NET_OFFLINE", "true");
    p.redirectError(ProcessBuilder.Redirect.INHERIT);
    bridge = p.start();
    input =
        new BufferedWriter(
            new OutputStreamWriter(bridge.getOutputStream(), StandardCharsets.UTF_8));
    output =
        new BufferedReader(new InputStreamReader(bridge.getInputStream(), StandardCharsets.UTF_8));
  }

  public String model(List<String[]> messages, int budget) throws Exception {
    if (requests >= Integer.parseInt(System.getenv().getOrDefault("RBX_LIVE_STOP_AFTER_REQUESTS","999")))throw new IOException("Interrupção controlada do teste de retomada");
    if (requests >= budget) throw new IOException("Live test request budget reached");
    long pacing=Long.parseLong(System.getenv().getOrDefault("RBX_LIVE_PACING_MS","6000"));
    if(requests>0)Thread.sleep(Math.max(0,Math.min(pacing,10000)));
    requests++;
    if (messages.get(messages.size() - 1)[1].startsWith("Resultado real"))
      trace.put(new JSONObject().put("tool_feedback", messages.get(messages.size() - 1)[1]));
    JSONArray contents = new JSONArray();
    for (String[] m : messages)
      contents.put(
          new JSONObject()
              .put("role", m[0].equals("assistant") ? "model" : "user")
              .put("parts", new JSONArray().put(new JSONObject().put("text", m[1]))));
    JSONObject body =
        new JSONObject()
            .put("contents", contents)
            .put("generationConfig", new JSONObject().put("maxOutputTokens", 6000));
    StudioAiConversation.configure(1, body);
    if(!plainTools)wire.configure(1, body);
    HttpURLConnection c =
        (HttpURLConnection)
            new URL(
                    "https://generativelanguage.googleapis.com/v1beta/models/"
                        + model
                        + ":generateContent")
                .openConnection();
    JSONObject response;
    try {
      c.setConnectTimeout(12000);
      c.setReadTimeout(90000);
      c.setInstanceFollowRedirects(false);
      c.setRequestMethod("POST");
      c.setDoOutput(true);
      c.setRequestProperty("x-goog-api-key", secret);
      c.setRequestProperty("Content-Type", "application/json");
      try (OutputStream out = c.getOutputStream()) {
        out.write(body.toString().getBytes(StandardCharsets.UTF_8));
      }
      int status = c.getResponseCode();
      if (status != 200) {
        if (StudioAiRetry.provider(status, retries, requests, budget)) {
          retries++;
          Thread.sleep(600);
          return model(messages, budget);
        }
        String e = new String(c.getErrorStream().readNBytes(16000), StandardCharsets.UTF_8).replace(secret,"[redacted]");
        if(status==429&&rateRetries<2&&requests<budget){
          long wait=StudioAiUsage.retryDelay(c.getHeaderFields(),new JSONObject(e),System.currentTimeMillis());
          if(wait>0){rateRetries++;Thread.sleep(wait);return model(messages,budget);}
        }
        throw new IOException(
            "Provider HTTP " + status + ": " + e.substring(0, Math.min(1000, e.length())));
      }
      try (InputStream in = c.getInputStream()) {
        response = new JSONObject(new String(in.readNBytes(1024 * 1024), StandardCharsets.UTF_8));
      }
    } finally {
      c.disconnect();
    }
    JSONObject usage = response.optJSONObject("usageMetadata");
    if (usage != null) {
      inTokens += usage.optInt("promptTokenCount");
      outTokens += usage.optInt("candidatesTokenCount");
    }
    if(!plainTools&&StudioAiToolProtocol.needsPlainFallback(1,response)&&requests<budget){
      plainTools=true;wire=new StudioAiToolProtocol();trace.put(new JSONObject().put("format_fallback",true));return model(messages,budget);
    }
    String result = plainTools?null:wire.extract(1, response, messages.size());
    if (result == null) {
      StringBuilder text = new StringBuilder();
      JSONArray parts =
          response
              .getJSONArray("candidates")
              .getJSONObject(0)
              .getJSONObject("content")
              .getJSONArray("parts");
      for (int i = 0; i < parts.length(); i++)
        text.append(parts.getJSONObject(i).optString("text"));
      result = text.toString();
    }
    trace.put(new JSONObject().put("provider_action", new JSONObject(result)));
    return result;
  }

  public String call(String tool, JSONObject args) throws Exception {
    input.write(new JSONObject().put("tool", tool).put("arguments", args).toString());
    input.newLine();
    input.flush();
    String raw = output.readLine();
    if (raw == null) throw new IOException("Headless bridge closed");
    JSONObject result = new JSONObject(raw);
    if (result.has("error")) throw new StudioAiAgent.Refusal(result.getString("error"));
    trace.put(new JSONObject().put("editor_tool", tool).put("result", result.get("result")));
    Object v = result.get("result");
    return v instanceof String ? (String) v : v.toString();
  }

  public boolean approve(String c, String d) {
    return false;
  }

  public void check() throws IOException {
    if (!bridge.isAlive()) throw new IOException("Bridge exited");
  }

  @Override public void resetConversation() { wire=new StudioAiToolProtocol(); }
  public void progress(String s) {
    System.err.println(s);
  }

  public void writing(boolean active) {}

  @Override public void resetConversation() { wire=new StudioAiToolProtocol(); }
  public String loadTask()throws Exception {String path=System.getenv("RBX_LIVE_TASK_STATE");return path!=null&&Files.exists(Path.of(path))?Files.readString(Path.of(path)):"";}
  public void saveTask(String data)throws Exception {String path=System.getenv("RBX_LIVE_TASK_STATE");if(path!=null)Files.writeString(Path.of(path),data);}

  public void capture(List<String[]> m) throws IOException {
    throw new IOException("No renderer in headless test");
  }

  public void close() throws Exception {
    input.close();
    bridge.waitFor();
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 5)
      throw new IllegalArgumentException("key-file model goal-file map-output report-output");
    String key = Files.readString(Path.of(args[0])).trim(),
        goal = Files.readString(Path.of(args[2]));
    try (StudioAiLiveTest h =
        new StudioAiLiveTest(key, args[1], Path.of(args[3]).toAbsolutePath().toString())) {
      List<String[]> messages = new ArrayList<>();
      messages.add(new String[] {"user", goal});
      StudioAiAgent.Run run = new StudioAiAgent.Run(Integer.parseInt(System.getenv().getOrDefault("RBX_LIVE_BUDGET","24")), true, false, false, false, goal);
      run.resume=Boolean.parseBoolean(System.getenv().getOrDefault("RBX_LIVE_RESUME","false"));
      String answer = "", error = "";
      try {
        answer = StudioAiAgent.run(h, run, messages);
      } catch (Exception e) {
        error = e.getMessage();
      }
      JSONObject report =
          new JSONObject()
              .put("model", args[1])
              .put("requests", h.requests)
              .put("input_tokens", h.inTokens)
              .put("output_tokens", h.outTokens)
              .put("confirmed_operations", run.operations)
              .put("saved", run.saved).put("completed",run.completed)
              .put("write_pending", run.writePending)
              .put("error", error)
              .put("answer", answer)
              .put("trace", h.trace)
              .put(
                  "scope",
                  "Live Gemini plus production DOM/reflection/Luau compiler and binary save/reopen."
                      + " Android UI, MCP socket, rendering and Play untested.");
      Files.writeString(Path.of(args[4]), report.toString(2));
      System.out.println(
          new JSONObject()
              .put("model", args[1])
              .put("requests", h.requests)
              .put("operations", run.operations)
              .put("saved", run.saved).put("completed",run.completed)
              .put("error", error));
      if (!error.isEmpty() || run.operations == 0 || !run.saved || !run.completed)
        throw new AssertionError("Live task incomplete; inspect report");
    }
  }
}
