package com.rbxnative.androidport;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class StudioAiConversationTest {
    public static void main(String[] args) throws Exception {
        String luau = "local n: number = 0\nn += 1\nreturn n";
        String prompt = "Chuva no mapa\n\n```Luau\n" + luau + "\n```\n\nUse um LocalScript.";
        List<StudioAiConversation.Block> result = StudioAiConversation.blocks(prompt);
        check(result.size() == 3 && !result.get(0).code());
        check(result.get(1).luau() && result.get(1).text.equals(luau));
        check(result.get(2).text.equals("Use um LocalScript."));
        check(StudioAiConversation.hasForeignCode("```python\r\nimport pygame\r\n```"));
        check(!StudioAiConversation.hasForeignCode(prompt));
        check(!StudioAiConversation.hasForeignCode("```json\n{}\n```"));
        check(!StudioAiConversation.blocks("```luau\nlocal n = 1").get(0).luau());

        check(StudioAiConversation.scriptSource("\uFEFFIntrodução\r\n```luau\r\nreturn 42\r\n```\r\nFim").equals("return 42"));
        try {
            StudioAiConversation.scriptSource("```luau\nprint(1)\n```\n```luau\nprint(2)\n```");
            throw new AssertionError("Two script blocks accepted for one write");
        } catch (java.io.IOException expected) {}
        check(StudioAiWeather.requested("Crie chuva, raios e trovões"));
        check(!StudioAiWeather.requested("Crie chuva e uma GUI de inventário"));
        check(!StudioAiWeather.requested("Como faço chuva?"));
        if (args.length == 1) java.nio.file.Files.writeString(java.nio.file.Path.of(args[0]), StudioAiWeather.SOURCE);

        String[] field = {"instructions", "system_instruction", "system", "messages"};
        for (int provider = 0; provider < 4; provider++) {
            JSONObject input = new JSONObject().put("role", "user").put("content", "Crie chuva");
            JSONArray original = new JSONArray().put(input);
            JSONObject body = new JSONObject().put("messages", original);
            StudioAiConversation.configure(provider, body);
            String system;
            if (provider == 1) {
                system = body.getJSONObject(field[provider]).getJSONArray("parts")
                        .getJSONObject(0).getString("text");
            } else if (provider == 3) {
                JSONArray messages = body.getJSONArray("messages");
                check(messages.length() == 2 && messages.getJSONObject(1) == input);
                check(messages.getJSONObject(0).getString("role").equals("system"));
                system = messages.getJSONObject(0).getString("content");
            } else {
                system = body.getString(field[provider]);
            }
            check(system.contains("Luau") && system.contains("Roblox") && system.contains("Explorer"));
        }
        System.out.println("Roblox instructions for four providers and Luau code card boundaries verified.");
    }

    private static void check(boolean passed) {
        if (!passed) throw new AssertionError();
    }
}
