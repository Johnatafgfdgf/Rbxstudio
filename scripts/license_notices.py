#!/usr/bin/env python3
"""Include actual dependency license/copyright files in the debug APK."""
import json
import os
import pathlib
import subprocess

root=pathlib.Path(__file__).resolve().parents[1]
cargo=os.environ.get("CARGO","cargo")
metadata=json.loads(subprocess.check_output([str(cargo),"metadata","--locked","--format-version","1","--filter-platform",os.environ.get("PORT_ARCH","aarch64-linux-android")],cwd=root))
used={n['id'] for n in metadata['resolve']['nodes']}
licenses=[];texts=[];missing=[]
for p in sorted(metadata['packages'],key=lambda p:(p['name'],p['version'])):
    if p['id'] not in used:continue
    licenses.append({'name':p['name'],'version':p['version'],'license':p['license'],'repository':p.get('repository')})
    directory=pathlib.Path(p['manifest_path']).parent
    files=[f for f in directory.iterdir() if f.is_file() and f.name.upper().startswith(('LICENSE','LICENCE','COPYING','NOTICE','COPYRIGHT'))]
    if p['name'] in ('luau0-src','mlua-sys','ring','zstd-sys','libz-sys','lz4-sys'):
        files.extend(f for f in directory.rglob('*') if f.is_file() and f.name.upper().startswith(('LICENSE','LICENCE','COPYING','NOTICE','COPYRIGHT')) and f not in files)
    if p.get('license_file'):
        f=directory/p['license_file']
        if f.is_file() and f not in files:files.append(f)
    # Workspace crates inherit their upstream's license.
    if not files and 'upstream' in directory.parts:
        for parent in directory.parents:
            files=[f for f in parent.iterdir() if f.is_file() and f.name.upper().startswith(('LICENSE','LICENCE','COPYING','NOTICE'))]
            if files or parent==root:break
    if not files:
        files=list((root/'docs/license-sources').glob(p['name']+'-'+p['version']+'--*'))
    if not files and p['name'] in ('rbx_android','rbx_android_core','rbx_android_studio','rbx_android_player'):
        files=[root/'LICENSE']
    if not files and p['name']=='rook_geometry_bridge':
        files=[root/'upstream/rook/LICENSE',root/'upstream/rook/NOTICE']
    if not files:missing.append(p['name']+' '+p['version'])
    texts.append('\n\n===== '+p['name']+' '+p['version']+' | '+str(p['license'])+' =====\n')
    for f in sorted(files):texts.append('\n'+f.name+'\n'+f.read_text(errors='replace'))
out=root/'android/build/assets';out.mkdir(parents=True,exist_ok=True)
(out/'DEPENDENCY_LICENSES.txt').write_text(''.join(texts))
(out/'dependency-licenses.json').write_text(json.dumps(licenses,indent=2)+'\n')
print(json.dumps({'packages':len(licenses),'license_file_missing':missing,'bytes':sum(map(len,texts))}))
