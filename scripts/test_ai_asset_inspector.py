#!/usr/bin/env python3
"""Exercise the actual production Java byte-sniffer and pixel indicators."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
src = root / "android/src/com/rbxnative/androidport/StudioAssetInspector.java"
actual = src.read_text()
hub = (root / "android/src/com/rbxnative/androidport/StudioAiHub.java").read_text()

# Keep this a local, bounded read-only preview, not a hidden asset uploader.
assert "MAX_IMAGE_BYTES = 8 * 1024 * 1024" in actual
assert "MAX_AUDIO_BYTES = 16 * 1024 * 1024" in actual
assert "MessageDigest.getInstance(\"SHA-256\")" in actual
assert "pixelSignals" in actual
assert "describeWav(audioHeader, audioHeaderLength, size)" in actual
assert "Duração estimada:" in actual
for forbidden in ("HttpURLConnection", "android.media.projection",
                  "startActivityForResult(choose, TOOLBOX_XML_PICK_REQUEST);"):
    assert forbidden not in actual
assert "ASSET_PREVIEW_PICK_REQUEST" in hub
assert "StudioAssetInspector.inspect(input)" in hub
assert "StudioAssetInspector.pixelSignals(pixels, w, h)" in hub
assert "assetPreviewImage.setImageBitmap(image)" in hub
assert "Intent.ACTION_OPEN_DOCUMENT" in hub
assert "if (inspectAssetFile != null) inspectAssetFile.setEnabled(!waiting);" in hub

java = r"""package com.rbxnative.androidport;
import java.io.*;
import java.util.*;

public final class AssetInspectorContract {
    static void yes(boolean ok) {
        if (!ok) throw new AssertionError("local asset inspector failed");
    }
    static StudioAssetInspector.Inspected inspect(byte[] bytes) throws Exception {
        return StudioAssetInspector.inspect(new ByteArrayInputStream(bytes));
    }
    static void le16(byte[] out, int off, int value) {
        out[off] = (byte) value;
        out[off + 1] = (byte) (value >>> 8);
    }
    static void le32(byte[] out, int off, long value) {
        for (int i = 0; i < 4; i++) out[off + i] = (byte) (value >>> (8 * i));
    }
    static byte[] pcmWave() throws Exception {
        // 8000 one-byte mono samples => exactly one second of PCM WAV.
        byte[] wav = new byte[8044];
        System.arraycopy("RIFF".getBytes("UTF-8"), 0, wav, 0, 4);
        le32(wav, 4, wav.length - 8);
        System.arraycopy("WAVEfmt ".getBytes("UTF-8"), 0, wav, 8, 8);
        le32(wav, 16, 16); le16(wav, 20, 1); le16(wav, 22, 1);
        le32(wav, 24, 8000); le32(wav, 28, 8000);
        le16(wav, 32, 1); le16(wav, 34, 8);
        System.arraycopy("data".getBytes("UTF-8"), 0, wav, 36, 4);
        le32(wav, 40, 8000);
        return wav;
    }
    static void refused(byte[] bytes) throws Exception {
        try {
            inspect(bytes);
            throw new AssertionError("unsafe file was accepted");
        } catch (IOException expected) {}
    }
    public static void main(String[] args) throws Exception {
        byte[] png = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL/tAAAAABJRU5ErkJggg==");
        var img = inspect(png);
        yes(img.image && !img.audio && "PNG".equals(img.format));
        yes(img.imageBytes.length == png.length);
        yes(img.sha256.length() == 64 && img.byteCount == png.length);
        yes(img.report().contains("Nenhum arquivo foi enviado à IA"));
        byte[] wav = new byte[44];
        System.arraycopy("RIFF".getBytes("UTF-8"), 0, wav, 0, 4);
        System.arraycopy("WAVE".getBytes("UTF-8"), 0, wav, 8, 4);
        var audio = inspect(wav);
        yes(audio.audio && !audio.image && audio.imageBytes == null);
        yes("WAV".equals(audio.format));
        yes(audio.report().contains("não verificável"));
        byte[] pcm = pcmWave();
        var cleanAudio = inspect(pcm);
        yes(cleanAudio.audio && cleanAudio.imageBytes == null);
        yes(cleanAudio.report().contains("8000 Hz"));
        yes(cleanAudio.report().contains("Duração estimada: 1.00 s"));
        var streamed = StudioAssetInspector.inspect(new InputStream() {
            int p = 0;
            @Override public int read() {
                return p < pcm.length ? pcm[p++] & 255 : -1;
            }
            @Override public int read(byte[] bytes, int offset, int length) {
                if (p >= pcm.length) return -1;
                bytes[offset] = pcm[p++];
                return 1;
            }
        });
        yes(streamed.report().contains("Duração estimada: 1.00 s"));
        byte[] invalidRate = Arrays.copyOf(pcm, pcm.length);
        le32(invalidRate, 28, 0);
        yes(inspect(invalidRate).report().contains("não verificável"));
        byte[] truncated = Arrays.copyOf(pcm, 200);
        yes(inspect(truncated).report().contains("não verificável"));
        refused("<svg><script>evil</script></svg>".getBytes("UTF-8"));
        refused("PK\003\004garbage".getBytes("UTF-8"));
        refused(new byte[1024]);
        int[] clear = new int[64];
        yes(StudioAssetInspector.pixelSignals(clear, 8, 8)
            .contains("não contém pixels visíveis"));
        int[] dark = new int[64];
        Arrays.fill(dark, 0xff050505);
        yes(StudioAssetInspector.pixelSignals(dark, 8, 8)
            .contains("predominam pixels escuros"));
        int[] light = new int[64];
        Arrays.fill(light, 0xffffffff);
        yes(StudioAssetInspector.pixelSignals(light, 8, 8)
            .contains("predominam pixels muito claros"));
        try {
            StudioAssetInspector.pixelSignals(new int[4], 100, 100);
            throw new AssertionError("invalid pixel count accepted");
        } catch (IllegalArgumentException expected) {}
        byte[] huge = new byte[StudioAssetInspector.MAX_IMAGE_BYTES + 1];
        System.arraycopy(png, 0, huge, 0, png.length);
        refused(huge);
        System.out.println("Real Java visual/VFX/audio inspector: passed");
    }
}
"""
with tempfile.TemporaryDirectory(prefix="rbx-asset-audit-") as folder:
    harness = Path(folder) / "AssetInspectorContract.java"
    harness.write_text(java)
    subprocess.run(["javac", "--release", "11", "-d", folder,
                    str(src), str(harness)], check=True)
    subprocess.run(["java", "-cp", folder,
                    "com.rbxnative.androidport.AssetInspectorContract"],
                   check=True)
