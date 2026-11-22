#!/usr/bin/env python3
"""Opt-in R10 integration tasks. Needs an explicit Gemini key file; never runs in CI.

Uses real provider calls plus production headless DOM/compiler/save operations.
No Android renderer, socket, visual certification or Play is exercised here.
"""
from pathlib import Path
import subprocess, os, json, sys

root = Path(__file__).resolve().parents[1]
if len(sys.argv) != 3:
    raise SystemExit('key-file evidence-directory (must contain *-goal.txt)')
key, out = (Path(x).resolve() for x in sys.argv[1:])
out.mkdir(parents=True, exist_ok=True)
results = []

def test(name, goal, model, extra=None, output=None):
    env = dict(os.environ, RBX_LIVE_BUDGET='32')
    env.update(extra or {})
    command = [sys.executable, str(root/'scripts/test_ai_live.py'), str(key), model,
               str(out/(goal+'-goal.txt')), str(output or out/(name+'.rbxl')),
               str(out/(name+'.json'))]
    with (out/(name+'.log')).open('wb') as log:
        try:
            code = subprocess.run(command, cwd=root, env=env, stdout=log,
                                  stderr=subprocess.STDOUT, timeout=1100).returncode
        except subprocess.TimeoutExpired:
            code = 124
    record = {'test': name, 'exit_code': code}
    results.append(record)
    (out/'batch-results.json').write_text(json.dumps(results, indent=2)+'\n')
    print(json.dumps(record), flush=True)
    return code

if test('gui', 'gui', 'gemini-3.5-flash-lite') == 0:
    test('edit-gui', 'edit-gui', 'gemini-3.1-flash-lite', {'RBX_LIVE_INPUT': str(out/'gui.rbxl')})
test('local-rain', 'local-rain', 'gemini-3.1-flash-lite', {'RBX_LIVE_SETUP_FILE': str(out/'local-rain-setup.json')})
test('repair', 'repair', 'gemini-3.1-flash-lite', {'RBX_LIVE_INJECT_ERROR': 'true'})
state, garden = str(out/'handoff-state.json'), out/'handoff-garden.rbxl'
test('handoff-interrupted', 'handoff', 'gemini-3.5-flash-lite',
     {'RBX_LIVE_TASK_STATE': state, 'RBX_LIVE_STOP_AFTER_WRITES': '1'}, garden)
if garden.exists():
    test('handoff-resumed', 'handoff', 'gemini-3.1-flash-lite',
         {'RBX_LIVE_TASK_STATE': state, 'RBX_LIVE_RESUME': 'true', 'RBX_LIVE_INPUT': str(garden)}, garden)
