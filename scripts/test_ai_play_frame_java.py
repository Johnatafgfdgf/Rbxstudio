#!/usr/bin/env python3
"""Run production JVM receipt validators and assert actual Android player PixelCopy wiring."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
src = root / "android/src/com/rbxnative/androidport/StudioPlayFrameRules.java"
evidence = (root / "android/src/com/rbxnative/androidport/StudioPlayFrameEvidence.java").read_text()
player = (root / "android/src/com/rbxnative/androidport/RookPlayerActivity.java").read_text()
hub = (root / "android/src/com/rbxnative/androidport/StudioAiHub.java").read_text()

assert "StudioPlayFrameEvidence.begin(this)" in player
assert "PixelCopy.request(getWindow(), frame, result ->" in player
assert "StudioPlayFrameEvidence.save(this, requestId," in player
assert "if (result == PixelCopy.SUCCESS && ready" in player
assert "showControls();\n                    scheduleOnePlayFrame();" in player
assert "playFrameConsentId == null || frameCaptureScheduled" in player
assert "900L" in player
assert "StudioPlayFrameEvidence.failed(this, playFrameConsentId," in player
assert "StudioPlayFrameEvidence.arm(this)" in hub
assert "StudioPlayFrameEvidence.takeOnce(this)" in hub
assert "StudioPlayFrameEvidence.clear(this)" in hub
assert "setPositiveButton(\"Autorizar esta captura\"" in hub
assert "Nenhuma imagem foi inventada ou enviada à IA." in hub
assert "assetPreviewImage.setImageBitmap(frame)" in hub
assert "imageSelectionCount++" in hub
assert "report.optBoolean(\"playerReady\", false)" in evidence
assert "StudioPlayFrameRules.validReceipt(" in evidence
assert '"rook_native_window_pixelcopy"' in evidence
assert "MAX_JPEG_BYTES" in evidence
assert "file(c, IMAGE)" in evidence
assert "PixelCopy.SUCCESS" in evidence
assert "public static synchronized void clear(Context c)" in evidence
for forbidden in ("HttpURLConnection", "https://", "MediaProjection", "FileProvider",
                  "MediaStore", "ACTION_SCREEN_CAPTURE"):
    assert forbidden not in evidence

runner = r"""package com.rbxnative.androidport;
import java.util.Arrays;
public final class FrameRulesContract {
    static void yes(boolean truth, String message) {
        if (!truth) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        long armed=100000000L;
        long expires=armed+StudioPlayFrameRules.WINDOW_MS;
        long now=armed+9000;
        yes(StudioPlayFrameRules.validTime(armed, expires, now), "within window");
        yes(!StudioPlayFrameRules.validTime(armed, expires, expires+1), "expired");
        yes(!StudioPlayFrameRules.validTime(armed, expires, armed-1), "before arm");
        yes(!StudioPlayFrameRules.validTime(armed, expires+1, now), "overlong consent");
        byte[] jpeg=new byte[100];
        jpeg[0]=(byte)0xff; jpeg[1]=(byte)0xd8; jpeg[2]=(byte)0xff;
        jpeg[98]=(byte)0xff; jpeg[99]=(byte)0xd9;
        yes(StudioPlayFrameRules.validImage(jpeg, 420, 236), "jpeg");
        yes(!StudioPlayFrameRules.validImage(jpeg, 513, 236), "dimension");
        yes(!StudioPlayFrameRules.validImage(jpeg, 0, 236), "zero dimension");
        String hash=StudioPlayFrameRules.digest(jpeg);
        String id="ec3e9d13-8987-40d1-b6b9-f2c93995c6ca";
        yes(StudioPlayFrameRules.validReceipt(
                id,id,"rook_native_window_pixelcopy",true,0,
                armed,expires,now,hash,jpeg,420,236,now+100),
            "accepted actual frame receipt");
        yes(!StudioPlayFrameRules.validReceipt(
                id,id,"studio_editor_preview",true,0,
                armed,expires,now,hash,jpeg,420,236,now+100),
            "rejected EDITOR screenshot");
        yes(!StudioPlayFrameRules.validReceipt(
                id,id,"rook_native_window_pixelcopy",false,0,
                armed,expires,now,hash,jpeg,420,236,now+100),
            "rejected loading frame");
        yes(!StudioPlayFrameRules.validReceipt(
                id,id,"rook_native_window_pixelcopy",true,3,
                armed,expires,now,hash,jpeg,420,236,now+100),
            "rejected PixelCopy failure");
        yes(!StudioPlayFrameRules.validReceipt(
                id,"wrong-id","rook_native_window_pixelcopy",true,0,
                armed,expires,now,hash,jpeg,420,236,now+100),
            "rejected mismatched session");
        yes(!StudioPlayFrameRules.validReceipt(
                id,id,"rook_native_window_pixelcopy",true,0,
                armed,expires,now,hash,jpeg,420,236,expires+1),
            "rejected expired screenshot");
        jpeg[12]=(byte)24;
        yes(!StudioPlayFrameRules.validReceipt(
                id,id,"rook_native_window_pixelcopy",true,0,
                armed,expires,now,hash,jpeg,420,236,now+100),
            "rejected altered bytes");
        yes(StudioPlayFrameRules.digest(jpeg).length()==64,"digest");
        byte[] oversized=new byte[StudioPlayFrameRules.MAX_JPEG_BYTES+1];
        Arrays.fill(oversized,(byte)0);
        yes(!StudioPlayFrameRules.validImage(oversized,420,236),"oversize");
        System.out.println("Real Java Rook PixelCopy receipt rules: passed");
    }
}
"""
with tempfile.TemporaryDirectory(prefix="rook-frame-contract-") as temp_dir:
    out = Path(temp_dir)
    program = out / "FrameRulesContract.java"
    program.write_text(runner)
    subprocess.run(["javac", "--release", "11", "-d", str(out),
                    str(src), str(program)], check=True)
    subprocess.run(["java", "-cp", str(out),
                    "com.rbxnative.androidport.FrameRulesContract"], check=True)
