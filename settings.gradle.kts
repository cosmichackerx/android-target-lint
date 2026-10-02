pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "android-target-lint"
include(":lint-rules")
// The sample app needs the Android SDK and AGP; CI and the README show how to build it.
if (file("sample-app").exists() && providers.gradleProperty("withSample").isPresent) {
    include(":sample-app")
}
