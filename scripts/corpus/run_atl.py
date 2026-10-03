#!/usr/bin/env python3
"""Run the android-target-lint jar over a cloned repo with the Lint command-line tool (no Gradle build).

Builds a Lint project.xml with one module per src/main/AndroidManifest.xml, every non-test source set,
android.jar plus a few AndroidX class jars on the classpath, then writes an XML report.
usage: run_atl.py REPO_DIR OUT_XML [--mem 700]"""
import os, subprocess, sys, glob
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from xml.sax.saxutils import quoteattr

HERE = os.path.dirname(os.path.abspath(__file__))
DEPS = os.environ.get("DEPS") == "1"
SDK = os.environ.get("ANDROID_HOME", "/home/box/tools/android-sdk")
JAR = os.environ.get("ATL_JAR") or sorted(glob.glob(os.path.join(HERE, "atl-*.jar")))[-1]
IDS = "OnBackPressedOverride,KeyCodeBackHandling,EdgeToEdgeOptOut,PredictiveBackOptOut,FixedOrientationManifest,FixedOrientationCode"
SKIP = {".git", "node_modules", "build", ".gradle", "testData", "test-data"}
TESTSETS = lambda n: n.startswith(("test", "androidTest", "sharedTest", "androidDeviceTest", "testFixtures"))

def modules(root):
    found = []
    for dp, dns, fns in os.walk(root):
        dns[:] = [d for d in dns if d not in SKIP]
        if os.path.basename(dp) == "main" and "AndroidManifest.xml" in fns and os.path.basename(os.path.dirname(dp)) == "src":
            found.append(os.path.dirname(os.path.dirname(dp)))
    return found

def resolve_name(path, byname):
    """Map a Gradle project path (':a:b') to a module directory: exact relative path, else a unique module whose path ends with it
    (the Gradle root may be a subdirectory, e.g. android/), else a unique module whose last segment matches."""
    cand = path.strip(":").replace(":", "/")
    if cand in byname: return cand
    suf = [n for n in byname if n.endswith("/" + cand)]
    if len(suf) == 1: return suf[0]
    last = cand.rsplit("/", 1)[-1]
    suf = [n for n in byname if n.rsplit("/", 1)[-1] == last]
    return suf[0] if len(suf) == 1 else None

def project_edges(root, names):
    """Module -> modules it depends on, read from project(':a:b') in its build file; cycles are broken (back edges dropped)."""
    import re
    byname = set(names)
    raw = {}
    for n in names:
        d = os.path.join(root, n) if n != "root" else root
        deps = set()
        for bf in ("build.gradle", "build.gradle.kts"):
            p = os.path.join(d, bf)
            if os.path.isfile(p):
                txt = open(p, errors="ignore").read()
                for m in re.finditer(r"""project\(\s*(?:path\s*[:=]\s*)?[\"']:?([\w:.\-]+)[\"']""", txt):
                    cand = resolve_name(m.group(1), byname)
                    if cand and cand != n: deps.add(cand)
        raw[n] = sorted(deps)
    state, out = {}, {n: [] for n in names}
    def visit(n):
        state[n] = 1
        for d in raw[n]:
            if state.get(d) == 1: continue          # back edge: would create a cycle
            out[n].append(d)
            if d not in state: visit(d)
        state[n] = 2
    for n in names:
        if n not in state: visit(n)
    return out

def project_xml(root):
    jars = [os.path.join(SDK, "platforms", "android-36", "android.jar")] + [j for j in sorted(glob.glob(os.path.join(HERE, "libs", "*.jar"))) if os.path.getsize(j)]
    out = ["<project>", "  <sdk dir=%s/>" % quoteattr(SDK)]
    n = 0
    mods = modules(root)
    names = [os.path.relpath(m, root) or "root" for m in mods]
    if DEPS:
        import resolve_deps
        dj, missing = resolve_deps.resolve(root)
        jars = jars + dj
        sys.stderr.write("deps: %d jars, %d unresolved\n" % (len(dj), len(missing)))
    edges = project_edges(root, names) if DEPS else {}
    for m in mods:
        src = os.path.join(m, "src")
        sets = [s for s in sorted(os.listdir(src)) if not TESTSETS(s)]
        manifests = [os.path.join(src, s, "AndroidManifest.xml") for s in sets if os.path.isfile(os.path.join(src, s, "AndroidManifest.xml"))]
        out.append('  <module name=%s android="true" library="false" compile-sdk-version="36">' % quoteattr(os.path.relpath(m, root) or "root"))
        for mf in sorted(manifests, key=lambda p: "/main/" not in p)[:1]:
            out.append("    <manifest file=%s/>" % quoteattr(mf))
        for s in sets:
            for sub in ("java", "kotlin"):
                d = os.path.join(src, s, sub)
                for dp, dns, fns in os.walk(d):
                    dns[:] = [x for x in dns if x not in SKIP]
                    for fn in sorted(fns):
                        if fn.endswith((".java", ".kt")):
                            out.append("    <src file=%s/>" % quoteattr(os.path.join(dp, fn)))
            d = os.path.join(src, s, "res")
            if os.path.isdir(d): out.append("    <resource file=%s/>" % quoteattr(d))
        for j in jars: out.append("    <classpath jar=%s/>" % quoteattr(j))
        if DEPS:  # modules this one declares as project(":x") dependencies (acyclic), so base classes defined there resolve
            for other in edges.get(os.path.relpath(m, root) or "root", ()):
                out.append("    <dep module=%s/>" % quoteattr(other))
        out.append("  </module>")
        n += 1
    out.append("</project>")
    return "\n".join(out), n

def main():
    root, xml = os.path.abspath(sys.argv[1]), os.path.abspath(sys.argv[2])
    mem = sys.argv[sys.argv.index("--mem") + 1] if "--mem" in sys.argv else "700"
    px, n = project_xml(root)
    if n == 0:
        open(xml, "w").write('<?xml version="1.0" encoding="UTF-8"?>\n<issues format="6"><!-- no modules --></issues>\n'); print("0 modules"); return
    pf = xml + ".project.xml"
    open(pf, "w").write(px)
    cmd = ["java", "-Xmx%sm" % mem, "-Djava.awt.headless=true", "-cp", os.path.join(HERE, "cp", "*"), "com.android.tools.lint.Main",
           "--project", pf, "--check", IDS, "--xml", xml, "--quiet", "--nowarn" if False else "--exitcode"]
    cmd = [c for c in cmd if c != "--exitcode"]
    if os.environ.get("LINT_CONFIG"): cmd += ["--config", os.environ["LINT_CONFIG"]]
    env = dict(os.environ, ANDROID_LINT_JARS=JAR)
    r = subprocess.run(cmd, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=int(os.environ.get("TO", "600")))
    open(xml + ".log", "w").write(r.stdout)
    print(n, "modules, exit", r.returncode)
if __name__ == "__main__":
    main()
