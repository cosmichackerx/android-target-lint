# Cross-check against android-target-ready (corpus comparison)

This page has two runs: the first without dependency jars, the second (below) with direct dependencies resolved and a hand-checked precision sample. Two independent implementations of the same six checks were run over the same public repositories and their findings were paired. Disagreements were read by hand and every bug found was fixed in the tool that had it. **Agreement between two tools is not accuracy**: both can be wrong in the same way, and nothing here measures recall or precision against ground truth.

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

- **OnBackPressedOverride, atr only (10):** the activity extends a base class in another module or library that the harness cannot resolve (for example Thanox's `ThemeActivity`, an external `FilePickerActivity`). atl resolves types, so with dependency jars it should report these; this is a harness limit, not a detector difference. Re-checked with dependency jars: see the second run below (3 of the 10 now agree).
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

---

# Second run: dependency jars resolved, precision sampled

What changed: `resolve_deps.py` reads the `group:artifact:version` literals of `build.gradle(.kts)` and `libs.versions.toml`, downloads the jar/aar of each (Google Maven and Maven Central only; direct dependencies only, no BOMs, no variables, no JitPack, no `org.jetbrains.kotlin` jars) and hands them to Lint. `project(":x")` dependencies become module edges in Lint's `project.xml` (cycles are broken, so one of two mutually dependent modules loses its edge). Run with the released atl 0.2.1 jar (includes the `setOnKeyListener` fix) and atr main after two fixes found in this run (`requestedOrientation` local variable, `!= KEYCODE_BACK`). Scripts: `scripts/corpus/runall-deps.sh`, `resolve_deps.py`, `sample.py`.

The number of unresolved dependencies per repository is in each `*.run` log (typically 0-11; JitPack and snapshot dependencies are never resolved).

## Result (54 repositories, same selection as above)

| rule (atr / atl) | both | atr only | atl only | agreement |
|---|---|---|---|---|
| back-pressed-override / OnBackPressedOverride | 47 | 7 | 0 | 87 % |
| back-keycode / KeyCodeBackHandling | 26 | 37 | 0 | 41 % |
| edge-to-edge-opt-out / EdgeToEdgeOptOut | 5 | 0 | 0 | 100 % |
| back-opt-out / PredictiveBackOptOut | 20 | 0 | 0 | 100 % |
| fixed-orientation / FixedOrientationManifest | 28 | 0 | 0 | 100 % |
| set-requested-orientation / FixedOrientationCode | 9 | 1 | 4 | 64 % |
| **all** | **135** | **45** | **4** | **73 %** |

(first run: 126 / 47 / 5, 71 %). Dependency jars helped little: 3 more `onBackPressed` overrides were found. The rest of the improvement comes from the bug fixes below. Findings in files the harness cannot analyse (Kotlin Multiplatform source sets, old layouts: 14 atr findings) are left out of the figures, as before.

## Precision, by hand

A random sample of each tool's findings (all rules pooled; seed 7; 40 per tool, drawn from the 173 atr and 139 atl findings of the run before the atr fixes), each read in its source context by the author. "Correct" means: a real use of the construct the rule describes. It does not mean the app is broken on Android 16, and it is one reader, not an independent review.

| tool | sample | correct | not correct |
|---|---|---|---|
| android-target-ready | 40 of 173 | 38 | 2 (`case KEYCODE_BACK: return "\033"` in a terminal key-mapping table: not back handling) |
| android-target-lint | 40 of 139 | 40 | 0 |

With 40 findings the 95 % interval is wide (about 83-99 % for 38/40, 91-100 % for 40/40): read it as "both are high", not as "atl is more precise". Most sampled findings are manifest/resource and `onBackPressed` hits that both tools report, which are the easy cases. The harder question is the findings only one tool reports, so all of those were read too:

- **atl only (7):** all correct. Three `KEYCODE_BACK` in `OnKeyListener.onKey` / `setOnKeyListener` (Anki-Android, SmartTube; atr's pattern did not match `!=`), four `requestedOrientation` assignments inside `if`/`when` (Kvaesitso, NeriPlayer, mpv-android).
- **atr only (41):** 7 `onBackPressed()` overrides that are real (the activity's base class sits in Kotlin source of another module or in a JitPack library; atl cannot resolve it, and in Thanox the Kotlin analysis also fails with an exception in the harness); 30 `KEYCODE_BACK` hits, of which **7 are not back handling** (key-mapping tables in terminal emulators and key-remapper apps) and 23 are real key handling that atl does not report by design (Views, overlays, helper classes); 4 orientation hits, of which 3 were **atr false positives** (a local variable named `requestedOrientation` in Telegram-X, fixed) and 1 is a real assignment atl missed (`activity?.requestedOrientation = ...` in vivi-music; the same code in a unit test is reported, so I think the harness's type resolution is at fault, but I did not confirm it).

So the honest summary: each tool has a few false positives that the other does not share, atr's are key-mapping tables (not fixed; they need context a pattern scanner does not have) and, until this run, local variables; atl's remaining misses in this corpus come from unresolved types. Recall was not measured, and agreement is not accuracy.

## The View key handler option

atl does not report `KEYCODE_BACK` in `android.view.View` subclasses by default. 23 of atr's atr-only hits are real key handling outside atl's default scope (mostly Views, plus overlays and helper classes), so atl 0.2.2 adds the lint option `checkViews` (see the README; off by default). Effect on the 12 repositories that have atr-only `KEYCODE_BACK` findings (same jars, `lint-checkviews.xml`):

| | both | atr only | atl only |
|---|---|---|---|
| default | 23 | 37 | 0 |
| `checkViews = true` | 40 | 20 | 0 |

The remaining 20 atr-only are the 7 mapping tables above plus helpers and listeners that are not Views or Activities (`DoubleBackManager`, a `TerminalViewClient`, `onInterceptInputEvent` in a leanback fork). The new findings were not hand-checked beyond reading the list.

## Bugs found in this run

- atr: a local variable called `requestedOrientation` was reported as an Activity property (3 false positives, Telegram-X); `keyCode != KEYCODE_BACK` was not matched (3 missed uses in Anki-Android and SmartTube; adds 10 findings in total on this corpus, all inside key handlers). Both fixed in atr 0.2.2.
- Harness: `project(":modules:x")` names did not map to module directories when the Gradle root is a subdirectory (Thanox's `android/`), so no module edges were created; fixed by suffix matching.
- No new bug in atl except the option above; a regression test for `x?.requestedOrientation = ...` was added.

## Third run: Gradle-resolved classpaths (small subset)

The direct-dependency jars used above miss transitive and unresolved artifacts, so I tried a fairer classpath: the repository's own `./gradlew` with an init script that dumps each project's `debugCompileClasspath` (external module artifacts only) and feeds those jars, plus `android.jar` and project-to-project edges, to atl 0.2.2. It was tried on the 8 repositories that had atr-only findings and fits in the box (JDK 17, Gradle 8.14.3 as launcher, Android SDK, about 1.1 GB heap, `--no-daemon`).

**What worked.** Only **3 of 8** repositories gave a usable classpath: termux-app (142 jars, 4 projects), NewPipe (156 jars) and overlay-translator (170 jars). The other five did not:

| repository | outcome |
|---|---|
| Xed-Editor | Gradle failed: a git submodule is missing |
| KeyMapper | Gradle failed: needs the NDK |
| SmartTube | Gradle failed: a referenced settings script is missing from the clone (old Gradle 7.5 project) |
| Inure | build "succeeded" but only a stub project was resolved (4 jars), so the run is not valid; excluded |
| Podroid | 0 jars resolved, 3 failures; excluded |

Same three repositories, same atr output, atl 0.2.2 with direct-dependency jars versus Gradle-resolved jars (Kotlin stdlib/compiler jars excluded in both, as before, because the Lint CLI cannot read newer Kotlin metadata):

| | both | atr only | atl only |
|---|---|---|---|
| direct-dependency jars (before) | 9 | 9 | 0 |
| Gradle-resolved jars | 10 | 8 | 0 |

- The one finding that moved is `FilePickerActivityHelper.onBackPressed()` in NewPipe, whose base class `com.nononsenseapps.filepicker.FilePickerActivity` comes from a library that the direct-dependency run could not resolve (4 unresolved artifacts). With the Gradle classpath atl reports it. That is the kind of miss the classpath explains.
- The 8 that remain are all `KEYCODE_BACK` hits outside Activities (a `View.onKeyPreIme`, an anonymous `FrameLayout.dispatchKeyEvent`, helper classes, a key-mapping table in termux's `KeyHandler`). That is atl's by-design scope (see `checkViews` above), not a classpath problem. Better jars would not change them.

**Caveats.** Three repositories and 18 findings is a tiny sample; one recovered finding is an anecdote, not a rate. The whole subset was chosen because it had atr-only findings, so it overstates the gap. Kotlin library jars are still excluded. Whether the "atl only"/"atr only" split is right was read by the author (me), not independently. Recall against ground truth is still not measured, and agreement is not accuracy. The init script and driver are not part of this repository.

## Not done

- Gradle-resolved classpaths on the full corpus: only 3 repositories could be tried (see the next section).
- No ground truth: nobody ran these apps on an Android 16 device.
