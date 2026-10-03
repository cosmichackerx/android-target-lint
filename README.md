# android-target-lint

[![CI](https://github.com/cosmichackerx/android-target-lint/actions/workflows/ci.yml/badge.svg)](https://github.com/cosmichackerx/android-target-lint/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/cosmichackerx/android-target-lint?sort=semver)](https://github.com/cosmichackerx/android-target-lint/releases)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

**Android Lint rules for the `targetSdkVersion` 36 / 37 migration**: predictive back (`onBackPressed`, `KEYCODE_BACK`), the edge-to-edge opt-out, and fixed screen orientation on large screens. A single jar you add with `lintChecks(...)`. Works in Gradle `lint`, Android Studio and CI.

These checks use Lint's UAST/XML APIs, so they resolve classes (a method named `onBackPressed` on a non-Activity is not reported). The sibling project [android-target-ready](https://github.com/cosmichackerx/android-target-ready) is a regex scanner that needs no Gradle build; this one is the type-aware version for projects that already run Android Lint.

## Rules

| Issue id | Severity | What it reports |
|---|---|---|
| `OnBackPressedOverride` | warning | `onBackPressed()` overridden in an `Activity` or `Dialog` subclass (not called on Android 16 devices for apps targeting 36) |
| `KeyCodeBackHandling` | warning | `KEYCODE_BACK` used in `onKeyDown/onKeyUp/onKeyPreIme/onKeyLongPress/dispatchKeyEvent` of an `Activity`/`Dialog` subclass |
| `PredictiveBackOptOut` | informational | `android:enableOnBackInvokedCallback="false"` on `<application>` or `<activity>` |
| `EdgeToEdgeOptOut` | warning | `android:windowOptOutEdgeToEdgeEnforcement` = `true` in a values resource |
| `FixedOrientationManifest` | warning | `android:screenOrientation` portrait/landscape variants in the manifest (apps with `appCategory="game"` are exempt) |
| `FixedOrientationCode` | warning | `setRequestedOrientation(...)` / `requestedOrientation =` with a fixed portrait/landscape constant |
| `LargeScreenRestrictionsIgnored` | warning | `resizeableActivity="false"`, `minAspectRatio`, `maxAspectRatio` (ignored on 600dp+ screens for apps targeting 36; games exempt) |
| `LargeScreenOptOutProperty` | warning | the temporary `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` opt-out (does not apply when targeting 37) |
| `ContentCaptureEnabledDeprecated` | warning | `ContentCaptureManager.setContentCaptureEnabled(false)` (no longer disables Content Capture at targetSdk 37; use `FLAG_SECURE`) |
| `BackgroundActivityStartLegacyMode` | warning | `ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED` (Android 17 asks apps to move to granular modes) |

### Quick-fixes

Offered in Android Studio and by `lint --apply-suggestions`-style tooling: `PredictiveBackOptOut` (set the attribute to `true`),
`EdgeToEdgeOptOut` (set the opt-out to `false`), `FixedOrientationManifest` (`screenOrientation="unspecified"`),
`LargeScreenRestrictionsIgnored` (remove the attribute / set `resizeableActivity="true"`), `LargeScreenOptOutProperty` (remove the property),
`BackgroundActivityStartLegacyMode` (use `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE`, which **changes behaviour**: check the launch still works).
Each fix is covered by a `expectFixDiffs` test. There are no fixes for the back-handling rules; migrating to `OnBackPressedCallback` needs a human.

The Android 17 rules (`ContentCaptureEnabledDeprecated`, `BackgroundActivityStartLegacyMode`) are taken from the
[Android 17 behavior changes](https://developer.android.com/about/versions/17/behavior-changes-17) page (last updated 2026-10-01 when written).
Other items on that page (local network permission, static final reflection, native `System.load`, SMS OTP delay, RFCOMM reads) were left out
because no low-false-positive static check was found; [android-target-ready](https://github.com/cosmichackerx/android-target-ready) has heuristic rules for several of them.

Behaviour is from the [Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16) and the [predictive back guide](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture). Read those before relying on a message here.

## Install

1. Download `android-target-lint-<version>.jar` from [Releases](https://github.com/cosmichackerx/android-target-lint/releases) (a `SHA256SUMS.txt` is attached).
2. Put it in your repo, for example `lint-libs/`, and in the module's `build.gradle.kts`:

```kotlin
dependencies {
    lintChecks(files("../lint-libs/android-target-lint-0.2.2.jar"))
}
```

3. Run `./gradlew lint` (or `lintDebug`). Configure severities in `lint.xml` as usual, for example:

```xml
<lint>
  <issue id="PredictiveBackOptOut" severity="ignore" />
  <issue id="OnBackPressedOverride" severity="error" />
</lint>
```

`KeyCodeBackHandling` has one option, `checkViews` (default `false`): set it to also check the key callbacks of `android.view.View` subclasses
(`onKeyDown`, `onKeyUp`, `onKeyPreIme`, `onKeyLongPress`, `dispatchKeyEvent`). It is off by default because many custom views map every key code,
including `KEYCODE_BACK`, for input handling (terminals, game pads); turn it on to match what a text-based scanner reports.

```xml
<lint>
  <issue id="KeyCodeBackHandling">
    <option name="checkViews" value="true" />
  </issue>
</lint>
```

### Via GitHub Packages (Maven)

Each release is also published as `io.github.cosmichackerx:android-target-lint:<version>` to GitHub Packages. **GitHub Packages requires
authentication even for public packages** (a personal access token with `read:packages`), so the plain jar from Releases is the
simpler route. If you want the Maven form:

```kotlin
// settings.gradle.kts / build.gradle.kts
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/cosmichackerx/android-target-lint")
        credentials { username = providers.gradleProperty("gpr.user").get(); password = providers.gradleProperty("gpr.key").get() }
    }
}
dependencies { lintChecks("io.github.cosmichackerx:android-target-lint:0.2.2") }
```

Maven Central is not used (it needs signing keys and a namespace verification that the repository owner has to do).

## Real output

This is the unedited report from `sample-app/` (AGP 8.13.2, `targetSdk = 36`), shortened to the headline of each finding:

```
values-v35/styles.xml:3: Warning: windowOptOutEdgeToEdgeEnforcement is a temporary opt-out. ... [EdgeToEdgeOptOut]
LegacyActivity.kt:21: Warning: KEYCODE_BACK handled in onKeyDown. ... [KeyCodeBackHandling]
LegacyActivity.kt:16: Warning: onBackPressed() is deprecated with predictive back. ... [OnBackPressedOverride]
AndroidManifest.xml:18: Hint: Predictive back is switched off here ... [PredictiveBackOptOut]
LegacyActivity.kt:11/12: Warning: setRequestedOrientation with a fixed portrait/landscape value ... [FixedOrientationCode]
AndroidManifest.xml:9: Warning: screenOrientation="portrait" is ignored on screens of 600dp and wider ... [FixedOrientationManifest]
0 errors, 6 warnings, 1 hint
```

The same sample contains a `ModernActivity` (an overload named `onBackPressed(String)`, a different key code) and a plain class with `onBackPressed()`; CI asserts those are not reported.

## What was verified, and what was not

- Verified: the unit tests pass on JDK 17 and 21 (Linux), 17 on Windows and macOS (see CI); the sample app is linted by a real AGP 8.13.2 build in CI.
- Built against Lint 31.13.2 (AGP 8.13.2). Other Lint/AGP versions are untested.
- Not measured: recall and precision against ground truth. Only the cases covered by tests and the sample are known to behave. A cross-check of the six predictive-back / edge-to-edge / orientation rules against the independent [android-target-ready](https://github.com/cosmichackerx/android-target-ready) scanner on 52-54 public repositories is in [docs/corpus-comparison.md](docs/corpus-comparison.md): 71 % agreement without dependency jars, 73 % with them (plus a hand-read sample of 40 findings per tool: atr 38 correct, atl 40), and it found bugs in both tools. Agreement between two tools is not accuracy.
- Android Lint itself has overlapping checks (`GestureBackNavigation`, `LockedOrientationActivity`, and the `DiscouragedApi` message about fixed orientation on Android 16). The overlap with these rules was not measured; the rules here additionally key on `targetSdkVersion` 36/37 and ship quick-fixes.
- `KeyCodeBackHandling` deliberately reports only `Activity`/`Dialog` key callbacks, `OnKeyListener.onKey` and lambdas passed to `setOnKeyListener`; it does not report `View.onKeyDown` overrides unless you enable the `checkViews` option (to avoid flagging key-mapping tables), so it reports fewer sites than the pattern scanner.
- Constant evaluation is limited: a value computed at runtime is not seen.
- Quick-fixes exist for six of the ten rules (see above); they change source/manifest text and should be reviewed like any edit.

## Build

```
./gradlew :lint-rules:test :lint-rules:jar        # lint-rules/build/libs/android-target-lint-0.1.0.jar
./gradlew -PwithSample :sample-app:lintDebug      # needs an Android SDK with platform 36
```

MIT licensed. See [CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md).

## Related tools

Small, independent tools by the same author, for build and CI hygiene and for migrations with a deadline. Each works on its own; none requires another.

**Gradle and Android migrations**

* [gradle-version-catalog-lint](https://github.com/cosmichackerx/gradle-version-catalog-lint): Lints `libs.versions.toml`: unused libraries, plugins and versions, dynamic or SNAPSHOT versions, hard-coded dependencies.
* [gradle10-ready](https://github.com/cosmichackerx/gradle10-ready): Static scan of Gradle build scripts for what Gradle 10 removes (space assignment, multi-string dependencies, Kotlin DSL delegates). `--fix`, PR mode.
* [agp9-ready](https://github.com/cosmichackerx/agp9-ready): Static scan of Gradle files for what Android Gradle Plugin 9 and 10 break (built-in Kotlin, legacy variant API, opt-outs), including `buildSrc`. `--fix`, PR mode.
* [kotlin24-ready](https://github.com/cosmichackerx/kotlin24-ready): Static scan of Gradle build scripts for what Kotlin 2.4 removes in the Kotlin Gradle plugin (language version 1.9, KMP `targetHierarchy`, Compose options, ABI validation). `--fix`, PR mode.
* [android-target-ready](https://github.com/cosmichackerx/android-target-ready): Static scanner for the targetSdk 36 / 37 migration in app code and manifests (edge-to-edge, predictive back, large screens).

**CI and repository hygiene**

* [node24-ready](https://github.com/cosmichackerx/node24-ready): Finds GitHub Actions still on the removed Node 20 runtime, also inside composite actions and reusable workflows, and the smallest node24 upgrade.
* [dependabot-gaps](https://github.com/cosmichackerx/dependabot-gaps): Finds manifests your `dependabot.yml` does not cover, and dead or overlapping entries.
* [sha256-ready](https://github.com/cosmichackerx/sha256-ready): Finds code that assumes 40-character Git hashes before Git 3.0 makes SHA-256 repositories the default.
* [helm4-ready](https://github.com/cosmichackerx/helm4-ready): Finds the Helm 3 CLI usage (removed and deprecated flags, executable post-renderers, `registry login` URLs, Helm 3 pins) that Helm 4 rejects in CI workflows, scripts and Makefiles, checked against real Helm 3.22.0 and 4.3.0.
* [kafka4-ready](https://github.com/cosmichackerx/kafka4-ready): Finds Kafka 3 settings and CLI usage that Kafka 4 rejects or silently ignores (ZooKeeper-mode broker files, removed `zookeeper.*` and `log.message.format.version` settings, `--zookeeper` options, space-separated `--bootstrap-server`), checked against a real Kafka 4 broker and tools.
* [agent-context-diff](https://github.com/cosmichackerx/agent-context-diff): Diffs `AGENTS.md`, `CLAUDE.md`, Cursor rules and MCP configs between git refs (new servers, widened permissions, hidden Unicode).

