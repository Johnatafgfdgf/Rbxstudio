#!/usr/bin/env python3
"""Inventory every tracked file and flag compatibility/identity risks.

External source trees and legal notice files are counted and hashed, not
silently edited. Keyword findings are evidence for review, not proofs of bugs.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "dist" / "source-audit"
OUT.mkdir(parents=True, exist_ok=True)

LEGACY = re.compile(r"rbx[-_. ]?native|rbxnative|RbxNative", re.IGNORECASE)
RISK = re.compile(r"FIXME|TODO|todo!\s*\(|unimplemented!\s*\(|panic!\s*\(", re.IGNORECASE)

def tracked(repo):
    try:
        result = subprocess.check_output(["git", "-C", str(repo), "ls-files", "-z"],
                                         stderr=subprocess.DEVNULL)
    except (OSError, subprocess.CalledProcessError):
        return []
    return [repo / os.fsdecode(name) for name in result.split(b"\0") if name]

roots = [("product", ROOT)]
roots.extend(("third_party", ROOT / "upstream" / name)
             for name in ("rbx-native", "rook", "gpui-mobile"))
records = []
legacy_hits = []
risk_hits = []
totals = {"product": 0, "third_party": 0, "read_bytes": 0, "binary": 0,
          "unreadable": 0, "user_facing_candidates": 0}

for scope, repo in roots:
    for path in tracked(repo):
        if not path.is_file():
            continue
        relative = path.relative_to(ROOT).as_posix()
        try:
            raw = path.read_bytes()
        except OSError:
            totals["unreadable"] += 1
            records.append({"path": relative, "scope": scope, "read_error": True})
            continue
        totals[scope] += 1
        totals["read_bytes"] += len(raw)
        sha = hashlib.sha256(raw).hexdigest()
        data = {"path": relative, "scope": scope, "size": len(raw), "sha256": sha}
        if b"\0" in raw[:2048]:
            totals["binary"] += 1
            data["binary"] = True
            records.append(data)
            continue
        try:
            source = raw.decode("utf-8")
        except UnicodeDecodeError:
            totals["binary"] += 1
            data["binary"] = True
            records.append(data)
            continue
        records.append(data)
        if scope != "product":
            continue
        # License, provenance and historical docs must retain correct credits.
        legal = relative == "LICENSE" or relative.startswith(
            ("docs/license-sources/", "docs/evidence/", "docs/CONTINUITY"))
        code = relative.startswith(("android/", "native/", "core/", "studio/",
                                    "player/", "scripts/", "patches/", "preview/"))
        if not code:
            continue
        for number, line in enumerate(source.splitlines(), 1):
            if relative.startswith("patches/") and (line.startswith("-") or line.startswith("+++")):
                continue
            if LEGACY.search(line):
                kind = "internal-or-dependency"
                if any(token in line for token in ('setTitle(', 'status:', '"status":',
                                                   '"name":', '<source>', 'setText(',
                                                   '"description":', '"RbxNative"')):
                    kind = "potential-user-facing"
                    totals["user_facing_candidates"] += 1
                # Do not print full source, tokens, account data or key contents.
                legacy_hits.append({"path": relative, "line": number, "kind": kind})
            if RISK.search(line) and not legal and len(risk_hits) < 1000:
                risk_hits.append({"path": relative, "line": number,
                                  "kind": "static-code-review-candidate"})
reported = {
    "root": "Rbx Studio source tree plus pinned third-party submodules",
    "totals": totals,
    "tracked_files": records,
    "legacy_references": legacy_hits,
    "review_candidates": risk_hits,
    "known_risks": [
        {"id": "debug-signing", "severity": "high",
         "note": "A fresh Android debug key on disposable CI runners may prevent APK upgrades",
         "path": "scripts/package_apk.py"},
        {"id": "legacy-credential-alias", "severity": "migration-needed",
         "note": "Keystore and API-key service names retain old aliases to avoid invalidating existing secrets",
         "paths": ["android/src/com/rbxnative/androidport/StudioServices.java",
                   "android/src/com/rbxnative/androidport/RookPlayerActivity.java"]},
        {"id": "java-package", "severity": "migration-needed",
         "note": "Renaming com.rbxnative.androidport.experimental requires Android data migration",
         "path": "scripts/package_apk.py"},
        {"id": "debuggable", "severity": "release-blocker",
         "note": "StudioManifest defaults to debuggable; release builds must explicitly disable it",
         "path": "android/StudioManifest.xml"},
        {"id": "incomplete-agent", "severity": "feature-gap",
         "note": "MCP panel works as a connection interface; a native LLM chat is not implemented",
         "path": "docs/ANDROID_AI_AGENT.md"}
    ]
}
(OUT / "repository-inventory.json").write_text(
    json.dumps(reported, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
by_path = {}
for item in legacy_hits:
    by_path.setdefault(item["path"], []).append(item)
lines = [
    "# Auditoria automatizada do repositório RBX Studio",
    "",
    f"Arquivos rastreados e lidos do produto: {totals['product']}",
    f"Arquivos rastreados e lidos das dependências: {totals['third_party']}",
    f"Bytes examinados: {totals['read_bytes']}",
    f"Arquivos binários: {totals['binary']}",
    f"Referências legadas classificadas para revisão: {len(legacy_hits)}",
    "",
    "## Arquivos que exigem revisão de identidade",
]
for path, hits in sorted(by_path.items(), key=lambda value: -len(value[1])):
    kinds = sorted({hit["kind"] for hit in hits})
    lines.append(f"- `{path}`: {len(hits)} ocorrências ({', '.join(kinds)})")
lines.extend([
    "",
    "Ocorrências internas de dependências e avisos legais não devem ser apagadas "
    "só para ocultar proveniência. A contagem não prova falha em execução.",
    "",
    "## Questões técnicas conhecidas",
])
for issue in reported["known_risks"]:
    lines.append(f"- **{issue['severity']}**: {issue['note']}")
(OUT / "report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
print(json.dumps({
    "product_files_scanned": totals["product"],
    "third_party_files_scanned": totals["third_party"],
    "legacy_refs": len(legacy_hits),
    "potential_user_facing": totals["user_facing_candidates"],
    "risk_candidates": len(risk_hits),
}, ensure_ascii=False))
