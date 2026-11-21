package com.rbxnative.androidport;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class StudioAiScriptResult {
    private static final Pattern SYNTAX_LINE = Pattern.compile("RBX_LUAU_SYNTAX:([0-9]{1,7})");

    private StudioAiScriptResult() {}

    static String refusal(String text) {
        Matcher syntax = SYNTAX_LINE.matcher(text);
        if (syntax.matches()) {
            int line = Integer.parseInt(syntax.group(1));
            return line == 0 ? "O script contém erro de sintaxe Luau."
                    : "O Luau não compilou na linha " + line + ".";
        }
        if (text.equals("Ação recusada no RBX Studio.")) return "A solicitação foi recusada no painel do editor.";
        if (text.startsWith("nothing in the place is at ") ||
                text.startsWith("Could not find instance at path ") ||
                text.startsWith("Script not found at path: "))
            return "O caminho do script ou a pasta de destino está ausente no Explorer.";
        if (text.equals("O destino passou a existir. Escolha outro nome."))
            return "Já existe um objeto com o nome proposto. Escolha outro nome.";
        if (text.startsWith("Há mais de uma pasta") || text.startsWith("Há mais de um objeto"))
            return "Há nomes duplicados nesse caminho do Explorer.";
        if (text.startsWith("the editor is showing a running playtest"))
            return "Encerre o Play ou volte para o mapa em edição antes de gravar.";
        if (text.startsWith("Edit ") && text.endsWith("not found in current content"))
            return "O script mudou. Gere uma nova proposta para o conteúdo atual.";
        if (text.startsWith("Edit ") && text.contains("times; give more of the surrounding text"))
            return "O trecho que seria substituído aparece mais de uma vez. Gere uma proposta com mais contexto.";
        if (text.equals("A proposta não altera o script.") || text.endsWith("identical old_string and new_string"))
            return "A proposta não altera o script.";
        if (text.endsWith(" is not a script")) return "O objeto no caminho selecionado não é um script.";
        return "O editor recusou a proposta. Confira o destino e o código.";
    }
}
