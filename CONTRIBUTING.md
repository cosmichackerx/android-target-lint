# Contributing

- `./gradlew :lint-rules:test` runs the unit tests (about a minute).
- `./gradlew -PwithSample :sample-app:lintDebug` applies the rule pack to `sample-app/`; CI asserts the 7 expected findings there. It needs an Android SDK with platform 36 (`ANDROID_HOME`).
- A new rule needs: the detector, a registry entry, tests with at least one case that must NOT be reported, and a row in the README table with the source for the behaviour change (Android developer docs link).
- Keep messages free of facts you have not checked against the docs.
- Open a PR; CI must be green.
