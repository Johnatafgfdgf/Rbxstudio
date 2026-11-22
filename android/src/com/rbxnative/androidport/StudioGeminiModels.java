package com.rbxnative.androidport;

import java.util.Collection;

final class StudioGeminiModels {
    private StudioGeminiModels() {}

    static String id(String name) {
        String id = name.startsWith("models/") ? name.substring(7) : "";
        return id.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,179}") ? id : "";
    }

    static int priority(String id) {
        if (id.equals("gemini-3.5-flash-lite")) return 0;
        if (id.equals("gemini-3.8-flash")) return 1;
        if (id.startsWith("gemini-2.5")) return 5;
        if (id.contains("preview") || id.contains("experimental") || id.endsWith("-latest")) return 4;
        return id.startsWith("gemini-") ? 2 : 6;
    }

    static String restored(String saved, String previous, Collection<String> liveModels) {
        if (liveModels.contains(saved)) return saved;
        // Older builds persisted the first catalog entry even without a user selection.
        if (priority(previous) >= 4 && liveModels.stream().anyMatch(id -> priority(id) <= 1))
            return "";
        return liveModels.contains(previous) ? previous : "";
    }

    static String generationUrl(String id) {
        if (id.isEmpty() || !id.equals(id("models/" + id)))
            throw new IllegalArgumentException("Identificador Gemini inválido");
        return "https://generativelanguage.googleapis.com/v1beta/models/" + id + ":generateContent";
    }
}
