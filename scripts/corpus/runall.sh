#!/bin/bash
# Run android-target-lint (jar in $ATL_JAR) and android-target-ready over the analysed repos, then compare.
# Layout under $WORK (default ./work): clones/<owner>__<repo>, out-atl/, out-atr/.
# Needs: clones (see clone.sh + repos.txt), setup.sh done, python3, `pip install android-target-ready` (or PYTHONPATH=<atr>/src).
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"; WORK="${WORK:-$PWD/work}"; : "${ATL_JAR:?set ATL_JAR to the android-target-lint jar}"
mkdir -p "$WORK/out-atl" "$WORK/out-atr"
while read b; do
  [ -d "$WORK/clones/$b" ] || continue
  [ -s "$WORK/out-atl/$b.xml" ] || TO=600 python3 "$HERE/run_atl.py" "$WORK/clones/$b" "$WORK/out-atl/$b.xml" > "$WORK/out-atl/$b.run" 2>&1 || echo "atl failed: $b"
  timeout 300 python3 -m android_target_ready "$WORK/clones/$b" -f json --fail-on never > "$WORK/out-atr/$b.json" 2> "$WORK/out-atr/$b.err" || true
done < "$HERE/analysed-repos.txt"
python3 "$HERE/compare.py" "$WORK/out-atr" "$WORK/out-atl" "$WORK/clones" --json "$WORK/cmp.json"
