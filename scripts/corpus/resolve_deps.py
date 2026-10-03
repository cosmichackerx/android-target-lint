#!/usr/bin/env python3
"""Best-effort dependency jars for a cloned Android repo, without running Gradle.

Reads `group:artifact:version` literals from build.gradle(.kts) and libs.versions.toml, downloads the .aar/.jar
from Google Maven / Maven Central into a shared cache and returns the class jars.
Direct dependencies only (no transitive resolution, no variables, no BOMs, no JitPack): unresolved ones are reported."""
import os, re, sys, zipfile, urllib.request, urllib.error, concurrent.futures as cf

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.environ.get("DEPS_CACHE", os.path.join(HERE, "m2"))
REPOS = ["https://dl.google.com/dl/android/maven2", "https://repo1.maven.org/maven2"]
SKIP_DIRS = {".git", "node_modules", "build", ".gradle"}
COORD = re.compile(r"""["']([A-Za-z0-9_.\-]+):([A-Za-z0-9_.\-]+):([0-9][A-Za-z0-9_.\-]*)(?:@\w+)?["']""")

def vkey(v):
    return [int(p) if p.isdigit() else -1 for p in re.split(r"[.\-]", v)]

def toml_coords(text):
    versions = dict(re.findall(r'^\s*([\w\-.]+)\s*=\s*"([^"]+)"', text.split("[libraries]")[0], re.M)) if "[libraries]" in text else {}
    out = []
    lib = text.split("[libraries]", 1)[1].split("\n[", 1)[0] if "[libraries]" in text else ""
    for line in lib.splitlines():
        m = re.search(r'module\s*=\s*"([^":]+):([^":]+)"', line) or None
        g = a = None
        if m: g, a = m.group(1), m.group(2)
        else:
            mg, mn = re.search(r'group\s*=\s*"([^"]+)"', line), re.search(r'name\s*=\s*"([^"]+)"', line)
            if mg and mn: g, a = mg.group(1), mn.group(1)
        if not g: continue
        v = None
        mr = re.search(r'version(?:\.ref)?\s*=\s*"([^"]+)"', line)
        if mr:
            v = versions.get(mr.group(1), mr.group(1)) if "version.ref" in line else mr.group(1)
        else:
            ms = re.search(r'=\s*"[^":]+:[^":]+:([^"]+)"', line)
            if ms: v = ms.group(1)
        if v and re.match(r"[0-9]", v): out.append((g, a, v))
    for m in COORD.finditer(lib): out.append(m.groups())
    return out

def find_coords(root):
    found = {}
    for dp, dns, fns in os.walk(root):
        dns[:] = [d for d in dns if d not in SKIP_DIRS]
        for fn in fns:
            p = os.path.join(dp, fn)
            try:
                if fn.endswith((".gradle", ".gradle.kts")):
                    txt = open(p, errors="ignore").read()
                    cs = [m.groups() for m in COORD.finditer(txt)]
                elif fn.endswith(".toml"):
                    cs = toml_coords(open(p, errors="ignore").read())
                else: continue
            except OSError: continue
            for g, a, v in cs:
                if (g, a) not in found or vkey(v) > vkey(found[(g, a)]): found[(g, a)] = v
    return found

def get(url, dest):
    try:
        with urllib.request.urlopen(url, timeout=30) as r, open(dest + ".part", "wb") as f:
            f.write(r.read())
        os.replace(dest + ".part", dest); return True
    except (urllib.error.URLError, OSError): return False

def fetch(g, a, v):
    d = os.path.join(CACHE, g, a, v); os.makedirs(d, exist_ok=True)
    jar = os.path.join(d, a + ".jar")
    if os.path.exists(jar): return jar if os.path.getsize(jar) else None
    if os.path.exists(jar + ".missing"): return None
    for repo in REPOS:
        base = "%s/%s/%s/%s/%s-%s" % (repo, g.replace(".", "/"), a, v, a, v)
        tmp = os.path.join(d, a + ".aar")
        if get(base + ".aar", tmp):
            try:
                with zipfile.ZipFile(tmp) as z:
                    open(jar, "wb").write(z.read("classes.jar") if "classes.jar" in z.namelist() else b"")
                os.remove(tmp)
            except zipfile.BadZipFile: continue
            return jar if os.path.getsize(jar) else None
        if get(base + ".jar", jar): return jar
    open(jar + ".missing", "w").close()
    return None

def resolve(root, workers=8):
    coords = find_coords(root)
    todo = [(k, v) for k, v in coords.items() if k[0] != "org.jetbrains.kotlin"]  # Kotlin jars newer than the Lint CLI's compiler break its analysis
    jars, missing = [], []
    def one(item):
        (g, a), v = item
        r = fetch(g, a, v)
        if r is None and g.startswith("androidx."):  # Kotlin Multiplatform artifacts publish the Android classes as <artifact>-android
            r = fetch(g, a + "-android", v)
        return item, r
    with cf.ThreadPoolExecutor(workers) as ex:
        for item, r in ex.map(one, todo):
            (jars if r else missing).append(r or "%s:%s:%s" % (item[0][0], item[0][1], item[1]))
    return jars, missing

if __name__ == "__main__":
    j, m = resolve(sys.argv[1])
    print(len(j), "jars,", len(m), "unresolved")
