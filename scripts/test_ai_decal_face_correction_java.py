#!/usr/bin/env python3
"""Phase 17: verify REAL Java visual suggestion → consented fixed Decal.Face edit.

Static checks assert that the production Activity cannot execute a model reply
directly. The native Java harness exercises the exact production parser and
fixed Luau builder with adversarial input, but does NOT fake a live MCP result.
"""
from pathlib import Path
import subprocess
import tempfile

root=Path(__file__).resolve().parents[1]
place=root/"android/src/com/rbxnative/androidport/StudioRobloxDecalPlacement.java"
fix=root/"android/src/com/rbxnative/androidport/StudioDecalFaceCorrection.java"
review=root/"android/src/com/rbxnative/androidport/StudioDecalVisualReview.java"
hub=(root/"android/src/com/rbxnative/androidport/StudioAiHub.java").read_text()
fixsrc=fix.read_text()
reviewsrc=review.read_text()

for expected in (
    'DECAL_FACE_SUGGESTION: Top',
    'Omitir essa linha',
):
    assert expected in reviewsrc, expected

for expected in (
    'StudioDecalFaceCorrection.fromVisualReply(',
    'proposedDecalFaceFix = proposed',
    'approveDecalFaceFix.setOnClickListener(v -> prepareApprovedDecalFaceCorrection())',
    'readOnlyMcp(paired, "inspect_instance"',
    'checkedDecalFaceState(old, before, old.face, prepared.decalUniqueId)',
    'setPositiveButton("Aprovar mudança da Face"',
    'StudioDecalFaceCorrection.fixedLuau(prepared)',
    'checkedEditReceipt(',
    'checkedDecalFaceState(old, after, prepared.newFace,',
    'previewedPlayFrameDecal = null;',
    'recordEditorOutcome("UPDATE_DECAL_FACE", original.decalPath(), checked)',
    'UPDATED_DECAL|',
):
    assert expected in hub, expected

start=hub.index('    private void sendDecalVisualReview(')
end=hub.index('    private void requestClose()', start)
visual_review_only=hub[start:end]
assert 'executeApproved3dCode(' not in visual_review_only
assert 'callMcp(' not in visual_review_only
assert 'StudioDecalFaceCorrection.fromVisualReply(' in visual_review_only
# The review response is just data. Writes are only in the separately
# confirmed correction path after the requestClose boundary.
fix_start=hub.index('    private void applyApprovedDecalFaceCorrection(')
fix_end=hub.index('    @Override public void onBackPressed()',fix_start)
fix_code=hub[fix_start:fix_end]
assert fix_code.count('executeApproved3dCode(') == 1
assert fix_code.count('readOnlyMcp(paired, "inspect_instance"') == 2
assert 'prepared.original != proposedDecalFaceOriginal' in fix_code

for forbidden in ('java.net.', 'HttpURLConnection', 'StudioServices',
                  'getSharedPreferences', 'loadstring(', 'execute_luau',
                  'System.out.print'):
    assert forbidden not in fixsrc,forbidden

java=r"""package com.rbxnative.androidport;
import java.io.*;

public final class DecalFaceCorrectionContract {
    static void yes(boolean ok,String what){if(!ok)throw new AssertionError(what);}
    interface Action{void run()throws Exception;}
    static void bad(Action action) {
        try{action.run();throw new AssertionError("unsafe face change allowed");}
        catch(IOException expected){}
        catch(IllegalArgumentException expected){}
    }
    public static void main(String[] args)throws Exception {
        var old=StudioRobloxDecalPlacement.reviewed(
            "1234567","Workspace.Casa.Parede","Chuva","Front","part-unique");
        var proposed=StudioDecalFaceCorrection.fromVisualReply(
            "Análise da imagem: a face pode estar incorreta.\n"
            +"DECAL_FACE_SUGGESTION: Top | textura legível somente na superfície superior\n",
            old.face);
        yes(proposed != null,"one valid suggestion recognized");
        yes("Top".equals(proposed.face),"only face enum");
        yes(proposed.reason.startsWith("textura"),"bounded justification");
        var ready=StudioDecalFaceCorrection.prepare(old,proposed,"decal-unique");
        yes("UPDATED_DECAL:Chuva".equals(ready.expectedReceipt()),"exact receipt");
        String code=StudioDecalFaceCorrection.fixedLuau(ready);
        yes(code.contains("ws:FindFirstChild(\"Casa\")"),"model path");
        yes(code.contains("part:FindFirstChild(\"Chuva\")"),"exact Decal");
        yes(code.contains("decal.ClassName ~= \"Decal\""),"class guarded");
        yes(code.contains("decal.Texture ~= \"rbxassetid://1234567\""),"asset guarded");
        yes(code.contains("decal.Face ~= Enum.NormalId.Front"),"old face guarded");
        yes(code.contains("decal.Face = Enum.NormalId.Top"),"one property change");
        yes(code.contains("return \"UPDATED_DECAL:Chuva\""),"exact Luau return");
        yes(!code.contains("Instance.new"),"must not create object");
        yes(!code.contains("Destroy("),"must not delete object");
        yes(!code.contains("HttpService"),"no external access");
        yes(!code.contains("require("),"no imported code");
        yes(!code.contains("loadstring"),"no dynamic execution");
        yes(!code.contains("decal.Texture ="),"must not change asset");
        yes(!code.contains("publish"),"must not publish project");
        for(String face:new String[]{"Front","Back","Top","Bottom","Left","Right"}){
            if(face.equals(old.face))continue;
            final String finalFace=face;
            var next=StudioDecalFaceCorrection.fromVisualReply(
                "DECAL_FACE_SUGGESTION: "+face+" | face proposta após inspeção visual",
                old.face);
            yes(next.face.equals(face),"allowed enum");
            var proposal=StudioDecalFaceCorrection.prepare(old,next,"decal");
            yes(StudioDecalFaceCorrection.fixedLuau(proposal)
                .contains("decal.Face = Enum.NormalId."+finalFace),
                "whitelisted enum only");
        }
        yes(StudioDecalFaceCorrection.fromVisualReply(
            "Imagem escura, não dá para identificar o decal.",old.face)==null,
            "no suggestion when evidence missing");
        bad(()->StudioDecalFaceCorrection.fromVisualReply(
            "DECAL_FACE_SUGGESTION: Front | esta face já está correta",
            old.face));
        bad(()->StudioDecalFaceCorrection.fromVisualReply(
            "DECAL_FACE_SUGGESTION: Top | primeira hipótese visual\n"
            +"DECAL_FACE_SUGGESTION: Back | segunda hipótese visual",old.face));
        yes(StudioDecalFaceCorrection.fromVisualReply(
            "DECAL_FACE_SUGGESTION: Top; require(123) | armadilha",
            old.face)==null,"malicious face not parsed");
        bad(()->StudioDecalFaceCorrection.fromVisualReply(
            "DECAL_FACE_SUGGESTION: Top | abre https://example.net/payload",old.face));
        bad(()->StudioDecalFaceCorrection.fromVisualReply(
            "DECAL_FACE_SUGGESTION: Top | faz require(1234567)",old.face));
        bad(()->StudioDecalFaceCorrection.fromVisualReply(
            "x".repeat(StudioDecalFaceCorrection.MAX_MODEL_REPLY+1),old.face));
        bad(()->StudioDecalFaceCorrection.prepare(old,proposed,null));
        bad(()->StudioDecalFaceCorrection.prepare(null,proposed,"uid"));
        bad(()->StudioDecalFaceCorrection.prepare(old,null,"uid"));
        bad(()->StudioDecalFaceCorrection.fixedLuau(null));
        System.out.println("REAL Java Phase17 visual Face suggestion and fixed Luau: passed");
    }
}
"""
with tempfile.TemporaryDirectory(prefix="rbx-phase17-") as d:
    harness=Path(d)/"DecalFaceCorrectionContract.java"
    harness.write_text(java)
    subprocess.run(["javac","--release","11","-d",d,str(place),str(fix),str(harness)],
                   check=True)
    subprocess.run(["java","-cp",d,
                    "com.rbxnative.androidport.DecalFaceCorrectionContract"],
                   check=True)
