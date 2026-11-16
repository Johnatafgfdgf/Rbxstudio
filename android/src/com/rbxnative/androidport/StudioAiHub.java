package com.rbxnative.androidport;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Real, opt-in model hub for RBX Studio. Only inference and model discovery
 * happen here: a model reply MUST NOT be interpreted as a command to edit,
 * run Lua, import assets, access local files or publish Roblox content.
 * Credentials are encrypted by StudioServices (Android Keystore) and never
 * included in project files, logs, model prompts or model listings.
 */
public final class StudioAiHub extends Activity {
    private static final String[] NAMES = {"OpenAI", "Gemini", "Claude", "OpenRouter"};
    private static final String[] IDS = {"openai", "gemini", "claude", "openrouter"};
    private static final String[] MODELS_URL = {
        "https://api.openai.com/v1/models",
        "https://generativelanguage.googleapis.com/v1beta/models",
        "https://api.anthropic.com/v1/models",
        "https://openrouter.ai/api/v1/models"
    };
    private static final String[] HELP = {
        "OpenAI: crie uma chave de API no painel oficial da plataforma OpenAI. A assinatura do ChatGPT não inclui automaticamente créditos para a API.",
        "Gemini: configure sua chave no Google AI Studio. A disponibilidade e os limites dependem da conta e do modelo.",
        "Claude: configure uma chave na Claude Console. O uso de API pode exigir créditos ou faturamento.",
        "OpenRouter: configure uma chave na plataforma OpenRouter. Os custos e limites variam por modelo e provedor."
    };
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final int MAX_HISTORY = 12;
    private static final String PREFS = "rbxstudio-ai-hub-v1";
    private final ExecutorService requests = Executors.newSingleThreadExecutor();
    private final List<String[]> history = new ArrayList<>();
    private int provider;
    private boolean busy;
    private String project = "";
    private String mcpPairing = "";
    private String projectSummary = "";
    private CheckBox includeProject;
    private long requestEpoch;
    private Spinner providerPicker, modelPicker;
    private EditText apiKey, prompt;
    private TextView status, conversation;
    private Button refresh, send, save;
    private final List<String> models = new ArrayList<>();

    public static void open(Activity activity, String project, String pairing) {
        activity.runOnUiThread(() -> {
            Intent intent = new Intent(activity, StudioAiHub.class);
            intent.putExtra("project_name", project);
            // In-app explicit intent; never write the private MCP token to disk/logs.
            intent.putExtra("mcp_pairing", pairing);
            activity.startActivity(intent);
        });
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(Color.rgb(10, 19, 33));
        getWindow().setNavigationBarColor(Color.rgb(10, 19, 33));
        project = getIntent().getStringExtra("project_name");
        if (project == null) project = "";
        mcpPairing = getIntent().getStringExtra("mcp_pairing");
        if (mcpPairing == null) mcpPairing = "";
        provider = Math.max(0, Math.min(IDS.length - 1,
                getSharedPreferences(PREFS, MODE_PRIVATE).getInt("provider", 0)));
        buildUi();
        providerPicker.setSelection(provider);
        readStatus();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable shape(int fill, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(dp(radius));
        return bg;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int size, boolean heading) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.rgb(228, 236, 250));
        view.setTextSize(size);
        if (heading) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setMinHeight(dp(48));
        b.setBackground(shape(Color.rgb(41, 86, 153), 13));
        return b;
    }

    private EditText field(String hint, boolean secret) {
        EditText result = new EditText(this);
        result.setSingleLine(secret);
        result.setTextColor(Color.WHITE);
        result.setHintTextColor(Color.rgb(161, 177, 200));
        result.setHint(hint);
        result.setTextSize(14);
        result.setPadding(dp(13), dp(8), dp(13), dp(8));
        result.setBackground(shape(Color.rgb(29, 46, 69), 12));
        if (secret) {
            result.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else {
            result.setInputType(InputType.TYPE_CLASS_TEXT |
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            result.setMinLines(2);
            result.setMaxLines(5);
        }
        return result;
    }

    private void add(LinearLayout layout, View view, int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(bottomMargin);
        layout.addView(view, params);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(10, 19, 33));
        LinearLayout root = column();
        root.setPadding(dp(18), dp(12), dp(18), dp(24));
        scroll.addView(root);
        setContentView(scroll);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("IA • RBX Studio", 21, true);
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button back = button("Voltar ao editor");
        back.setOnClickListener(v -> finish());
        header.addView(back);
        add(root, header, 8);
        TextView notice = text("Chat por API real • Nenhum comando é executado automaticamente", 12, false);
        notice.setTextColor(Color.rgb(166, 197, 229));
        add(root, notice, 14);

        add(root, text("Provedor", 14, true), 5);
        providerPicker = new Spinner(this);
        ArrayAdapter<String> providers = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, NAMES);
        providerPicker.setAdapter(providers);
        add(root, providerPicker, 10);
        status = text("Nenhuma conexão verificada", 12, false);
        add(root, status, 8);

        apiKey = field("Cole a API Key (não é guardada como texto simples)", true);
        add(root, apiKey, 7);
        save = button("Salvar chave com segurança");
        save.setOnClickListener(v -> saveKey());
        add(root, save, 7);
        refresh = button("Consultar modelos disponíveis • testar conexão");
        refresh.setOnClickListener(v -> refreshModels());
        add(root, refresh, 9);

        TextView cost = text("APIs podem cobrar por uso. O catálogo não garante preço, saldo ou gratuidade. Confira as condições do provedor antes de enviar mensagens.", 12, false);
        cost.setTextColor(Color.rgb(222, 194, 142));
        add(root, cost, 12);
        Button tutorial = button("Como conectar esta IA?");
        tutorial.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle("Conectar " + NAMES[provider])
                .setMessage(HELP[provider] +
                    "\n\n1. Obtenha uma chave pelo canal oficial do provedor, se você tiver permissão para usar a API.\n" +
                    "2. Cole a chave no campo e toque em Salvar.\n" +
                    "3. Toque em Consultar modelos e escolha um disponível.\n" +
                    "4. Digite sua mensagem e toque em Enviar.\n\n" +
                    "Não compartilhe a chave no chat. O RBX Studio não pode garantir custos ou limites de terceiros.")
                .setPositiveButton("Entendi", null).show());
        add(root, tutorial, 12);

        add(root, text("Modelo", 14, true), 4);
        modelPicker = new Spinner(this);
        modelPicker.setAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, new String[]{"Consulte os modelos primeiro"}));
        modelPicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                if (pos < models.size()) {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putString("model_" + IDS[provider], models.get(pos)).apply();
                }
            }
        });
        add(root, modelPicker, 11);

        add(root, text("Contexto do projeto (opcional)", 14, true), 4);
        Button inspectProject = button("Inspecionar Explorer real via MCP");
        inspectProject.setOnClickListener(v -> inspectProject());
        add(root, inspectProject, 5);
        includeProject = new CheckBox(this);
        includeProject.setText("Incluir a árvore carregada nas próximas mensagens");
        includeProject.setTextColor(Color.rgb(228, 236, 250));
        includeProject.setChecked(false);
        add(root, includeProject, 10);

        conversation = text("O histórico desta conversa permanece somente nesta sessão. O editor não será alterado pelo chat.", 13, false);
        conversation.setPadding(dp(12), dp(12), dp(12), dp(12));
        conversation.setBackground(shape(Color.rgb(22, 37, 59), 15));
        add(root, conversation, 10);
        prompt = field("Descreva o jogo, script, sistema ou ideia…", false);
        add(root, prompt, 8);
        send = button("Enviar mensagem ao modelo");
        send.setOnClickListener(v -> sendMessage());
        add(root, send, 12);
        providerPicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                provider = pos;
                requestEpoch++;
                models.clear();
                modelPicker.setAdapter(new ArrayAdapter<String>(StudioAiHub.this,
                        android.R.layout.simple_spinner_dropdown_item,
                        new String[]{"Consulte os modelos primeiro"}));
                history.clear();
                conversation.setText("Novo chat com " + NAMES[provider] +
                        ". Nenhuma alteração será aplicada automaticamente ao projeto.");
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt("provider", provider).apply();
                readStatus();
            }
        });
    }

    private void setBusy(boolean waiting, String message) {
        busy = waiting;
        refresh.setEnabled(!waiting);
        save.setEnabled(!waiting);
        send.setEnabled(!waiting);
        status.setText(message);
    }

    private String service(int p) {
        return "rbxstudio.ai.provider." + IDS[p];
    }

    private void readStatus() {
        try {
            String saved = readKey(provider);
            status.setText(saved == null ? "Sem chave de API salva para " + NAMES[provider]
                    : "Chave salva com Android Keystore • conexão ainda não testada");
        } catch (Exception problem) {
            status.setText("Não foi possível ler a credencial. Verifique as configurações.");
        }
    }

    private static String hex(byte[] bytes) {
        final char[] chars = "0123456789abcdef".toCharArray();
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(chars[(b >> 4) & 15]).append(chars[b & 15]);
        }
        return builder.toString();
    }

    private static byte[] unhex(String value) {
        if ((value.length() & 1) != 0) throw new IllegalArgumentException("Invalid credential");
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int x = Character.digit(value.charAt(2 * i), 16);
            int y = Character.digit(value.charAt(2 * i + 1), 16);
            if (x < 0 || y < 0) throw new IllegalArgumentException("Invalid credential");
            bytes[i] = (byte) ((x << 4) | y);
        }
        return bytes;
    }

    private String readKey(int p) throws Exception {
        String stored = StudioServices.credential(this, "read", service(p), "", "");
        if (stored == null) return null;
        int colon = stored.indexOf(':');
        if (colon < 0) throw new IllegalStateException("Invalid credential encoding");
        return new String(unhex(stored.substring(colon + 1)), StandardCharsets.UTF_8);
    }

    private void saveKey() {
        String secret = apiKey.getText().toString().trim();
        if (secret.isEmpty() || secret.length() > 4096 || secret.indexOf('\n') >= 0 ||
                secret.indexOf('\r') >= 0) {
            status.setText("Insira uma chave válida; ela não pode conter quebras de linha.");
            return;
        }
        try {
            StudioServices.credential(this, "write", service(provider), "studio",
                    hex(secret.getBytes(StandardCharsets.UTF_8)));
            apiKey.setText("");
            status.setText("Chave criptografada e salva. Consulte os modelos para testar.");
        } catch (Exception problem) {
            status.setText("Falha ao salvar a chave no Android Keystore.");
        }
    }

    private void refreshModels() {
        final int p = provider;
        final long epoch = ++requestEpoch;
        final String key;
        try { key = readKey(p); }
        catch (Exception ex) { status.setText("Erro lendo a chave do dispositivo."); return; }
        if (key == null || key.isEmpty()) {
            status.setText("Salve uma chave antes de consultar os modelos.");
            return;
        }
        setBusy(true, "Consultando o catálogo oficial de " + NAMES[p] + "…");
        requests.execute(() -> {
            try {
                JSONObject result = request(p, key, MODELS_URL[p], null);
                JSONArray entries = result.optJSONArray(p == 1 ? "models" : "data");
                if (entries == null) throw new IOException("Catálogo sem lista de modelos");
                List<String> available = new ArrayList<>();
                for (int i = 0; i < entries.length() && available.size() < 400; i++) {
                    JSONObject item = entries.optJSONObject(i);
                    if (item == null) continue;
                    String id = item.optString(p == 1 ? "name" : "id", "");
                    if (p == 1) {
                        JSONArray actions = item.optJSONArray("supportedGenerationMethods");
                        boolean suitable = false;
                        if (actions != null) for (int j = 0; j < actions.length(); j++) {
                            if ("generateContent".equals(actions.optString(j))) suitable = true;
                        }
                        if (!suitable) continue;
                        if (id.startsWith("models/")) id = id.substring(7);
                    }
                    if (!id.isEmpty()) available.add(id);
                }
                final String selected = getSharedPreferences(PREFS, MODE_PRIVATE)
                        .getString("model_" + IDS[p], "");
                runOnUiThread(() -> {
                    if (epoch != requestEpoch || isFinishing()) return;
                    models.clear();
                    models.addAll(available);
                    modelPicker.setAdapter(new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_dropdown_item, available));
                    int index = available.indexOf(selected);
                    if (index >= 0) modelPicker.setSelection(index);
                    setBusy(false, available.isEmpty()
                            ? "Conexão aceita, mas sem modelos de geração nesta resposta."
                            : "Conexão com " + NAMES[p] + " confirmada • " + available.size() + " modelos");
                });
            } catch (Exception problem) {
                runOnUiThread(() -> {
                    if (epoch == requestEpoch && !isFinishing())
                        setBusy(false, safeError(problem));
                });
            }
        });
    }

    private void inspectProject() {
        final String paired = mcpPairing;
        if (paired.isEmpty()) {
            status.setText("Ative o servidor MCP no painel IA antes de abrir a central.");
            return;
        }
        if (busy) return;
        final long epoch = ++requestEpoch;
        setBusy(true, "Lendo somente a árvore real do Explorer…");
        requests.execute(() -> {
            try {
                JSONObject endpoint = new JSONObject(paired);
                String token = endpoint.getString("token");
                String studioId = endpoint.getString("studio_id");
                int port = endpoint.getInt("port");
                if (port < 1024 || port > 65535 || !token.matches("[0-9a-fA-F]{32}")
                        || !studioId.matches("[0-9a-fA-F]{16}")) {
                    throw new IOException("Endpoint local inválido");
                }
                JSONObject args = new JSONObject().put("datamodel_type", "Edit")
                        .put("studio_id", studioId).put("max_depth", 3)
                        .put("head_limit", 100);
                JSONObject call = new JSONObject().put("jsonrpc", "2.0")
                        .put("id", 1).put("method", "tools/call")
                        .put("params", new JSONObject().put("name", "search_game_tree")
                                .put("arguments", args));
                String reply;
                try (Socket socket = new Socket(InetAddress.getByName("127.0.0.1"), port)) {
                    socket.setSoTimeout(8000);
                    OutputStream out = socket.getOutputStream();
                    out.write((token + "\n" + call.toString() + "\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                            socket.getInputStream(), StandardCharsets.UTF_8));
                    reply = reader.readLine();
                }
                if (reply == null || reply.length() > 80000) throw new IOException("Resposta MCP inválida");
                JSONObject result = new JSONObject(reply).optJSONObject("result");
                if (result == null || result.optBoolean("isError")) {
                    throw new IOException("O editor recusou a leitura");
                }
                JSONArray content = result.optJSONArray("content");
                if (content == null || content.length() == 0) throw new IOException("Nenhum dado do Explorer");
                String tree = content.getJSONObject(0).optString("text", "");
                if (tree.isEmpty()) throw new IOException("Árvore sem dados");
                final String limited = tree.substring(0, Math.min(tree.length(), 12000));
                runOnUiThread(() -> {
                    if (epoch != requestEpoch || isFinishing()) return;
                    projectSummary = limited;
                    conversation.setText("Explorer consultado no RBX Studio (somente leitura):\n" +
                            limited + "\n\nMarque a opção acima para enviar esses dados ao modelo.");
                    setBusy(false, "Explorer consultado • a árvore não foi enviada ao provedor");
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (epoch == requestEpoch && !isFinishing()) setBusy(false,
                            "Não foi possível consultar o MCP; confirme que está ativo.");
                });
            }
        });
    }

    private void sendMessage() {
        final int p = provider;
        final String model = modelPicker.getSelectedItem() == null ? ""
                : modelPicker.getSelectedItem().toString();
        final String question = prompt.getText().toString().trim();
        if (!models.contains(model) || question.isEmpty()) {
            status.setText("Escolha um modelo conectado e escreva sua mensagem.");
            return;
        }
        if (question.length() > 6000) {
            status.setText("Mensagem muito longa. Limite local: 6.000 caracteres.");
            return;
        }
        final String key;
        try { key = readKey(p); }
        catch (Exception ex) { status.setText("Erro lendo a chave do dispositivo."); return; }
        if (key == null) { status.setText("Chave não cadastrada."); return; }
        final List<String[]> conversation = new ArrayList<>(history);
        final boolean include = includeProject.isChecked() && !projectSummary.isEmpty();
        final String message = include
                ? question + "\n\nContexto somente leitura autorizado pelo usuário:\n" + projectSummary
                : question;
        conversation.add(new String[]{"user", message});
        final long epoch = ++requestEpoch;
        setBusy(true, "Consultando " + model + "…");
        requests.execute(() -> {
            try {
                JSONObject result = request(p, key, messageUrl(p, model),
                        makeBody(p, model, conversation));
                String answer = extractText(p, result);
                if (answer.isEmpty()) throw new IOException("Modelo respondeu sem texto");
                final String response = answer.length() > 16000
                        ? answer.substring(0, 16000) + "\n[Resposta truncada para o celular]"
                        : answer;
                final String usage = usageSummary(p, result);
                runOnUiThread(() -> {
                    if (epoch != requestEpoch || isFinishing()) return;
                    history.add(new String[]{"user", question});
                    history.add(new String[]{"assistant", response});
                    while (history.size() > MAX_HISTORY) history.remove(0);
                    StringBuilder resultText = new StringBuilder();
                    for (String[] turn : history) {
                        resultText.append(turn[0].equals("user") ? "Você:\n" : "IA:\n")
                                .append(turn[1]).append("\n\n");
                    }
                    conversationDisplay(resultText.toString());
                    prompt.setText("");
                    setBusy(false, "Resposta recebida de " + model + usage);
                });
            } catch (Exception problem) {
                runOnUiThread(() -> {
                    if (epoch == requestEpoch && !isFinishing())
                        setBusy(false, safeError(problem));
                });
            }
        });
    }

    private void conversationDisplay(String value) {
        conversation.setText(value);
    }

    private static String messageUrl(int p, String model) {
        switch (p) {
            case 0: return "https://api.openai.com/v1/responses";
            case 1: return "https://generativelanguage.googleapis.com/v1beta/models/" +
                    java.net.URLEncoder.encode(model, StandardCharsets.UTF_8) + ":generateContent";
            case 2: return "https://api.anthropic.com/v1/messages";
            default: return "https://openrouter.ai/api/v1/chat/completions";
        }
    }

    private static JSONObject makeBody(int p, String model, List<String[]> turns) throws Exception {
        JSONObject body = new JSONObject();
        if (p != 1) body.put("model", model);
        if (p == 2) body.put("max_tokens", 1024);
        JSONArray messages = new JSONArray();
        for (String[] turn : turns) {
            String role = (p == 1 && turn[0].equals("assistant")) ? "model" : turn[0];
            JSONObject message = new JSONObject().put("role", role);
            if (p == 1) message.put("parts", new JSONArray()
                    .put(new JSONObject().put("text", turn[1])));
            else message.put("content", turn[1]);
            messages.put(message);
        }
        body.put(p == 0 ? "input" : p == 1 ? "contents" : "messages", messages);
        return body;
    }

    private static String extractText(int p, JSONObject result) throws Exception {
        if (p == 0) {
            JSONArray output = result.optJSONArray("output");
            if (output == null) return result.optString("output_text", "");
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < output.length(); i++) {
                JSONObject item = output.optJSONObject(i);
                if (item == null) continue;
                JSONArray parts = item.optJSONArray("content");
                if (parts == null) continue;
                for (int j = 0; j < parts.length(); j++) {
                    JSONObject part = parts.optJSONObject(j);
                    if (part != null && "output_text".equals(part.optString("type"))) {
                        content.append(part.optString("text"));
                    }
                }
            }
            return content.toString();
        } else if (p == 1) {
            JSONArray candidates = result.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) return "";
            JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
            if (content == null) return "";
            JSONArray parts = content.optJSONArray("parts");
            StringBuilder output = new StringBuilder();
            if (parts != null) for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.optJSONObject(i);
                if (part != null) output.append(part.optString("text", ""));
            }
            return output.toString();
        } else if (p == 2) {
            JSONArray parts = result.optJSONArray("content");
            StringBuilder output = new StringBuilder();
            if (parts != null) for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.optJSONObject(i);
                if (part != null && "text".equals(part.optString("type"))) output.append(part.optString("text"));
            }
            return output.toString();
        }
        JSONArray choices = result.optJSONArray("choices");
        if (choices == null || choices.length() == 0) return "";
        JSONObject message = choices.getJSONObject(0).optJSONObject("message");
        return message == null ? "" : message.optString("content", "");
    }

    private static String usageSummary(int p, JSONObject response) {
        JSONObject usage = response.optJSONObject("usage");
        if (usage == null) return "";
        int input = usage.optInt(p == 0 ? "input_tokens" : p == 2 ? "input_tokens"
                : p == 1 ? "promptTokenCount" : "prompt_tokens", -1);
        int output = usage.optInt(p == 0 ? "output_tokens" : p == 2 ? "output_tokens"
                : p == 1 ? "candidatesTokenCount" : "completion_tokens", -1);
        if (p == 1) usage = response.optJSONObject("usageMetadata") == null
                ? usage : response.optJSONObject("usageMetadata");
        if (p == 1) {
            input = usage.optInt("promptTokenCount", -1);
            output = usage.optInt("candidatesTokenCount", -1);
        }
        return input >= 0 && output >= 0
                ? " • " + input + " tokens de entrada, " + output + " de saída"
                : " • uso não informado pela API";
    }

    private static JSONObject request(int p, String secret, String endpoint,
            JSONObject body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(30000);
            conn.setUseCaches(false);
            conn.setInstanceFollowRedirects(false); // Never forward API keys to redirects.
            conn.setRequestProperty("Accept", "application/json");
            if (p == 1) conn.setRequestProperty("x-goog-api-key", secret);
            else if (p == 2) {
                conn.setRequestProperty("x-api-key", secret);
                conn.setRequestProperty("anthropic-version", "2023-06-01");
            } else conn.setRequestProperty("Authorization", "Bearer " + secret);
            if (body != null) {
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                if (payload.length > 128 * 1024) throw new IOException("Request too large");
                try (OutputStream out = conn.getOutputStream()) { out.write(payload); }
            }
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                if (code == 401 || code == 403) throw new IOException("HTTP " + code +
                        ": autenticação recusada, verifique a chave e as permissões.");
                if (code == 429) throw new IOException("HTTP 429: limite de requisições ou créditos atingido.");
                throw new IOException("HTTP " + code + ": o provedor recusou a solicitação.");
            }
            try (InputStream input = conn.getInputStream();
                 ByteArrayOutputStream data = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytes;
                while ((bytes = input.read(buffer)) >= 0) {
                    if (data.size() + bytes > MAX_RESPONSE_BYTES) throw new IOException("Resposta muito grande.");
                    data.write(buffer, 0, bytes);
                }
                return new JSONObject(data.toString(StandardCharsets.UTF_8.name()));
            }
        } finally { conn.disconnect(); }
    }

    private static String safeError(Exception e) {
        if (e instanceof IOException) {
            String message = e.getMessage();
            if (message != null && message.startsWith("HTTP ")) return message;
        }
        return "Falha de rede ou resposta incompatível. Confira a conexão e o modelo.";
    }

    @Override protected void onDestroy() {
        requestEpoch++;
        requests.shutdownNow();
        super.onDestroy();
    }
}
