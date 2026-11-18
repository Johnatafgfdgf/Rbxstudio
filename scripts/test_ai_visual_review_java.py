#!/usr/bin/env python3
"""Compile and execute production provider visual request shapes with small JSON stubs."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
src = root / "android/src/com/rbxnative/androidport/StudioVisualReview.java"
hub = (root / "android/src/com/rbxnative/androidport/StudioAiHub.java").read_text()
production = src.read_text()
assert 'MAX_JPEG_BYTES = 64 * 1024' in production
assert 'REVIEW_PROMPT' in production
assert 'data:image/jpeg;base64,' in production
assert 'max_output_tokens' in production and 'generationConfig' in production
assert 'max_tokens' in production and 'image_url' in production
assert 'studio' not in production.lower() or 'studiovisualreview' in production.lower()
assert 'setPositiveButton("Enviar uma miniatura"' in hub
assert 'setNegativeButton("Não enviar"' in hub
assert 'getOnUiThread' not in production
assert 'snapshot.recycle();' in hub
assert 'if (epoch != requestEpoch) throw new IOException("Cancelado.")' in hub
assert 'StudioVisualReview.buildRequest(' in hub
assert 'JSONObject result = request(selectedProvider, key,' in hub
assert 'requestModelWithFreeFallback' not in hub[
    hub.index('private void sendVisualReview('):hub.index('private static byte[] jpegForReview(')]
assert 'assetPreviewImage.getDrawable()' in hub
assert 'reviewAssetWithAi.setEnabled(!waiting && hasImage)' in hub

jsonobject = r"""package org.json;
import java.util.*;
public class JSONObject {
    private final Map<String,Object> v=new HashMap<>();
    public JSONObject put(String k,Object x){v.put(k,x);return this;}
    public Object get(String k){return v.get(k);}
    public String getString(String k){return (String)v.get(k);}
    public JSONObject getJSONObject(String k){return (JSONObject)v.get(k);}
    public JSONArray getJSONArray(String k){return (JSONArray)v.get(k);}
    public int getInt(String k){return (Integer)v.get(k);}
}
"""
jsonarray = r"""package org.json;
import java.util.*;
public class JSONArray {
    private final List<Object> v=new ArrayList<>();
    public JSONArray put(Object x){v.add(x);return this;}
    public JSONObject getJSONObject(int i){return (JSONObject)v.get(i);}
    public int length(){return v.size();}
}
"""
test = r"""package com.rbxnative.androidport;
import org.json.*;
public class VisualReviewContract {
    static void yes(boolean b) { if(!b) throw new AssertionError("visual review shape"); }
    static void invalid(int provider, String model, byte[] img) throws Exception {
        try { StudioVisualReview.buildRequest(provider,model,img); throw new AssertionError("accepted bad input"); }
        catch(java.io.IOException expected) {}
    }
    public static void main(String[] args) throws Exception {
        byte[] jpeg = new byte[48];
        jpeg[0]=(byte)0xff; jpeg[1]=(byte)0xd8; jpeg[2]=(byte)0xff;
        String mark="data:image/jpeg;base64,";
        for(int p=0;p<4;p++){
            JSONObject v=StudioVisualReview.buildRequest(p,"vision-model",jpeg);
            if(p==0){
                JSONObject msg=v.getJSONArray("input").getJSONObject(0);
                JSONArray content=msg.getJSONArray("content");
                yes("input_text".equals(content.getJSONObject(0).getString("type")));
                yes("input_image".equals(content.getJSONObject(1).getString("type")));
                yes(content.getJSONObject(1).getString("image_url").startsWith(mark));
                yes("low".equals(content.getJSONObject(1).getString("detail")));
                yes(v.getInt("max_output_tokens")==1100);
            }else if(p==1){
                JSONArray parts=v.getJSONArray("contents").getJSONObject(0).getJSONArray("parts");
                yes("image/jpeg".equals(parts.getJSONObject(1).getJSONObject("inline_data").getString("mime_type")));
                yes(parts.getJSONObject(1).getJSONObject("inline_data").getString("data").length()>20);
                yes(v.getJSONObject("generationConfig").getInt("maxOutputTokens")==1100);
            }else if(p==2){
                JSONArray blocks=v.getJSONArray("messages").getJSONObject(0).getJSONArray("content");
                JSONObject source=blocks.getJSONObject(1).getJSONObject("source");
                yes("image".equals(blocks.getJSONObject(1).getString("type")));
                yes("base64".equals(source.getString("type")));
                yes("image/jpeg".equals(source.getString("media_type")));
                yes(v.getInt("max_tokens")==1100);
            }else {
                JSONArray blocks=v.getJSONArray("messages").getJSONObject(0).getJSONArray("content");
                yes("image_url".equals(blocks.getJSONObject(1).getString("type")));
                yes(blocks.getJSONObject(1).getJSONObject("image_url").getString("url").startsWith(mark));
            }
        }
        invalid(-1,"x",jpeg);
        invalid(5,"x",jpeg);
        invalid(0,"with spaces",jpeg);
        invalid(1,"model",new byte[10]);
        invalid(2,"model",new byte[70000]);

        byte[] before=jpeg.clone(), after=jpeg.clone();
        after[5]=(byte)42;
        for(int p=0;p<4;p++){
            JSONObject pair=StudioVisualReview.buildComparisonRequest(
                    p,"vision-model",before,after);
            JSONArray content=p==0
                    ? pair.getJSONArray("input").getJSONObject(0).getJSONArray("content")
                    : p==1
                    ? pair.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
                    : pair.getJSONArray("messages").getJSONObject(0).getJSONArray("content");
            yes(content.length()==3);
            yes(content.getJSONObject(0).getString("text").contains("ANTES"));
            yes(content.getJSONObject(0).getString("text").contains("DEPOIS"));
            if(p==0){
                yes("input_image".equals(content.getJSONObject(1).getString("type")));
                yes("input_image".equals(content.getJSONObject(2).getString("type")));
                yes(!content.getJSONObject(1).getString("image_url")
                        .equals(content.getJSONObject(2).getString("image_url")));
            } else if(p==1){
                yes("image/jpeg".equals(content.getJSONObject(1)
                        .getJSONObject("inline_data").getString("mime_type")));
                yes("image/jpeg".equals(content.getJSONObject(2)
                        .getJSONObject("inline_data").getString("mime_type")));
            } else if(p==2){
                yes("image".equals(content.getJSONObject(1).getString("type")));
                yes("image".equals(content.getJSONObject(2).getString("type")));
            } else {
                yes("image_url".equals(content.getJSONObject(1).getString("type")));
                yes("image_url".equals(content.getJSONObject(2).getString("type")));
            }
        }
        try {
            StudioVisualReview.buildComparisonRequest(0,"vision-model",
                    new byte[41000],jpeg);
            throw new AssertionError("oversized pair accepted");
        } catch(java.io.IOException expected) {}
        System.out.println("Real provider JSON image review and comparison: passed");
    }
}
"""
with tempfile.TemporaryDirectory(prefix="rbx-vision-api-") as d:
    base = Path(d)
    for name, data in {
        "org/json/JSONObject.java": jsonobject,
        "org/json/JSONArray.java": jsonarray,
        "com/rbxnative/androidport/VisualReviewContract.java": test,
    }.items():
        target = base / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(data)
    subprocess.run(["javac","--release","11","-d",str(base),str(src),
                    str(base/"org/json/JSONObject.java"),
                    str(base/"org/json/JSONArray.java"),
                    str(base/"com/rbxnative/androidport/VisualReviewContract.java")],
                   check=True)
    subprocess.run(["java","-cp",str(base),
                    "com.rbxnative.androidport.VisualReviewContract"],check=True)
