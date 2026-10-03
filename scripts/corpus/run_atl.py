#!/usr/bin/env python3
"""Run the android-target-lint jar over a cloned repo with the Lint command-line tool (no Gradle build).

Builds a Lint project.xml with one module per src/main/AndroidManifest.xml, every non-test source set,
android.jar plus a few AndroidX class jars on the classpath, then writes an XML report.
usage: run_atl.py REPO_DIR OUT_XML [--mem 700]"""
import os, subprocess, sys, glob
from xml.sax.saxutils import quoteattr

HERE = os.path.dirname(os.path.abspath(__file__))
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

def project_xml(root):
    jars = [os.path.join(SDK, "platforms", "android-36", "android.jar")] + [j for j in sorted(glob.glob(os.path.join(HERE, "libs", "*.jar"))) if os.path.getsize(j)]
    out = ["<project>", "  <sdk dir=%s/>" % quoteattr(SDK)]
    n = 0
    for m in modules(root):
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
    env = dict(os.environ, ANDROID_LINT_JARS=JAR)
    r = subprocess.run(cmd, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=int(os.environ.get("TO", "600")))
    open(xml + ".log", "w").write(r.stdout)
    print(n, "modules, exit", r.returncode)
if __name__ == "__main__":
    main()
