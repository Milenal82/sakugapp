#!/usr/bin/env bash
# Native instrumentation on a dedicated CI AVD; prebuilt Debug APKs only.
set -euo pipefail
classes="${1:?test classes}"; log="${2:?output log}"; label="${3:-}"
mkdir -p "$(dirname "$log")"
args=(-w -r -e class "$classes")
if [[ -n "$label" ]]; then args+=(-e profileLabel "$label"); fi
status=0
adb shell am instrument "${args[@]}" com.sakugapp.test/androidx.test.runner.AndroidJUnitRunner > "$log" 2>&1 || status=$?
cat "$log"
[[ "$status" == 0 ]]
# am instrument itself can return zero for JUnit failures: require the runner's success summary.
python3 - "$log" <<'PY'
import pathlib,re,sys
log=pathlib.Path(sys.argv[1]).read_text(errors='replace')
assert re.search(r'^OK \([1-9][0-9]* tests?\)\s*$',log,re.M), 'No successful JUnit summary; inspect instrumentation log'
assert 'FAILURES!!!' not in log and 'INSTRUMENTATION_FAILED' not in log, 'Instrumentation failed'
assert re.search(r'^INSTRUMENTATION_CODE: -1\s*$',log,re.M), 'Runner did not finish normally'
PY
