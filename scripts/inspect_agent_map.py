#!/usr/bin/env python3
"""Read back a saved test map through the production headless DOM adapter.

No provider calls, renderer or Play. Source reads are limited to 120 lines.
The input file is never saved or modified by this inspection.
"""
from pathlib import Path
import subprocess, sys, os, json, hashlib, tempfile

root = Path(__file__).resolve().parents[1]
if len(sys.argv) != 3:
    raise SystemExit('input.rbxl report.json')
source, output = (Path(p).resolve() for p in sys.argv[1:])
with tempfile.TemporaryDirectory(prefix='rbx-map-readback-') as temp:
    env = dict(os.environ, RBX_LIVE_INPUT=str(source),
               RBX_LIVE_PLACE=str(Path(temp)/'unused.rbxl'), CARGO_NET_OFFLINE='true')
    env.pop('RBX_LIVE_SETUP_FILE', None)
    env.pop('RBX_LIVE_INJECT_ERROR', None)
    process = subprocess.Popen([sys.executable, str(root/'scripts/test_ai_project_native.py'), '--bridge'],
                               cwd=root, env=env, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               text=True)
    def call(tool, arguments):
        process.stdin.write(json.dumps({'tool': tool, 'arguments': arguments})+'\n')
        process.stdin.flush()
        line = process.stdout.readline()
        if not line:
            raise RuntimeError('Readback bridge closed')
        result = json.loads(line)
        if 'error' in result:
            raise RuntimeError(result['error'])
        return result['result']
    try:
        tree = call('search_game_tree', {'max_depth': 16, 'head_limit': 1000})
        scripts = []
        for node in tree:
            if node.get('className') in ('Script', 'LocalScript', 'ModuleScript'):
                path = node['fullPath']
                try:
                    scripts.append({'path': path, 'source_read': call('script_read', {'target_file': path})})
                except RuntimeError as error:
                    scripts.append({'path': path, 'read_error': str(error)})
        selected = []
        for node in tree:
            if node.get('className') in ('Tool', 'ParticleEmitter', 'ProximityPrompt', 'ScreenGui', 'RemoteEvent'):
                selected.append(call('inspect_instance', {'path': node['fullPath']}))
        report = {'input': source.name, 'sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
                  'scope': 'Binary reopen and production DOM/source/syntax readback. No visual, Play, account or provider test.',
                  'source_read_line_limit': 120, 'tree': tree, 'scripts': scripts,
                  'selected_instances': selected, 'diagnostics': call('android_project_diagnostics', {})}
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
        print(json.dumps({'objects': len(tree), 'scripts': len(scripts), 'report': str(output)}))
    finally:
        process.stdin.close()
        process.wait(timeout=30)
