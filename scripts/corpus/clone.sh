#!/bin/bash
r="$1"; d="${WORK:-work}/clones/${r//\//__}"
[ -d "$d" ] || GIT_TERMINAL_PROMPT=0 timeout 240 git clone -q --depth 1 "https://github.com/$r.git" "$d" 2>/dev/null || rm -rf "$d"
