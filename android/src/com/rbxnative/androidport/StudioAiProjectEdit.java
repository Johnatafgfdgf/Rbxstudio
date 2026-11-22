package com.rbxnative.androidport;

import java.io.IOException;
import org.json.JSONArray;
import org.json.JSONObject;

/** The model proposes structured changes. Only the local editor validates and commits them. */
final class StudioAiProjectEdit {
    static final String SCHEMA = "android_editor_schema";
    static final String PREVIEW = "android_preview_project_edit";
    static final String CONTEXT = "android_project_context";
    static final String SAVE = "android_project_save";
    static final String SELECT = "android_project_select";
    static final String DIAGNOSTICS = "android_project_diagnostics";
    static final String HISTORY = "android_project_history";
    static final String APPLY = "android_apply_project_edit";
    static final String PROTOCOL =
            "\nFERRAMENTAS PARA EDITAR O PROJETO: editor_schema recebe class e devolve propriedades reais. " +
            "project_edit recebe operations com op, parent/path e os campos tipados do catálogo. " +
            "Escolha nomes e estrutura para o pedido atual, sem reutilizar nomes de exemplos. " +
            "Agrupe até 32 operações em ordem de dependência. Prefira esse lote para criar sistemas " +
            "com várias instâncias, ajustar propriedades, montar GUIs, física, efeitos e animações " +
            "com Animation, KeyframeSequence, Keyframe e Pose. Use set com path e properties. " +
            "Valores de Properties usam números, booleanos ou o texto do painel: RGB 0–255, " +
            "vetores separados por vírgulas, UDim2 em escalaX, offsetX, escalaY, offsetY. " +
            "Use CFrame com 3 componentes para posição ou 12 para posição e matriz. " +
            "Enum pelo nome do item. Referência: {\"ref\":\"Workspace.Model.Part\"} ou null para limpar. " +
            "Use instance_id somente quando retornado por uma inspeção REAL da sessão. Nunca " +
            "adivinhe o ID de um objeto novo nem incremente IDs após erro: previews não criam " +
            "objetos no mapa. Dentro de um lote, referencie os objetos criados antes pelo " +
            "caminho parent + nome, não por IDs numéricos previstos. " +
            "Para nomes existentes com pontos, envie caminhos como listas de segmentos. " +
            "attribute usa path, name, value e type opcional como Vector3 ou Color3. " +
            "tag usa path, name e enabled booleano. " +
            "script_replace usa path, old (código completo anterior) e source (novo código completo). " +
            "rename usa path e name, move usa path e parent, duplicate usa path, parent e name, " +
            "delete usa path. Se faltar um container padrão como StarterPlayerScripts, " +
            "use ensure_service com class. Para buscar recursos gratuitos no catálogo Roblox, " +
            "use search_toolbox com query escolhida para a necessidade atual e type do catálogo, " +
            "apenas quando o pedido envolver assets. Modelos externos continuam com importação revisada. " +
            "Terreno: {\"op\":\"terrain\",\"center\":[0,16,0],\"size\":[64,16,64],\"material\":\"Grass\"}; " +
            "até uma região por lote, entre 4 e 192 studs por eixo, replace pode indicar o material de origem. " +
            "Se a edição de scripts estiver autorizada, create aceita source com Luau puro, " +
            "e set aceita properties.Source (S maiúsculo) com o código completo. Source é editável " +
            "por essas ferramentas mesmo sendo separado do painel Properties. Leia scripts vazios " +
            "como texto vazio e escreva a lógica neles; StringValue não executa código. " +
            "e edit_script aceita path, old (trecho literal único, leia antes) e new. " +
            "Use caminhos de mapa reais e confira containers antes de os usar. " +
            "O editor valida o lote e o aplica de uma vez de acordo com as permissões. " +
            "Exclusões e remoção de terreno serão revisadas pelo usuário. " +
            "Nunca declare execução em Play ou publicação baseada em uma edição do projeto.";

    final JSONArray operations;
    final String revision;
    final String details;
    final boolean review;
    boolean highImpact=true;
    final int count;
    int appliedCount;

    private StudioAiProjectEdit(JSONArray operations, String revision, String details, boolean review) {
        this.operations = operations;
        this.revision = revision;
        this.details = details;
        this.review = review;
        this.count = operations.length();
    }

    static JSONArray operations(JSONObject decision) throws IOException {
        JSONArray list = decision.optJSONArray("operations");
        if (list == null || list.length() < 1 || list.length() > 32 ||
                list.toString().length() > 48000) {
            throw new StudioAiAgent.Refusal("Envie de 1 a 32 operações e até 48 mil caracteres.");
        }
        try {
            JSONArray normalized=new JSONArray(list.toString());
            for(int i=0;i<normalized.length();i++){
                JSONObject op=normalized.getJSONObject(i),props=op.optJSONObject("properties");
                if(props!=null&&props.has("source")){
                    Object lower=props.get("source");
                    if(!(lower instanceof String))throw new StudioAiAgent.Refusal("Source deve ser uma string Luau.");
                    if(props.has("Source")&&!lower.equals(props.get("Source")))throw new StudioAiAgent.Refusal("Source e source são diferentes.");
                    props.put("Source",lower);props.remove("source");
                }
                if("set".equals(op.optString("op"))&&op.has("source")){
                    Object code=op.get("source");if(!(code instanceof String))throw new StudioAiAgent.Refusal("source deve ser uma string Luau.");
                    if(props==null){props=new JSONObject();op.put("properties",props);}
                    if(props.has("Source")&&!code.equals(props.get("Source")))throw new StudioAiAgent.Refusal("Source e source são diferentes.");
                    props.put("Source",code);op.remove("source");
                }
                if("create".equals(op.optString("op"))&&java.util.Arrays.asList("Script","LocalScript","ModuleScript").contains(op.optString("class"))
                    &&!op.has("source")&&(props==null||!props.has("Source")))op.put("source","");
            }
            return normalized;
        }catch(org.json.JSONException invalid){throw new StudioAiAgent.Refusal("Operações precisam ser objetos JSON tipados.");}
    }

    static StudioAiProjectEdit checkedPreview(JSONArray operations, String response) throws Exception {
        JSONObject preview = new JSONObject(response);
        String revision = preview.optString("revision");
        if (!revision.matches("[0-9]{1,20}:[0-9]{1,20}") || !(preview.opt("review") instanceof Boolean)) {
            throw new IOException("Prévia inválida do editor.");
        }
        StudioAiProjectEdit result=new StudioAiProjectEdit(operations, revision,
                report(preview, operations.length()), preview.getBoolean("review"));
        result.highImpact=preview.optBoolean("high_impact",true);
        return result;
    }

    /** The native editing endpoint supports Source specially, separately from
     * the generic Properties panel. Expose that existing capability accurately. */
    static String toolSchema(String response)throws Exception {
        JSONObject schema=new JSONObject(response);
        if(java.util.Arrays.asList("Script","LocalScript","ModuleScript").contains(schema.optString("class"))){
            JSONObject fields=schema.optJSONObject("properties");if(fields!=null)fields.put("Source","string");
            schema.put("script_source",new JSONObject().put("requires_permission","allow_scripts").put("max_bytes",16000)
                .put("create_field","source").put("set_field","properties.Source").put("empty_source_is_valid",true));
        }
        return schema.toString();
    }

    JSONObject arguments(boolean scriptsAllowed, boolean confirmDelete) throws Exception {
        return new JSONObject().put("operations", operations).put("revision", revision)
                .put("allow_scripts", scriptsAllowed).put("confirm_delete", confirmDelete);
    }

    String checkedReceipt(String response) throws Exception {
        JSONObject value = new JSONObject(response);
        if (!Boolean.TRUE.equals(value.opt("applied")) || !(value.opt("changed") instanceof Boolean)
                || !details.equals(report(value, count)))
            throw new IOException("O editor devolveu um resultado diferente da prévia.");
        appliedCount=value.getBoolean("changed")?count:0;
        return appliedCount>0
                ? (count == 1 ? "1 operação aplicada no mapa." : count + " operações aplicadas no mapa.") +
                    "\n" + details
                : "O mapa já estava com os valores solicitados.";
    }

    private static String report(JSONObject value, int count) throws Exception {
        JSONArray lines = value.getJSONArray("operations");
        if (lines.length() != count) throw new IOException("Relatório de edição incompleto.");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) {
            Object line = lines.get(i);
            if (!(line instanceof String)) throw new IOException("Operação inválida no relatório.");
            if (i > 0) result.append('\n');
            result.append(line);
        }
        return result.toString();
    }
}
