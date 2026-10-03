#!/bin/bash
# Like runall.sh, but resolves dependency jars (DEPS=1: resolve_deps.py + project(":x") module edges) before running atl.
# Layout under $WORK (default ./work): clones/<owner>__<repo>, out-atl-deps/, out-atr/. Optional LINT_CONFIG=lint-checkviews.xml.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"; WORK="${WORK:-$PWD/work}"; : "${ATL_JAR:?set ATL_JAR to the android-target-lint jar}"
export DEPS=1 DEPS_CACHE="${DEPS_CACHE:-$WORK/m2}"
mkdir -p "$WORK/out-atl-deps" "$WORK/out-atr"
while read b; do
  [ -d "$WORK/clones/$b" ] || continue
  [ -s "$WORK/out-atl-deps/$b.xml" ] || TO=600 python3 "$HERE/run_atl.py" "$WORK/clones/$b" "$WORK/out-atl-deps/$b.xml" --mem 1200 > "$WORK/out-atl-deps/$b.run" 2>&1 || echo "atl failed: $b"
  timeout 300 python3 -m android_target_ready "$WORK/clones/$b" -f json --fail-on never > "$WORK/out-atr/$b.json" 2> "$WORK/out-atr/$b.err" || true
done < "$HERE/analysed-repos.txt"
python3 "$HERE/compare.py" "$WORK/out-atr" "$WORK/out-atl-deps" "$WORK/clones" --json "$WORK/cmp-deps.json"
# hand-check sample: python3 "$HERE/sample.py" "$WORK/cmp-deps.json" "$WORK/clones" atr|atl 40 7
