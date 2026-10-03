#!/usr/bin/env python3
"""Compare android-target-ready JSON output with android-target-lint Lint XML output over the same clones.
usage: compare.py ATR_OUT_DIR ATL_OUT_DIR CLONES_DIR [--json out.json]"""
import json, os, sys, glob, collections
import xml.etree.ElementTree as ET

PAIRS = {  # atr rule -> atl issue
    "back-pressed-override": "OnBackPressedOverride",
    "back-keycode": "KeyCodeBackHandling",
    "edge-to-edge-opt-out": "EdgeToEdgeOptOut",
    "back-opt-out": "PredictiveBackOptOut",
    "fixed-orientation": "FixedOrientationManifest",
    "set-requested-orientation": "FixedOrientationCode",
}
REV = {v: k for k, v in PAIRS.items()}

def load_atl(path, root):
    out = set()
    try:
        tree = ET.parse(path)
    except Exception:
        return None
    for iss in tree.getroot().iter("issue"):
        rid = iss.get("id")
        if rid not in REV: continue
        loc = iss.find("location")
        if loc is None: continue
        f = os.path.normpath(loc.get("file", ""))
        # the Lint CLI writes paths relative to its working directory (the directory of the project.xml)
        f = os.path.relpath(os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(path)), f)), root)
        out.add((REV[rid], f.replace(os.sep, "/"), int(loc.get("line", "0"))))
    return out

def analysed_files(xf, root):
    """Files the harness handed to Lint (from the project.xml next to the report) plus resource dirs and manifests."""
    pf = xf + ".project.xml"
    files, dirs = set(), []
    if not os.path.exists(pf): return files, dirs
    for el in ET.parse(pf).getroot().iter():
        f = el.get("file")
        if not f: continue
        rel = os.path.relpath(f, root).replace(os.sep, "/")
        (files if el.tag in ("src", "manifest") else dirs).append(rel) if el.tag != "src" and el.tag != "manifest" else files.add(rel)
    return files, dirs

def main():
    atr_dir, atl_dir, clones = sys.argv[1:4]
    res = collections.defaultdict(lambda: collections.defaultdict(list))
    skipped = []
    for jf in sorted(glob.glob(os.path.join(atr_dir, "*.json"))):
        b = os.path.basename(jf)[:-5]
        xf = os.path.join(atl_dir, b + ".xml")
        if not os.path.exists(xf):
            skipped.append(b); continue
        atl = load_atl(xf, os.path.join(clones, b))
        if atl is None: skipped.append(b); continue
        atr = set()
        for x in json.load(open(jf)).get("findings", []):
            if x["rule"] in PAIRS: atr.add((x["rule"], x["file"], x["line"]))
        used = set()
        files, dirs = analysed_files(xf, os.path.join(clones, b))
        def analysed(f): return f in files or any(f.startswith(d + "/") for d in dirs)
        for k in sorted(atr):
            if not analysed(k[1]):
                res[k[0]]["not_analysed"].append((b,) + k[1:]); continue
            m = next((a for a in sorted(atl) if a[0] == k[0] and a[1] == k[1] and abs(a[2] - k[2]) <= 1 and a not in used), None)
            if m: used.add(m); res[k[0]]["both"].append((b,) + k[1:])
            else: res[k[0]]["atr_only"].append((b,) + k[1:])
        for a in sorted(atl - used): res[a[0]]["atl_only"].append((b,) + a[1:])
    tot = collections.Counter()
    print("| rule | both | atr only | atl only | agreement (Jaccard) | atr findings in files the harness did not analyse |\n|---|---|---|---|---|---|")
    for r in PAIRS:
        d = res[r]; b, a, l = len(d["both"]), len(d["atr_only"]), len(d["atl_only"])
        tot.update(both=b, atr_only=a, atl_only=l)
        na = len(d["not_analysed"]); tot.update(not_analysed=na)
        print("| %s / %s | %d | %d | %d | %s | %d |" % (r, PAIRS[r], b, a, l, "%.0f%%" % (100 * b / (b + a + l)) if b + a + l else "-", na))
    b, a, l = tot["both"], tot["atr_only"], tot["atl_only"]
    print("| **all** | %d | %d | %d | %.0f%% | %d |" % (b, a, l, 100 * b / max(1, b + a + l), tot["not_analysed"]))
    print("\nrepos skipped (no/invalid atl output):", len(skipped), skipped[:10])
    if "--json" in sys.argv:
        json.dump({r: dict(d) for r, d in res.items()}, open(sys.argv[sys.argv.index("--json") + 1], "w"), indent=1)
main()
