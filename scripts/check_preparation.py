#!/usr/bin/env python3
"""Verify that clean and repeated preparations produce identical sources."""
import hashlib
import io
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parents[1]

def prepare():
    subprocess.run([sys.executable, str(ROOT / 'scripts/prepare.py')], cwd=ROOT, check=True)
    return {str(path.relative_to(ROOT)): (hashlib.sha256(path.read_bytes()).hexdigest(), path.stat().st_mtime_ns)
            for folder in ['core/src/upstream', 'geometry/src/upstream']
            for path in (ROOT / folder).rglob('*') if path.is_file()}

first = prepare()
second = prepare()
if first != second:
    changed = sorted(name for name in first.keys() | second.keys() if first.get(name) != second.get(name))
    raise SystemExit('Repeated source preparation rewrote or changed: ' + ', '.join(changed))
if 'gpui_mobile' in (ROOT / 'core/src/upstream/save.rs').read_text():
    raise SystemExit('The standalone document adapter contains a GPUI host dependency')

# A reverse check in an already patched workspace cannot detect a newly
# added module accidentally omitted when regenerating a consolidated patch.
# Apply every consolidated patch to pristine pinned sources and compare all
# changed/new files against the actual working sources, including untracked
# files. Missing files fail here, before a costly native compilation.
for patch in sorted((ROOT / 'patches').glob('*--android-port.patch')):
    repo = ROOT / 'upstream' / patch.name.split('--', 1)[0]
    tracked = subprocess.check_output(['git', '-C', str(repo), 'diff', '--name-only', 'HEAD', '-z'])
    untracked = subprocess.check_output(['git', '-C', str(repo), 'ls-files', '--others', '--exclude-standard', '-z'])
    changed = {Path(p.decode()) for p in (tracked + untracked).split(b'\0') if p}
    archive = subprocess.check_output(['git', '-C', str(repo), 'archive', 'HEAD'])
    with tempfile.TemporaryDirectory(prefix='android-clean-source-') as folder:
        clean = Path(folder)
        with tarfile.open(fileobj=io.BytesIO(archive)) as source:
            source.extractall(clean, filter='data')
        subprocess.run(['git', '-C', str(clean), 'apply', str(patch)], check=True)
        missing = []
        for relative in sorted(changed):
            actual, rebuilt = repo / relative, clean / relative
            if actual.is_file() != rebuilt.is_file() or (actual.is_file() and actual.read_bytes() != rebuilt.read_bytes()):
                missing.append(str(relative))
        if missing: raise SystemExit(f'{repo.name} clean patch differs: ' + ', '.join(missing))
        print(f'Verified {repo.name}: {len(changed)} changed/new files match clean patch application')
print(f'Verified {len(first)} identical generated files and unchanged timestamps after repeated preparation')
