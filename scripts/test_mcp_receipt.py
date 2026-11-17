#!/usr/bin/env python3
"""Exercise the *actual* Java MCP edit-receipt parser without Android runtime.

Extracts the exact checkedEditReceipt(...) method from StudioAiHub.java and
compiles it into an isolated Java 11 test harness. No network, keys or APK.
"""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / "android/src/com/rbxnative/androidport/StudioAiHub.java").read_text()
start = source.index("    private static String checkedEditReceipt(")
end = source.index("    /** Shared real MCP transport", start)
method = source[start:end].strip()
assert "checkedEditReceipt" in method
assert method.endswith("}")

harness = """
import java.io.IOException;
public final class ReceiptHarness {
    %s

    private static void success(String raw, String expected) throws Exception {
        String actual = checkedEditReceipt(raw, expected);
        if (!actual.equals(expected)) throw new AssertionError("missing success");
    }

    private static void denied(String raw, String expected) throws Exception {
        try {
            checkedEditReceipt(raw, expected);
            throw new AssertionError("False-success accepted: " + raw);
        } catch (IOException correctlyRejected) {
            if (correctlyRejected.getMessage().isEmpty()) {
                throw new AssertionError("Missing rejection reason");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        success("CREATED:Base", "CREATED:Base");
        success("  UPDATED:Floor_2 \\n", "UPDATED:Floor_2");
        success("CREATED_MODEL:Bridge", "CREATED_MODEL:Bridge");
        success("CREATED_BILLBOARD:Info", "CREATED_BILLBOARD:Info");
        success("CREATED_SCREEN_GUI:Panel", "CREATED_SCREEN_GUI:Panel");
        success("CREATED_RECEIVE_TOOL:LightKit", "CREATED_RECEIVE_TOOL:LightKit");
        denied("NAME_EXISTS", "CREATED:Base");
        denied("STALE_PART", "UPDATED:Floor_2");
        denied("INVALID_PART", "UPDATED:Floor_2");
        denied("KIT_NAME_EXISTS", "CREATED_RECEIVE_TOOL:LightKit");
        denied("AssistantCommand:3: Lua runtime failure", "CREATED:Base");
        denied("CREATED:Other", "CREATED:Base");
        denied("CREATED:Base\\nERROR hidden", "CREATED:Base");
        denied("CREATED:Base", "CREATED:Bad.Name");
        denied("", "CREATED:Base");
        denied(null, "CREATED:Base");
        System.out.println("MCP edit receipt: 6 accepted, 10 rejected");
    }
}
""" % method

with tempfile.TemporaryDirectory(prefix="rbx-mcp-receipt-") as d:
    directory = Path(d)
    java = directory / "ReceiptHarness.java"
    java.write_text(harness)
    subprocess.run(["javac", "--release", "11", str(java)], check=True, cwd=d)
    subprocess.run(["java", "-cp", d, "ReceiptHarness"], check=True, cwd=d)
