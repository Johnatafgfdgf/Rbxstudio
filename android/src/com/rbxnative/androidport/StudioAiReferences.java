package com.rbxnative.androidport;

import android.app.Activity;
import android.app.AlertDialog;
import java.util.Locale;
import java.util.function.Consumer;

/** Bundled summaries and trusted links, independent of API and MCP availability. */
final class StudioAiReferences {
    private static final String[][] TOPICS = {
            {"Luau e APIs Roblox", "https://create.roblox.com/docs/luau",
                    "Escolha Script, LocalScript ou ModuleScript conforme a execução. Valide a API na referência da classe.",
                    "luau", "script", "api"},
            {"Interfaces e menus", "https://create.roblox.com/docs/building-and-visuals/ui",
                    "Use ScreenGui e layouts que se adaptem à tela. Teste o toque e a área segura em telas pequenas.",
                    "interface", "gui", "menu", "invent"},
            {"Modelos de interface em projetos oficiais", "https://github.com/Roblox/resources",
                    "Consulte exemplos e templates do Creator Hub. Para HUD, use ScreenGui no StarterGui, "
                            + "AnchorPoint, escala e UIListLayout. Preserve áreas seguras e valide a interação por toque.",
                    "interface", "gui", "hud", "menu", "invent"},
            {"Fusion", "https://github.com/dphfox/Fusion",
                    "Referência de interfaces reativas em Luau. Use a biblioteca se ela já estiver instalada no mapa.",
                    "fusion", "reativ"},
            {"Construção e terreno", "https://create.roblox.com/docs/parts/terrain",
                    "Inspecione escala, colisões e pivôs antes de editar grandes áreas. Terrain e Parts têm operações diferentes.",
                    "terreno", "terrain", "mapa", "cenário", "model"},
            {"Animação e rigs", "https://create.roblox.com/docs/animation",
                    "Confira o rig, o Animator e as permissões do asset. Use somente IDs confirmados.",
                    "anim", "rig", "cinem"},
            {"Física e constraints", "https://create.roblox.com/docs/physics",
                    "Valide assemblies, attachments, colisões e controle no cliente e no servidor.",
                    "física", "physics", "npc", "veículo", "constraint"},
            {"Rede, validação e desempenho", "https://create.roblox.com/docs/onboarding/developers",
                    "Valide no servidor dados e frequência de remotes. Meça o custo de execução e replicação antes de otimizar.",
                    "rede", "segurança", "remote", "fps", "otimi", "teste", "combate"},
            {"Projetos e bibliotecas oficiais", "https://github.com/Roblox/resources",
                    "Consulte exemplos do Creator Hub e verifique dependências e licença antes de usar código.",
                    "biblioteca", "referência", "exemplo"},
            {"React Lua", "https://github.com/Roblox/react-luau",
                    "Opção MIT para interfaces declarativas em Luau. Use quando o projeto já comportar a dependência.",
                    "react"},
            {"Luau Language Server", "https://github.com/JohnnyMorganz/luau-lsp",
                    "Ferramenta externa de análise e autocompletar para Luau com integração ao Rojo.",
                    "lsp", "autocomplete"},
            {"TestEZ", "https://github.com/Roblox/testez",
                    "Biblioteca de testes Luau. Confirme as APIs disponíveis no runtime escolhido.",
                    "testez"}
    };

    private StudioAiReferences() {}

    static void show(Activity activity, Consumer<String> open) {
        String[] labels = new String[TOPICS.length];
        for (int i = 0; i < TOPICS.length; i++) labels[i] = TOPICS[i][0];
        new AlertDialog.Builder(activity).setTitle("Referências Roblox")
                .setItems(labels, (list, index) -> {
                    String[] topic = TOPICS[index];
                    new AlertDialog.Builder(activity).setTitle(topic[0]).setMessage(topic[2])
                            .setNegativeButton("Fechar", null)
                            .setPositiveButton("Abrir referência", (dialog, which) -> open.accept(topic[1]))
                            .show();
                }).setNegativeButton("Fechar", null).show();
    }

    static String guidance(String question) {
        String normalized = question.toLowerCase(Locale.ROOT);
        StringBuilder selected = new StringBuilder();
        for (String[] topic : TOPICS) {
            for (int i = 3; i < topic.length; i++) {
                if (normalized.contains(topic[i])) {
                    selected.append("\n- ").append(topic[2]);
                    break;
                }
            }
        }
        return selected.length() == 0 ? "" :
                "\n\nOrientações locais para esta tarefa:" + selected;
    }
}
