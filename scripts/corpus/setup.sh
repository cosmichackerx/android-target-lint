#!/bin/bash
# One-time setup for the corpus harness: the Lint command-line classpath (cp/) and a few AndroidX class jars (libs/).
# Needs JDK 17, an Android SDK with platform android-36 (ANDROID_HOME) and network access.
set -e
cd "$(dirname "$0")"
mkdir -p cp libs
cat > settings.gradle.kts <<'KTS'
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "atl-harness"
KTS
cat > build.gradle.kts <<'KTS'
plugins { java }
val cli by configurations.creating
dependencies {
    cli("com.android.tools.lint:lint:31.13.2")
    cli("org.jetbrains.kotlin:kotlin-stdlib:2.2.21")
}
tasks.register<Copy>("exportCli") { from(cli); into("cp") }
KTS
gradle exportCli --no-daemon        # any Gradle 8.x; the repository's ./gradlew works too
g=https://dl.google.com/dl/android/maven2
for spec in androidx.appcompat/appcompat:1.7.1 androidx.fragment/fragment:1.8.9 androidx.activity/activity:1.11.0 androidx.core/core:1.16.0; do
  p=${spec%%:*}; v=${spec##*:}; grp=${p%%/*}; art=${p##*/}
  curl -fsSL -o libs/$art.aar $g/${grp//.//}/$art/$v/$art-$v.aar
  unzip -p libs/$art.aar classes.jar > libs/$art.jar
done
