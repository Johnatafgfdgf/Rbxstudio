package com.rbxnative.androidport;

public final class StudioAiScriptResultTest {
    public static void main(String[] args) {
        expect("RBX_LUAU_SYNTAX:27", "linha 27");
        expect("RBX_LUAU_SYNTAX:0", "sintaxe Luau");
        expect("Ação recusada no RBX Studio.", "recusada no painel");
        expect("Could not find instance at path 'StarterPlayer.StarterPlayerScripts'", "ausente no Explorer");
        expect("O destino passou a existir. Escolha outro nome.", "nome proposto");
        expect("the editor is showing a running playtest", "Encerre o Play");
        String error = StudioAiScriptResult.refusal("Edit 1 - old_string 'sensitive source' not found in current content");
        if (error.contains("sensitive") || !error.contains("O script mudou"))
            throw new AssertionError(error);
        System.out.println("Script refusal distinguishes Luau, destination, consent and stale edits.");
    }

    private static void expect(String response, String fragment) {
        String error = StudioAiScriptResult.refusal(response);
        if (!error.contains(fragment)) throw new AssertionError(error);
    }
}
