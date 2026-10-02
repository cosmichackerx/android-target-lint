# android-target-lint

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

Behaviour is from the [Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16) and the [predictive back guide](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture). Read those before relying on a message here.

## Install

1. Download `android-target-lint-<version>.jar` from [Releases](https://github.com/cosmichackerx/android-target-lint/releases) (a `SHA256SUMS.txt` is attached).
2. Put it in your repo, for example `lint-libs/`, and in the module's `build.gradle.kts`:

```kotlin
dependencies {
    lintChecks(files("../lint-libs/android-target-lint-0.1.0.jar"))
}
```

3. Run `./gradlew lint` (or `lintDebug`). Configure severities in `lint.xml` as usual, for example:

```xml
<lint>
  <issue id="PredictiveBackOptOut" severity="ignore" />
  <issue id="OnBackPressedOverride" severity="error" />
</lint>
```

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

- Verified: 19 unit tests pass on JDK 17 and 21 (Linux), 17 on Windows and macOS (see CI); the sample app is linted by a real AGP 8.13.2 build in CI.
- Built against Lint 31.13.2 (AGP 8.13.2). Other Lint/AGP versions are untested.
- Not measured: recall on real projects. Only the cases covered by tests and the sample are known to behave.
- Some of these situations may also be flagged by Android Lint itself or by AndroidX lint checks; overlap with those was not checked.
- Constant evaluation is limited: a value computed at runtime is not seen.
- No quick-fixes yet.

## Build

```
./gradlew :lint-rules:test :lint-rules:jar        # lint-rules/build/libs/android-target-lint-0.1.0.jar
./gradlew -PwithSample :sample-app:lintDebug      # needs an Android SDK with platform 36
```

MIT licensed. See [CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md).
