#!/usr/bin/env python3
"""Run production Java Poly Haven URL safety and check actual Android UI wiring."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
src = root / "android/src/com/rbxnative/androidport/StudioPolyHavenCatalog.java"
catalog = src.read_text()
hub = (root / "android/src/com/rbxnative/androidport/StudioAiHub.java").read_text()

assert "https://api.polyhaven.com/search" in catalog
assert "https://polyhaven.com/a/" in catalog
assert "setInstanceFollowRedirects(false)" in catalog
assert 'setRequestProperty("User-Agent", USER_AGENT)' in catalog
assert "MAX_RESPONSE_BYTES = 256 * 1024" in catalog
assert "MAX_DETAILS_BYTES = 512 * 1024" in catalog
assert "https://api.polyhaven.com/info/" in catalog
assert "https://api.polyhaven.com/files/" in catalog
assert "return fetchJson(buildInfoUrl(slug), MAX_DETAILS_BYTES)" in catalog
assert "return fetchJson(buildFilesUrl(slug), MAX_DETAILS_BYTES)" in catalog
assert "getResponseCode()" in catalog
assert "setConnectTimeout(7000)" in catalog
assert "setReadTimeout(9000)" in catalog
for forbidden in ("getSharedPreferences", "StudioServices", "execute_luau",
                  "Authorization", "HttpPost", "PUT", "setDoOutput(true)"):
    assert forbidden not in catalog, forbidden
assert "StudioPolyHavenCatalog.search(query, type)" in hub
assert 'JSONObject response = new JSONObject(' in hub
assert 'response.optJSONArray("results")' in hub
assert 'StudioPolyHavenCatalog.validSlug(slug)' in hub
assert "StudioPolyHavenCatalog.assetPage(slug)" in hub
assert "new Intent(Intent.ACTION_VIEW," in hub
assert "searchPolyHaven.setOnClickListener(v -> searchPolyHavenAssets())" in hub
assert "if (searchPolyHaven != null) searchPolyHaven.setEnabled(!waiting);" in hub
assert "Powered by Poly Haven" in hub
assert "detailsButton.setOnClickListener(v -> inspectPolyHavenAsset(slug, type))" in hub
assert "StudioPolyHavenCatalog.fetchInfo(slug)" in hub
assert "StudioPolyHavenCatalog.fetchFiles(slug)" in hub
assert 'JSONObject info = new JSONObject(StudioPolyHavenCatalog.fetchInfo(slug))' in hub
assert 'JSONObject files = new JSONObject(' in hub
assert 'collectPolyHavenFormats(files, formats, 0, new int[]{2000})' in hub
assert "Nenhum asset baixado, importado, enviado à IA ou publicado." in hub
assert "setBusy(true, \"Lendo formatos do catálogo, sem baixar arquivos...\")" in hub

java = r"""package com.rbxnative.androidport;
import java.io.IOException;
public final class PolyHavenContract {
    static void yes(boolean ok) { if (!ok) throw new AssertionError("catalog contract"); }
    static void failQuery(String q, String type) {
        try {
            StudioPolyHavenCatalog.buildSearchUrl(q, type);
            throw new AssertionError("accepted invalid search");
        } catch (IOException expected) {}
    }
    static void failAsset(String slug) {
        try {
            StudioPolyHavenCatalog.assetPage(slug);
            throw new AssertionError("accepted unsafe slug");
        } catch (IOException expected) {}
    }
    public static void main(String[] args) throws Exception {
        String url = StudioPolyHavenCatalog.buildSearchUrl(" Piso & Vento ", "textures");
        yes(url.equals("https://api.polyhaven.com/search?q=piso+%26+vento&t=textures&limit=8"));
        yes(StudioPolyHavenCatalog.buildSearchUrl("árvore", "models")
            .contains("q=%C3%A1rvore&t=models"));
        yes(StudioPolyHavenCatalog.buildSearchUrl("por do sol", "hdris")
            .contains("&t=hdris&"));
        failQuery(null, "textures");
        failQuery("a", "textures");
        failQuery("a".repeat(101), "textures");
        failQuery("construir\nsegredo", "textures");
        failQuery("wood", "audio");
        failQuery("wood", "https://malicious.example");
        yes(StudioPolyHavenCatalog.validSlug("concrete_floor_01"));
        yes(StudioPolyHavenCatalog.assetPage("concrete_floor_01")
            .equals("https://polyhaven.com/a/concrete_floor_01"));
        failAsset("../../other-host");
        failAsset("https://untrusted.example/path");
        failAsset("white space");
        failAsset(null);
        failAsset("item?redirect=true");
        yes(StudioPolyHavenCatalog.buildInfoUrl("concrete_floor_01")
            .equals("https://api.polyhaven.com/info/concrete_floor_01"));
        yes(StudioPolyHavenCatalog.buildFilesUrl("concrete_floor_01")
            .equals("https://api.polyhaven.com/files/concrete_floor_01"));
        yes("PNG".equals(StudioPolyHavenCatalog.knownFormat("png")));
        yes("JPG".equals(StudioPolyHavenCatalog.knownFormat("jpeg")));
        yes("GLTF".equals(StudioPolyHavenCatalog.knownFormat("gltf")));
        yes("".equals(StudioPolyHavenCatalog.knownFormat("../escape")));
        yes("".equals(StudioPolyHavenCatalog.knownFormat("zip")));
        yes("".equals(StudioPolyHavenCatalog.knownFormat(null)));
        yes(StudioPolyHavenCatalog.MAX_DETAILS_BYTES == 512 * 1024);

        yes(StudioPolyHavenCatalog.MAX_RESULTS == 8);
        System.out.println("Real Poly Haven Java URL and source policy: passed");
    }
}
"""
with tempfile.TemporaryDirectory(prefix="polyhaven-java-") as d:
    harness = Path(d) / "PolyHavenContract.java"
    harness.write_text(java)
    subprocess.run(["javac", "--release", "11", "-d", d, str(src),
                    str(harness)], check=True)
    subprocess.run(["java", "-cp", d,
                    "com.rbxnative.androidport.PolyHavenContract"], check=True)
