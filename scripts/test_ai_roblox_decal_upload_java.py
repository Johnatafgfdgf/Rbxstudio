#!/usr/bin/env python3
"""Production Java Open Cloud upload guards; no live Roblox API calls or keys."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
catalog = root / "android/src/com/rbxnative/androidport/StudioPolyHavenCatalog.java"
integrity = root / "android/src/com/rbxnative/androidport/StudioPolyHavenIntegrity.java"
upload = root / "android/src/com/rbxnative/androidport/StudioRobloxImageUpload.java"
source = upload.read_text()
hub = (root / "android/src/com/rbxnative/androidport/StudioAiHub.java").read_text()

for required in (
    'https://apis.roblox.com/assets/v1/assets',
    'x-api-key',
    'multipart/form-data; boundary=',
    r'name=\"request\"',
    r'name=\"fileContent\"',
    'setInstanceFollowRedirects(false)',
    'setFixedLengthStreamingMode(',
    'MAX_RESPONSE_BYTES = 64 * 1024',
    'public static String getOperation(',
):
    assert required in source, required
for forbidden in ("http://apis.roblox.com", "System.out.print",
                  "getSharedPreferences", "StudioServices", "execute_luau",
                  "setRequestProperty(\"Authorization\""):
    assert forbidden not in source, forbidden
for required in (
    'uploadRobloxDecal.setOnClickListener(v -> confirmRobloxDecalUpload())',
    'checkRobloxDecal.setOnClickListener(v -> checkRobloxDecalOperation())',
    'setPositiveButton("Enviar 1 decal"',
    'StudioPolyHavenIntegrity.readVerifiedBytes(file, input)',
    'StudioRobloxImageUpload.uploadDecal(',
    'StudioRobloxImageUpload.getOperation(key, path)',
    'inJustDecodeBounds = true',
    'verifiedPolyHavenCandidate = verified ? candidate : null',
    'pendingRobloxDecalOperation = operation',
    'nenhuma operação foi confirmada.',
):
    # Check the real source flow, not an illustrative mock.
    assert required.lower() in hub.lower(), required

java = r"""package com.rbxnative.androidport;
import java.io.*;
import java.security.*;
public final class RobloxDecalUploadContract {
    static void ok(boolean condition) {
        if (!condition) throw new AssertionError("real upload guard failed");
    }
    static void badOwner(String owner) {
        try { StudioRobloxImageUpload.requireCreator(owner);
              throw new AssertionError("invalid owner accepted");
        } catch (IOException expected) {}
    }
    static void badName(String name) {
        try { StudioRobloxImageUpload.requireName(name);
              throw new AssertionError("invalid name accepted");
        } catch (IOException expected) {}
    }
    static void badOperation(String path) {
        try { StudioRobloxImageUpload.operationUrl(path);
              throw new AssertionError("invalid operation accepted");
        } catch (IOException expected) {}
    }
    static String md5(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5").digest(bytes);
        StringBuilder out = new StringBuilder();
        for (byte b : digest) out.append(String.format("%02x",b & 255));
        return out.toString();
    }
    public static void main(String[] args) throws Exception {
        ok(StudioRobloxImageUpload.requireCreator("12345678").equals("12345678"));
        badOwner(null); badOwner(""); badOwner("0"); badOwner("001");
        badOwner("12x"); badOwner("-3"); badOwner("999999999999999999999");
        ok(StudioRobloxImageUpload.requireName("EfeitoVento01")
            .equals("EfeitoVento01"));
        badName("X"); badName("foo\" : {"); badName("../secret");
        badName("Abc\nExploit"); badName("áéí");
        String json = StudioRobloxImageUpload.requestJson("123", "EfeitoVento01");
        ok(json.contains("\"assetType\":\"Decal\""));
        ok(json.contains("\"userId\":\"123\""));
        ok(json.contains("\"displayName\":\"EfeitoVento01\""));
        ok(!json.contains("\n"));
        ok(StudioRobloxImageUpload.operationUrl("operations/abcd1234")
            .equals("https://apis.roblox.com/assets/v1/operations/abcd1234"));
        badOperation("https://evil.example/operations/12345678");
        badOperation("operations/../12345678");
        badOperation("operations/a?go=evil");
        badOperation("assets/12345678");
        badOperation(null);
        ok(StudioRobloxImageUpload.requireKey("rblx-abcdefghijklmnop")
            .startsWith("rblx-"));
        try { StudioRobloxImageUpload.requireKey("not\nvalid-secret");
              throw new AssertionError("newline key accepted"); }
        catch (IOException expected) {}
        byte[] image = new byte[2048];
        byte[] magic = {(byte)137,80,78,71,13,10,26,10};
        System.arraycopy(magic,0,image,0,magic.length);
        for (int i=8;i<image.length;i++) image[i]=(byte)(i*5);
        StudioPolyHavenIntegrity.Candidate manifest =
            new StudioPolyHavenIntegrity.Candidate(
                "wood_floor", "PNG", "diffuse/1k/png", image.length, md5(image),
                "https://dl.polyhaven.org/file/ph-assets/Textures/a.png");
        byte[] checked = StudioPolyHavenIntegrity.readVerifiedBytes(
                manifest,new ByteArrayInputStream(image));
        ok(checked.length == image.length);
        byte[] altered = image.clone(); altered[90]^=1;
        try { StudioPolyHavenIntegrity.readVerifiedBytes(
                manifest, new ByteArrayInputStream(altered));
              throw new AssertionError("modified image accepted");
        } catch (IOException expected) {}
        try { StudioPolyHavenIntegrity.readVerifiedBytes(
                manifest, new ByteArrayInputStream(new byte[image.length+1]));
              throw new AssertionError("overflow image accepted");
        } catch (IOException expected) {}
        try { StudioPolyHavenIntegrity.readVerifiedBytes(
                manifest, new ByteArrayInputStream(new byte[image.length-1]));
              throw new AssertionError("truncated image accepted");
        } catch (IOException expected) {}
        System.out.println("Real Java Roblox decal metadata guards and rehash: passed");
    }
}
"""
with tempfile.TemporaryDirectory(prefix="rbx-cloud-upload-") as temp:
    main = Path(temp) / "RobloxDecalUploadContract.java"
    main.write_text(java)
    subprocess.run(["javac", "--release", "11", "-d", temp,
                    str(catalog), str(integrity), str(upload), str(main)],
                   check=True)
    subprocess.run(["java", "-cp", temp,
                    "com.rbxnative.androidport.RobloxDecalUploadContract"],
                   check=True)
