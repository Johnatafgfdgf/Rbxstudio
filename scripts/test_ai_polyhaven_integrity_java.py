#!/usr/bin/env python3
"""Execute production Java local file integrity contract with adversarial fixtures."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
catalog = root / "android/src/com/rbxnative/androidport/StudioPolyHavenCatalog.java"
integrity = root / "android/src/com/rbxnative/androidport/StudioPolyHavenIntegrity.java"
hub = (root / "android/src/com/rbxnative/androidport/StudioAiHub.java").read_text()
source = integrity.read_text()

for text in (
    "POLYHAVEN_VERIFY_PICK_REQUEST",
    "collectPolyHavenCandidates(files, slug, \"\", candidates,",
    "StudioPolyHavenIntegrity.verify(candidate, input)",
    "beginPolyHavenVerification(candidate)",
    "candidate.variant",
    "getContentResolver().openInputStream(selected)",
    "Intent.ACTION_OPEN_DOCUMENT",
    "setPositiveButton(\"Escolher arquivo\"",
    "pendingPolyHavenCandidate = null",
):
    assert text in hub, text

assert "candidate.expectedSize" in source

for disallowed in ("HttpURLConnection", "java.net.URL", "openConnection(",
                   "getOutputStream", "writeFile(", "publish_animation",
                   "execute_luau", "StudioServices"):
    assert disallowed not in source, disallowed

java = r"""package com.rbxnative.androidport;
import java.io.*;
import java.security.*;
import java.util.*;
public final class PolyHavenIntegrityContract {
    static void yes(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    static String md5(byte[] content) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5").digest(content);
        StringBuilder h = new StringBuilder();
        for (byte b : digest) h.append(String.format("%02x", b & 255));
        return h.toString();
    }
    static StudioPolyHavenIntegrity.Candidate cand(byte[] content) throws Exception {
        return new StudioPolyHavenIntegrity.Candidate(
            "concrete_floor_01", "PNG", "diffuse/1k/png", content.length,
            md5(content), "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/x.png");
    }
    static void invalid(String slug,String format,String path,long size,
            String md5,String url) throws Exception {
        try {
            new StudioPolyHavenIntegrity.Candidate(slug,format,path,size,md5,url);
            throw new AssertionError("unsafe candidate accepted");
        } catch (IOException expected) {}
    }
    static void rejected(StudioPolyHavenIntegrity.Candidate c, byte[] bytes)
            throws Exception {
        try {
            StudioPolyHavenIntegrity.verify(c,new ByteArrayInputStream(bytes));
            throw new AssertionError("unsafe file accepted");
        } catch (IOException expected) {}
    }
    public static void main(String[] args) throws Exception {
        byte[] image = new byte[1024];
        byte[] magic = {(byte)137,80,78,71,13,10,26,10};
        System.arraycopy(magic,0,image,0,magic.length);
        for (int i=8;i<image.length;i++) image[i]=(byte)(i*7);
        var c = cand(image);
        var accepted = StudioPolyHavenIntegrity.verify(c,
                new ByteArrayInputStream(image));
        yes(accepted.matches,"official metadata and local bytes match");
        yes(accepted.bytes == image.length,"streamed exact byte count");
        yes(accepted.sha256.length()==64,"local sha");
        yes(accepted.report(c).contains("NÃO comprova"),"honest trust claim");
        yes(c.label().contains("diffuse"),"selection label");
        var streamed = StudioPolyHavenIntegrity.verify(c, new InputStream() {
            int offset;
            @Override public int read() {
                return offset<image.length ? image[offset++] & 255 : -1;
            }
            @Override public int read(byte[] b,int start,int len) {
                if (offset>=image.length) return -1;
                b[start]=image[offset++];
                return 1;
            }
        });
        yes(streamed.matches,"fragmented Android document stream");
        byte[] altered=image.clone(); altered[20]^=1;
        yes(!StudioPolyHavenIntegrity.verify(c,
                new ByteArrayInputStream(altered)).matches,
                "checksum mismatch is not accepted");
        byte[] truncated=Arrays.copyOf(image, image.length-1);
        yes(!StudioPolyHavenIntegrity.verify(c,
                new ByteArrayInputStream(truncated)).matches,
                "size mismatch is not accepted");
        byte[] tooLarge=Arrays.copyOf(image, image.length+1);
        rejected(c,tooLarge);
        byte[] badSignature=image.clone();
        badSignature[0]=0;
        rejected(c,badSignature);
        invalid("../not-an-asset","PNG","diffuse/1k/png",image.length,
                md5(image),"https://dl.polyhaven.org/file/ph-assets/a.png");
        invalid("concrete_floor_01","EXE","diffuse/1k/exe",image.length,
                md5(image),"https://dl.polyhaven.org/file/ph-assets/a.exe");
        invalid("concrete_floor_01","PNG","../../secret",image.length,
                md5(image),"https://dl.polyhaven.org/file/ph-assets/a.png");
        invalid("concrete_floor_01","PNG","diffuse/1k/png",
                StudioPolyHavenIntegrity.MAX_FILE_BYTES+1L,
                md5(image),"https://dl.polyhaven.org/file/ph-assets/a.png");
        invalid("concrete_floor_01","PNG","diffuse/1k/png",image.length,
                "missing-hash","https://dl.polyhaven.org/file/ph-assets/a.png");
        invalid("concrete_floor_01","PNG","diffuse/1k/png",image.length,
                md5(image),"https://evil.example/file/ph-assets/a.png");
        invalid("concrete_floor_01","PNG","diffuse/1k/png",image.length,
                md5(image),"https://dl.polyhaven.org@evil.example/file/ph-assets/a.png");
        invalid("concrete_floor_01","PNG","diffuse/1k/png",image.length,
                md5(image),"http://dl.polyhaven.org/file/ph-assets/a.png");
        yes(!StudioPolyHavenIntegrity.officialFileUrl(
                "https://dl.polyhaven.org/file/ph-assets/a.png?redirect=evil"),
                "untrusted redirects in URL rejected");
        yes(StudioPolyHavenIntegrity.MAX_FILE_BYTES == 8*1024*1024,
                "bounded streaming size");
        System.out.println("Real Java local Poly Haven size MD5 SHA256 and MIME checks: passed");
    }
}
"""
with tempfile.TemporaryDirectory(prefix="rbx-polyhaven-integrity-") as d:
    target = Path(d) / "PolyHavenIntegrityContract.java"
    target.write_text(java)
    subprocess.run(["javac", "--release", "11", "-d", d, str(catalog),
                    str(integrity), str(target)], check=True)
    subprocess.run(["java", "-cp", d,
                    "com.rbxnative.androidport.PolyHavenIntegrityContract"],
                   check=True)
