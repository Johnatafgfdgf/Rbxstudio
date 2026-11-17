#!/usr/bin/env python3
"""Compile and run real production Java validators used by the inserted-Model audit.

Tests canonical target paths and line-numbered MCP script pages. Android
ContentResolver, authenticated MCP, JSON tree walking and device UX still need
separate end-to-end testing.
"""
from pathlib import Path
import subprocess
import tempfile

src = (Path(__file__).resolve().parents[1] /
       "android/src/com/rbxnative/androidport/StudioAiHub.java").read_text()

def section(start, end):
    lo, hi = src.index(start), src.index(end, src.index(start))
    return src[lo:hi].strip()

path_checker = section(
    "    private static String checkedModelAuditPath(String path)",
    "    private static final class AuditScriptPage")
page_code = section(
    "    private static final class AuditScriptPage",
    "    private static JSONArray parseMcpModelTree")
risk_method = section(
    "    private static int sourceRiskMask(String source)",
    "        return mask;\n    }") + "        return mask;\n    }"
assert "callMcp(" not in path_checker + page_code
assert "return new AuditScriptPage" in page_code

java = """
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;
public final class InsertedModelAuditHarness {
%s
%s
%s
    private static void yes(boolean b) {
        if (!b) throw new AssertionError("Accepted input rejected");
    }
    private static void no(Runnable r) {
        try { r.run(); }
        catch (IllegalArgumentException e) { return; }
        throw new AssertionError("Invalid MCP response or model path accepted");
    }
    private static String path(String s) {
        try { return checkedModelAuditPath(s); }
        catch (IOException e) { throw new IllegalArgumentException(e); }
    }
    private static AuditScriptPage page(String s, int start) {
        try { return parseMcpScriptPage(s, start); }
        catch (IOException e) { throw new IllegalArgumentException(e); }
    }
    public static void main(String[] args) {
        yes(path("Workspace.Base") .equals("Workspace.Base"));
        yes(path("Workspace.Modelo Teste") .equals("Workspace.Modelo Teste"));
        yes(path("Workspace.Folder.Nested_Model") .equals("Workspace.Folder.Nested_Model"));
        yes(path(" Workspace.Mesa ") .equals("Workspace.Mesa"));
        no(() -> path("ReplicatedStorage.Model"));
        no(() -> path("Workspace"));
        no(() -> path("Workspace.Model;rm"));
        no(() -> path("Workspace.X\\nWorkspace.Y"));
        no(() -> path("Workspace.A..B"));
        no(() -> path("Workspace." + "x".repeat(65)));
        AuditScriptPage sample = page("     1→local x = require(123456)\\n     2→print(x)", 1);
        yes(sample.lines == 2);
        yes(sample.source.equals("local x = require(123456)\\nprint(x)"));
        yes((sourceRiskMask(sample.source) & 2) == 2);
        AuditScriptPage later = page("   121→print('another page')", 121);
        yes(later.lines == 1 && later.source.contains("another page"));
        yes(page("", 1).lines == 0);
        no(() -> page("     1→start\\n     3→skip", 1));
        no(() -> page("print('untrusted')", 1));
        no(() -> page("     2→wrong start", 1));
        no(() -> page("     1→ok\\nnot-numbered", 1));
        no(() -> page("a".repeat(12001), 1));
        StringBuilder oversized = new StringBuilder();
        for (int i = 1; i <= 121; i++) {
            if (i > 1) oversized.append("\\n");
            oversized.append(i).append("→x");
        }
        no(() -> page(oversized.toString(), 1));
        System.out.println("Production MCP Model audit paths and script pages: 21 cases passed");
    }
}
""" % (path_checker, page_code, risk_method)

with tempfile.TemporaryDirectory(prefix="rbx-model-audit-") as tmp:
    f = Path(tmp, "InsertedModelAuditHarness.java")
    f.write_text(java)
    subprocess.run(["javac", "--release", "11", str(f)], cwd=tmp, check=True)
    subprocess.run(["java", "-cp", tmp, "InsertedModelAuditHarness"], cwd=tmp, check=True)
