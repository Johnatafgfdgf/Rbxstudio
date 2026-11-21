#!/usr/bin/env python3
"""Run deterministic unit contracts. Native, live API and device checks are separate."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import subprocess, sys, json, os

root = Path(__file__).resolve().parents[1]
output = Path(sys.argv[1] if len(sys.argv) > 1 else root / 'target/ai-regressions')
output.mkdir(parents=True, exist_ok=True)
tests = sorted(p for p in (root / 'scripts').glob('test_*.py')
               if p.name not in {'test_ai_live.py', 'test_ai_project_native.py', 'test_chat_popup_android.py'})

def run(test):
    try:
        result = subprocess.run([sys.executable, str(test)], cwd=root, capture_output=True,
                                timeout=180, env=dict(os.environ, _JAVA_OPTIONS='-XX:-UsePerfData'))
        (output / (test.stem + '.log')).write_bytes(result.stdout + result.stderr)
        return {'test': test.name, 'passed': result.returncode == 0, 'exit_code': result.returncode}
    except subprocess.TimeoutExpired:
        return {'test': test.name, 'passed': False, 'error': '180 second timeout'}

with ThreadPoolExecutor(max_workers=2) as pool:
    results = list(pool.map(run, tests))
report = {'scope': 'Deterministic Java/unit/static transport regressions; no live API or Android runtime',
          'passed': sum(t['passed'] for t in results), 'total': len(results), 'tests': results}
(output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report, indent=2))
sys.exit(0 if report['passed'] == len(results) else 1)
