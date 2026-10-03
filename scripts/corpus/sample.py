#!/usr/bin/env python3
"""Draw a random sample of one tool's findings from a compare.py JSON and print them with source context.
usage: sample.py CMP.json CLONES_DIR atr|atl N SEED"""
import json, random, sys, os
cmp, clones, tool, n, seed = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]), int(sys.argv[5])
d = json.load(open(cmp))
pool = []
for rule, v in d.items():
    for cls in ("both", tool + "_only"):
        for x in v.get(cls, []):
            pool.append((rule, cls, x))
pool.sort(key=lambda t: (t[0], t[1], json.dumps(t[2])))
random.Random(seed).shuffle(pool)
print("pool size", len(pool))
for i, (rule, cls, x) in enumerate(pool[:n], 1):
    repo, f, line = x[0], x[1], x[2]
    p = os.path.join(clones, repo, f)
    print("\n#%d [%s] %s  %s %s:%s" % (i, cls, rule, repo, f, line))
    try:
        L = open(p, errors="ignore").read().split("\n")
        for k in range(max(0, line - 3), min(len(L), line + 2)):
            print("%s%5d| %s" % (">" if k + 1 == line else " ", k + 1, L[k][:150]))
    except Exception as e:
        print("  (unreadable)", e)
