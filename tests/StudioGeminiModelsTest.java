package com.rbxnative.androidport;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class StudioGeminiModelsTest {
    public static void main(String[] args) {
        String lite = "gemini-3.5-flash-lite";
        String flash = "gemini-3.8-flash";
        String legacy = "gemini-2.5-flash";
        List<String> catalog = Arrays.asList(lite, flash, legacy);
        check(StudioGeminiModels.priority(lite) < StudioGeminiModels.priority(legacy));
        check(StudioGeminiModels.priority(flash) < StudioGeminiModels.priority(legacy));
        check(StudioGeminiModels.priority(flash) < StudioGeminiModels.priority("gemini-4-preview"));
        equal("", StudioGeminiModels.restored("", legacy, catalog));
        equal(legacy, StudioGeminiModels.restored(legacy, flash, catalog));
        equal(legacy, StudioGeminiModels.restored("", legacy, Collections.singletonList(legacy)));
        equal("", StudioGeminiModels.restored("", flash, Collections.singletonList(legacy)));
        equal(flash, StudioGeminiModels.id("models/" + flash));
        equal("", StudioGeminiModels.id("models/gemini-3.8-flash:generateContent"));
        equal("", StudioGeminiModels.id("models/models/" + flash));
        equal("https://generativelanguage.googleapis.com/v1beta/models/" + flash + ":generateContent",
                StudioGeminiModels.generationUrl(flash));
        try {
            StudioGeminiModels.generationUrl("models/" + flash);
            throw new AssertionError("Double model prefix accepted");
        } catch (IllegalArgumentException expected) {
            System.out.println("Gemini catalog priority, saved model migration and generation URL verified.");
        }
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError();
    }

    private static void equal(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
