# Cross-check against android-target-ready (corpus comparison)

Two independent implementations of the same six checks were run over the same public repositories and their findings were paired. Disagreements were read by hand and every bug found was fixed in the tool that had it. **Agreement between two tools is not accuracy**: both can be wrong in the same way, and nothing here measures recall or precision against ground truth.

## Method

- Corpus: 100 public Android repositories cloned shallowly (`scripts/corpus/repos.txt`, commits in `scripts/corpus/clone-commits.txt`). 54 were analysed with both tools: every repository where [android-target-ready](https://github.com/cosmichackerx/android-target-ready) reported any of the six overlapping rules (39) plus 15 random others (`scripts/corpus/analysed-repos.txt`). The selection is biased toward repositories that have findings, so the numbers below say nothing about how often the rules fire in general.
- android-target-ready 0.2.0 plus its fixes from this comparison (main at the time of the run), pattern based, no type resolution.
- android-target-lint: the CI-built jar from main (a 0.2.2 snapshot, before the `setOnKeyListener` unresolved-receiver fix), run through the **Lint command line**, not Gradle (`scripts/corpus/run_atl.py`).
- Harness limits, which lower atl's recall here:
  - No dependency jars, only `android.jar` (API 36) and four AndroidX jars. Classes that extend a base class from another module or library cannot be resolved, so `onBackPressed` overrides in them are not seen.
  - Modules are found only through `src/main/AndroidManifest.xml`. Kotlin Multiplatform `androidMain` source sets and old project layouts are not analysed (14 atr findings fall in such files and are excluded from the agreement figures).
  - Test source sets are skipped. Two Kotlin files in Thunderbird crash Lint's own Kotlin analysis (`EnumEntries`/JDK 1.7), and `threema-android` and `NeriPlayer` timed out (600 s) and are excluded.

## Result (52 repositories with valid output from both)

| rule (atr / atl) | both | atr only | atl only | agreement |
|---|---|---|---|---|
| back-pressed-override / OnBackPressedOverride | 44 | 10 | 0 | 81 % |
| back-keycode / KeyCodeBackHandling | 20 | 33 | 2 | 36 % |
| edge-to-edge-opt-out / EdgeToEdgeOptOut | 5 | 0 | 0 | 100 % |
| back-opt-out / PredictiveBackOptOut | 20 | 0 | 0 | 100 % |
| fixed-orientation / FixedOrientationManifest | 28 | 0 | 0 | 100 % |
| set-requested-orientation / FixedOrientationCode | 9 | 4 | 3 | 56 % |
| **all** | **126** | **47** | **5** | **71 %** |

Agreement is the Jaccard index over findings paired by file with a line tolerance of ±1.

## What the disagreements were

Bugs found and fixed:

- atl: `OnBackPressedOverride` missed activities extending AndroidX base classes (it looked only at the direct super method); `KeyCodeBackHandling` ignored `OnKeyListener.onKey` and `setOnKeyListener` lambdas, and lambdas on a receiver whose type does not resolve; `FixedOrientationCode` ignored conditional arguments (`if`/ternary/`when`).
- atr: an edge-to-edge opt-out with an attribute such as `tools:targetApi` between the name and value was missed; `requestedOrientation == X` was treated as an assignment; the lexer treated a Java banner comment `/*////…////*/` as a nested comment and blanked the rest of the file (NewPipe's `MainActivity`). The last one is a recall bug that probably also affected earlier atr runs on Java files with such banners.

Remaining differences, by cause:

- **OnBackPressedOverride, atr only (10):** the activity extends a base class in another module or library that the harness cannot resolve (for example Thanox's `ThemeActivity`, an external `FilePickerActivity`). atl resolves types, so with dependency jars it should report these; this is a harness limit, not a detector difference (not re-verified with dependencies).
- **KeyCodeBackHandling, atr only (33):** mostly outside atl's deliberate scope: `View.onKeyDown` overrides, services/overlays/fragments, and key-mapping tables (terminal emulators, key-remapper apps). atl does not report them on purpose.
- **KeyCodeBackHandling, atl only (2):** SmartTube's leanback `OnboardingFragment`, `OnKeyListener.onKey` in an anonymous class (`return keyCode != KEYCODE_BACK`). A real use that the pattern scanner does not look for.
- **FixedOrientationCode, atl only (3):** conditional/`when` assignments (Kvaesitso, mpv-android) that the pattern scanner does not follow.
- **FixedOrientationCode, atr only (4):** an assignment in a non-Activity class (Telegram's `CameraController`, probably an atr false positive since it does not know the receiver) and safe-call assignments (`activity?.requestedOrientation = …`).

## Reproduce

```
cd scripts/corpus
./setup.sh                                   # Lint CLI classpath + AndroidX jars (JDK 17, Android SDK with android-36)
export WORK=$PWD/work; mkdir -p $WORK/clones
xargs -n1 -P4 ./clone.sh < repos.txt         # default branch at clone time; clone-commits.txt lists the commits used
ATL_JAR=/path/to/android-target-lint.jar ./runall.sh
```
