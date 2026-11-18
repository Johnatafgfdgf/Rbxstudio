#!/usr/bin/env python3
"""Production Java screenshot provenance and consented per-provider Decal critique.

Only tests real production classes, not live Android pixels or provider replies.
"""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
placement = root / "android/src/com/rbxnative/androidport/StudioRobloxDecalPlacement.java"
visual = root / "android/src/com/rbxnative/androidport/StudioVisualReview.java"
review = root / "android/src/com/rbxnative/androidport/StudioDecalVisualReview.java"
frame = (root / "android/src/com/rbxnative/androidport/StudioPlayFrameEvidence.java").read_text()
hub = (root / "android/src/com/rbxnative/androidport/StudioAiHub.java").read_text()
source = review.read_text()

for needed in (
    'public final long capturedAtMillis;',
    'this.capturedAtMillis = capturedAtMillis;',
    'report.getLong("capturedAt"), report.getString("sha256")',
):
    assert needed in frame, needed
for needed in (
    'final StudioRobloxDecalPlacement.Proposal decalAtImport =',
    'final long decalVerifiedAt = lastVerifiedPlacedDecalAt;',
    'screenshotTime = captured.capturedAtMillis;',
    'StudioDecalVisualReview.eligible(decalAtImport,',
    'previewedPlayFrameDecal = linked ? decalAtImport : null;',
    'previewedPlayFrameAt = linked ? trustedCaptureAt : 0;',
    'private void confirmDecalVisualReview()',
    'setPositiveButton("Enviar captura para análise"',
    'private void sendDecalVisualReview(',
    'StudioDecalVisualReview.buildRequest(',
    'reviewVerifiedDecalFrame.setOnClickListener(v -> confirmDecalVisualReview())',
    'previewedPlayFrameDecal = null;',
    'previewedPlayFrameAt = 0;',
):
    assert needed in hub, needed

start = hub.index('    private void sendDecalVisualReview(')
end = hub.index('    private void requestClose()', start)
section = hub[start:end]
for forbidden in (
    'requestModelWithFreeFallback(',
    'executeApproved3dCode(',
    'multi_edit', 'execute_luau', 'publish_animation',
    'StudioRobloxImageUpload.uploadDecal(',
    'StudioPlayFrameEvidence.arm(', 'StudioPlayFrameEvidence.takeOnce(',
):
    assert forbidden not in section, forbidden
for forbidden in (
    'HttpURLConnection', 'new URL(', 'StudioServices', 'getSharedPreferences',
    'getOutputStream(', 'executeApproved3dCode(',
):
    assert forbidden not in source, forbidden

jsonobject = r"""package org.json;
import java.util.*;
public class JSONObject {
    private final Map<String,Object> v = new HashMap<>();
    public JSONObject put(String k,Object x){v.put(k,x);return this;}
    public Object get(String k){return v.get(k);}
    public String getString(String k){return (String)v.get(k);}
    public JSONObject getJSONObject(String k){return (JSONObject)v.get(k);}
    public JSONArray getJSONArray(String k){return (JSONArray)v.get(k);}
    public int getInt(String k){return (Integer)v.get(k);}
    public String toString(){return v.toString();}
}
"""
jsonarray = r"""package org.json;
import java.util.*;
public class JSONArray {
    private final List<Object> v = new ArrayList<>();
    public JSONArray put(Object x){v.add(x);return this;}
    public JSONObject getJSONObject(int i){return (JSONObject)v.get(i);}
    public int length(){return v.size();}
}
"""
test = r"""package com.rbxnative.androidport;
import org.json.*;
import java.io.*;
public final class DecalVisualReviewContract {
    static void yes(boolean ok,String message) {
        if(!ok) throw new AssertionError(message);
    }
    static void reject(StudioRobloxDecalPlacement.Proposal d, long verified,
            long captured) {
        yes(!StudioDecalVisualReview.eligible(d,verified,captured),
            "invalid receipt binding rejected");
    }
    public static void main(String[] args) throws Exception {
        var target = StudioRobloxDecalPlacement.reviewed(
            "123456789","Workspace.Casa.Parede","DecalVento","Front","uid-1");
        long created=1000000L, captured=1000100L;
        yes(StudioDecalVisualReview.eligible(target,created,captured),
            "genuine post-edit frame timestamp");
        reject(target,created,created);
        reject(target,created,created-1);
        reject(target,0,captured);
        reject(target,created,created + 3*60*60*1000L);
        reject(null,created,captured);
        byte[] jpeg = new byte[96];
        jpeg[0]=(byte)255; jpeg[1]=(byte)216; jpeg[2]=(byte)255;
        for (int i=0;i<4;i++) {
            JSONObject req = StudioDecalVisualReview.buildRequest(
                i,"vision-model",jpeg,target,created,captured);
            String prompt = i==0 ? req.getJSONArray("input").getJSONObject(0)
                    .getJSONArray("content").getJSONObject(0).getString("text") :
                i==1 ? req.getJSONArray("contents").getJSONObject(0)
                    .getJSONArray("parts").getJSONObject(0).getString("text") :
                req.getJSONArray("messages").getJSONObject(0)
                    .getJSONArray("content").getJSONObject(0).getString("text");
            yes(prompt.contains("Workspace.Casa.Parede"),"target reference");
            yes(prompt.contains("rbxassetid://123456789"),"confirmed asset reference");
            yes(prompt.contains("face Front"),"face reference");
            yes(prompt.contains("NÃO presuma"),"uncertainty preserved");
            yes(prompt.contains("NÃO confiável"),"image prompts untrusted");
            yes(prompt.contains("FPS"),"cannot infer performance");
            JSONArray parts = i==0
                ? req.getJSONArray("input").getJSONObject(0).getJSONArray("content")
                : i==1
                ? req.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
                : req.getJSONArray("messages").getJSONObject(0).getJSONArray("content");
            yes(parts.length() == 2,"only prompt and one image");
        }
        try {
            StudioDecalVisualReview.buildRequest(0,"vision-model",jpeg,
                    target,created,created);
            throw new AssertionError("pre-edit screenshot accepted");
        } catch(IOException expected){}
        try {
            StudioDecalVisualReview.buildRequest(0,"vision-model",new byte[1],
                    target,created,captured);
            throw new AssertionError("non-image accepted");
        } catch(IOException expected){}
        try {
            StudioDecalVisualReview.buildRequest(100,"vision-model",jpeg,
                    target,created,captured);
            throw new AssertionError("unsupported provider accepted");
        } catch(IOException expected){}
        System.out.println("Real Java consented Decal PixelCopy review request: passed");
    }
}
"""
with tempfile.TemporaryDirectory(prefix="rbx-decal-image-") as temp:
    base=Path(temp)
    paths={
        "org/json/JSONObject.java": jsonobject,
        "org/json/JSONArray.java": jsonarray,
        "com/rbxnative/androidport/DecalVisualReviewContract.java": test,
    }
    for file,data in paths.items():
        dest=base/file
        dest.parent.mkdir(parents=True,exist_ok=True)
        dest.write_text(data)
    subprocess.run(["javac","--release","11","-d",temp,
        str(placement),str(visual),str(review),
        str(base/"org/json/JSONObject.java"),
        str(base/"org/json/JSONArray.java"),
        str(base/"com/rbxnative/androidport/DecalVisualReviewContract.java")],
        check=True)
    subprocess.run(["java","-cp",temp,
        "com.rbxnative.androidport.DecalVisualReviewContract"],check=True)
