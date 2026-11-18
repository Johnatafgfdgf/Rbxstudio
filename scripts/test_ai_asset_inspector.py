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
