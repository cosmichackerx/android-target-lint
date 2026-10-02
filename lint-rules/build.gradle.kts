import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

group = "io.github.cosmichackerx"
version = providers.gradleProperty("releaseVersion").getOrElse("0.1.0")

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        // Lint embeds its own Kotlin runtime; stay on a language level it understands.
        languageVersion = KotlinVersion.KOTLIN_1_9
        apiVersion = KotlinVersion.KOTLIN_1_9
    }
}

dependencies {
    compileOnly(libs.lint.api)
    compileOnly(libs.lint.checks)
    testImplementation(libs.lint)
    testImplementation(libs.lint.tests)
    testImplementation(libs.junit)
}

tasks.jar {
    archiveBaseName = "android-target-lint"
    manifest {
        attributes(
            "Lint-Registry-v2" to "io.github.cosmichackerx.targetlint.TargetLintRegistry",
            "Implementation-Title" to "android-target-lint",
            "Implementation-Version" to project.version,
        )
    }
}

tasks.test {
    useJUnit()
    maxHeapSize = "1g"
    testLogging { events("passed", "failed", "skipped"); showStandardStreams = false }
}

publishing {
    publications {
        create<MavenPublication>("lintRules") {
            artifactId = "android-target-lint"
            from(components["java"])
            pom {
                name = "android-target-lint"
                description = "Android Lint rules for the targetSdk 36/37 migration"
                url = "https://github.com/cosmichackerx/android-target-lint"
                licenses {
                    license {
                        name = "MIT"
                        url = "https://opensource.org/licenses/MIT"
                    }
                }
                scm { url = "https://github.com/cosmichackerx/android-target-lint" }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/cosmichackerx/android-target-lint")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
