plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.sample"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    lint {
        abortOnError = false
        checkOnly += listOf(
            "OnBackPressedOverride", "KeyCodeBackHandling", "EdgeToEdgeOptOut",
            "PredictiveBackOptOut", "FixedOrientationManifest", "FixedOrientationCode",
        )
        textReport = true
        textOutput = layout.buildDirectory.file("reports/lint-results.txt").get().asFile
        xmlReport = true
        htmlReport = false
    }
}

dependencies {
    // In your own project: lintChecks(files("lint-libs/android-target-lint-0.1.0.jar"))
    lintChecks(project(":lint-rules"))
}
