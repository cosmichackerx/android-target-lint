# Changelog

## 0.1.0

First release. Six Android Lint issues for apps moving to `targetSdkVersion` 36 (Android 16) and 37:

- `OnBackPressedOverride`, `KeyCodeBackHandling` (predictive back)
- `PredictiveBackOptOut` (informational)
- `EdgeToEdgeOptOut`
- `FixedOrientationManifest`, `FixedOrientationCode`

19 unit tests (Lint's `LintDetectorTest`, including its partial-analysis mode), plus a sample app that CI lints.
