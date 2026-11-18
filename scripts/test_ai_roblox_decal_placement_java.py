#!/usr/bin/env python3
"""Exercise actual production Java decal placement guards and fixed Luau.

This test does not claim a live Roblox server or Android MCP was available.
"""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
src = root / "android/src/com/rbxnative/androidport/StudioRobloxDecalPlacement.java"
hub = (root / "android/src/com/rbxnative/androidport/StudioAiHub.java").read_text()
actual = src.read_text()

for required in (
    'confirmedRobloxDecalId',
    'confirmedId = StudioRobloxDecalPlacement.validateAssetId(id)',
    'pendingDecalPlacement = null',
    'inspectDecalTarget.setOnClickListener(v -> inspectDecalPartTarget())',
    'applyDecalToPart.setOnClickListener(v -> confirmDecalPlacement())',
    'setPositiveButton("Sim, criar 1 Decal"',
    'readOnlyMcp(mcpPairing, "inspect_instance"',
    'checkedBillboardTarget(path, actual, name)',
    'pendingDecalPlacement = checked',
    'p.originalUniqueId.equals(latest.optString("uniqueId", ""))',
    'StudioRobloxDecalPlacement.fixedLuau(p)',
    'executeApproved3dCode(paired,',
    'checkedEditReceipt(',
    'verifyPlacedRobloxDecal(paired, p)',
    'p.textureUri().equals(props.optString("Texture", ""))',
    'CREATED_DECAL|',
    'recordEditorOutcome("CREATE_DECAL", p.decalPath(), confirmed)',
):
    assert required in hub, required

assert 'getSharedPreferences' not in actual
assert 'execute_luau' not in actual
assert 'callMcp(' not in actual

java = r"""package com.rbxnative.androidport;
import java.io.*;
public final class DecalPlacementContract {
    static void yes(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    static void reject(Check check) {
        try { check.run(); throw new AssertionError("invalid placement accepted"); }
        catch (IOException expected) {}
    }
    interface Check { void run() throws IOException; }
    public static void main(String[] args) throws Exception {
        var simple = StudioRobloxDecalPlacement.reviewed(
            "123456789", "Workspace.Parede", "Vento01", "Front", "part-uid");
        yes(simple.decalPath().equals("Workspace.Parede.Vento01"),"decal path");
        yes(simple.textureUri().equals("rbxassetid://123456789"),"texture path");
        yes(simple.receipt().equals("CREATED_DECAL:Vento01"),"exact receipt");
        String code = StudioRobloxDecalPlacement.fixedLuau(simple);
        yes(code.contains("game:GetService(\"Workspace\")"),"Workspace only");
        yes(code.contains("local target = parent:FindFirstChild(\"Parede\")"),"exact Part");
        yes(code.contains("target.ClassName ~= \"Part\""),"class guard");
        yes(code.contains("target.Parent ~= parent"),"direct child guard");
        yes(code.contains("return \"DECAL_EXISTS\""),"duplicate prevention");
        yes(code.contains("Instance.new(\"Decal\")"),"real Decal created");
        yes(code.contains("decal.Texture = \"rbxassetid://123456789\""),"real texture");
        yes(code.contains("decal.Face = Enum.NormalId.Front"),"exact selected face");
        yes(code.contains("return \"CREATED_DECAL:Vento01\""),"exact receipt");
        yes(!code.contains("HttpService"),"no HTTP in code");
        yes(!code.contains("loadstring"),"no dynamic code");
        yes(!code.contains("require("),"no require calls");
        yes(!code.contains("Destroy("),"no deletions");
        yes(!code.contains("GameId"),"no publication");
        var nested = StudioRobloxDecalPlacement.reviewed(
            "456", "Workspace.Casa.Telha", "Chuva", "Top", "uid-2");
        String nestedCode=StudioRobloxDecalPlacement.fixedLuau(nested);
        yes(nestedCode.contains("ws:FindFirstChild(\"Casa\")"),"one-level Model");
        yes(nestedCode.contains("parent.ClassName ~= \"Model\""),"Model guard");
        yes(nestedCode.contains("parent.Parent ~= ws"),"parent scope");
        yes(nestedCode.contains("parent:FindFirstChild(\"Telha\")"),"Part in Model");
        yes(nestedCode.contains("decal.Face = Enum.NormalId.Top"),"Top face");
        for (String face: new String[]{"Front","Back","Top","Bottom","Left","Right"}) {
            var item=StudioRobloxDecalPlacement.reviewed(
                "1","Workspace.Parede","D1",face,"uid");
            yes(StudioRobloxDecalPlacement.fixedLuau(item)
                 .contains("Enum.NormalId."+face),"enum validated");
        }
        reject(()->StudioRobloxDecalPlacement.reviewed(null,
                "Workspace.Parede","D1","Front","uid"));
        reject(()->StudioRobloxDecalPlacement.reviewed("0",
                "Workspace.Parede","D1","Front","uid"));
        reject(()->StudioRobloxDecalPlacement.reviewed("001",
                "Workspace.Parede","D1","Front","uid"));
        reject(()->StudioRobloxDecalPlacement.reviewed("9999999999999999999",
                "Workspace.Parede","D1","Front","uid"));
        reject(()->StudioRobloxDecalPlacement.reviewed("1;print('bad')",
                "Workspace.Parede","D1","Front","uid"));
        reject(()->StudioRobloxDecalPlacement.reviewed("123",
                "ReplicatedStorage.Parede","D1","Front","uid"));
        reject(()->StudioRobloxDecalPlacement.reviewed("123",
                "Workspace.Parede.Other.Deep","D1","Front","uid"));
        reject(()->StudioRobloxDecalPlacement.reviewed("123",
                "Workspace.Parede;require(1)","D1","Front","uid"));
        reject(()->StudioRobloxDecalPlacement.reviewed("123",
                "Workspace.Parede","D1;print('bad')","Front","uid"));
        reject(()->StudioRobloxDecalPlacement.reviewed("123",
                "Workspace.Parede","D1","Front;print('bad')","uid"));
        reject(()->StudioRobloxDecalPlacement.reviewed("123",
                "Workspace.Parede","D1","front","uid"));
        reject(()->StudioRobloxDecalPlacement.reviewed("123",
                "Workspace.Parede","D1","Front",""));
        reject(()->StudioRobloxDecalPlacement.fixedLuau(null));
        System.out.println("Production Java Decal placement Luau and guards: passed");
    }
}
"""
with tempfile.TemporaryDirectory(prefix="rbx-decal-placement-") as tmp:
    harness = Path(tmp) / "DecalPlacementContract.java"
    harness.write_text(java)
    subprocess.run(["javac", "--release", "11", "-d", tmp,
                    str(src), str(harness)], check=True)
    subprocess.run(["java", "-cp", tmp,
                    "com.rbxnative.androidport.DecalPlacementContract"], check=True)
