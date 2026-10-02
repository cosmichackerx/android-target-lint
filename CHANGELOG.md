# Changelog

## 0.2.0

- New rules: `LargeScreenRestrictionsIgnored`, `LargeScreenOptOutProperty`, `ContentCaptureEnabledDeprecated`, `BackgroundActivityStartLegacyMode` (Android 16/17 behaviour-change docs).
- Quick-fixes for six rules (see README); every fix has a diff test.
- Fixed: the games exemption no longer depends on attribute visit order (`appCategory="game"` is looked up on the `<application>` element).
- Published to GitHub Packages (Maven) in addition to the release jar.
- Messages: clearer wording for the target SDK note.

## 0.1.0

First release. Six Android Lint issues for apps moving to `targetSdkVersion` 36 (Android 16) and 37:

- `OnBackPressedOverride`, `KeyCodeBackHandling` (predictive back)
- `PredictiveBackOptOut` (informational)
- `EdgeToEdgeOptOut`
- `FixedOrientationManifest`, `FixedOrientationCode`

19 unit tests (Lint's `LintDetectorTest`, including its partial-analysis mode), plus a sample app that CI lints.
