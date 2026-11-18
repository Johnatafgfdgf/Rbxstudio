#!/usr/bin/env python3
"""Execute production Java before/after pixel diff, and assert consented 2-image API contracts."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
src = root / "android/src/com/rbxnative/androidport/StudioVisualCompare.java"
hub = (root / "android/src/com/rbxnative/androidport/StudioAiHub.java").read_text()
review = (root / "android/src/com/rbxnative/androidport/StudioVisualReview.java").read_text()
assert 'MAX_COMPARISON_JPEG_BYTES = 40 * 1024' in review
assert 'buildComparisonRequest(' in review
assert 'COMPARISON_PROMPT' in review
assert '120 * 1024' in review
assert 'StudioVisualCompare.describe(' in hub
assert 'setPositiveButton("Enviar as duas miniaturas"' in hub
assert 'setNegativeButton("Não enviar"' in hub
assert 'StudioVisualReview.buildComparisonRequest(' in hub
assert 'não' in hub.lower()
java = r"""package com.rbxnative.androidport;
import java.util.Arrays;
public class CompareContract {
  static void yes(boolean ok) { if (!ok) throw new AssertionError("compare contract"); }
  static void bad(int[] before,int[] after,int w,int h) {
    try { StudioVisualCompare.describe(before,after,w,h);
          throw new AssertionError("accepted invalid compare"); }
    catch(IllegalArgumentException good) {}
  }
  public static void main(String[] args) {
    int n=160*160;
    int[] a=new int[n],b=new int[n];
    Arrays.fill(a,0xff222222);
    Arrays.fill(b,0xff222222);
    String same=StudioVisualCompare.describe(a,b,160,160);
    yes(same.contains("0.0%") && same.contains("nenhuma mudança detectável"));
    for(int y=0;y<50;y++) for(int x=0;x<50;x++) b[y*160+x]=0xffdddddd;
    String different=StudioVisualCompare.describe(a,b,160,160);
    yes(different.contains("superior esquerda"));
    yes(different.contains("Diferença RGB média"));
    yes(different.contains("NÃO comprova"));
    yes(!different.contains("0.0%"));
    bad(null,b,160,160); bad(a,new int[5],160,160);
    bad(a,b,1000,1000);
    b[0]=0x44222222;
    bad(a,b,160,160);
    System.out.println("Real Java before/after comparison: passed");
  }
}
"""
with tempfile.TemporaryDirectory(prefix="rbx-compare-") as d:
    f=Path(d)/"CompareContract.java"
    f.write_text(java)
    subprocess.run(["javac","--release","11","-d",d,str(src),str(f)],check=True)
    subprocess.run(["java","-cp",d,"com.rbxnative.androidport.CompareContract"],check=True)
