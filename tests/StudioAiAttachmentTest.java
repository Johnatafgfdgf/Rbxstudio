package com.rbxnative.androidport;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** Run on the JVM with Android SDK and org.json on the classpath. */
public final class StudioAiAttachmentTest {
    public static void main(String[] args) throws Exception {
        byte[] luau = "local nome = \"Câmera\"\nreturn nome".getBytes(StandardCharsets.UTF_8);
        StudioAiAttachment file = StudioAiAttachment.read(new ByteArrayInputStream(luau), false);
        String withFile = file.withText("Revise o script");
        if (!withFile.startsWith("Revise o script") || !withFile.contains("Câmera"))
            throw new AssertionError("Texto do arquivo perdido");
        rejects(new byte[12 * 1024 + 1]);
        rejects(new byte[]{(byte)0xff});
        rejects(new byte[]{0, 1});
        rejects(new byte[0]);

        byte[] jpeg = new byte[48];
        jpeg[0] = (byte)0xff;
        jpeg[1] = (byte)0xd8;
        jpeg[2] = (byte)0xff;
        Constructor<StudioAiAttachment> fixture = StudioAiAttachment.class
                .getDeclaredConstructor(byte[].class, String.class);
        fixture.setAccessible(true);
        StudioAiAttachment image = fixture.newInstance(jpeg, "");
        for (int provider = 0; provider < 4; provider++) {
            String container = provider == 0 ? "input" : provider == 1 ? "contents" : "messages";
            String blocks = provider == 1 ? "parts" : "content";
            JSONObject first = new JSONObject().put(blocks, "mensagem anterior");
            JSONObject current = new JSONObject().put(blocks, provider == 1
                    ? new JSONArray().put(new JSONObject().put("text", "pedido com foto"))
                    : "pedido com foto");
            JSONObject mcp = new JSONObject().put(blocks, "resultado MCP");
            JSONObject body = new JSONObject().put(container, new JSONArray().put(first).put(current).put(mcp));
            image.addImage(body, provider, "vision-model", 1);
            JSONArray content = current.getJSONArray(blocks);
            if (content.length() != 2 || !"pedido com foto".equals(content.getJSONObject(0).getString("text")))
                throw new AssertionError("Pedido com imagem alterado no provedor " + provider);
            JSONObject photo = content.getJSONObject(1);
            if (provider == 1 ? !photo.has("inline_data") : !photo.has("type"))
                throw new AssertionError("Imagem ausente no provedor " + provider);
            if (!"mensagem anterior".equals(first.getString(blocks)) ||
                    !"resultado MCP".equals(mcp.getString(blocks)))
                throw new AssertionError("Imagem inserida no turno incorreto");
        }
        System.out.println("Anexos de chat: limites, UTF-8 e turno de imagem dos quatro provedores verificados.");
    }

    private static void rejects(byte[] bytes) throws Exception {
        try {
            StudioAiAttachment.read(new ByteArrayInputStream(bytes), false);
            throw new AssertionError("Arquivo inválido aceito");
        } catch (IOException expected) {
            // The same parsing and size checks run for Android document uploads.
        }
    }
}
