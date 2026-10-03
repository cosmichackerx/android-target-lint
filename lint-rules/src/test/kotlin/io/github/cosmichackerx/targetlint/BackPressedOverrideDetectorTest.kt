package io.github.cosmichackerx.targetlint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

class BackPressedOverrideDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = BackPressedOverrideDetector()
    override fun getIssues(): List<Issue> = listOf(BackPressedOverrideDetector.ISSUE)

    fun testKotlinActivityOverrideIsReported() {
        lint().files(
            *Stubs.all,
            Stubs.manifestWith(36, ""),
            kotlin(
                """
                package test.pkg
                import android.app.Activity
                class Main : Activity() {
                    override fun onBackPressed() {
                        super.onBackPressed()
                    }
                }
                """,
            ).indented(),
        ).run().expect(
            """
            src/test/pkg/Main.kt:4: Warning: onBackPressed() is deprecated with predictive back. This app targets API 36, so it is no longer called on Android 16 devices. Use OnBackPressedDispatcher.addCallback / OnBackPressedCallback instead. [OnBackPressedOverride]
                override fun onBackPressed() {
                             ~~~~~~~~~~~~~
            0 errors, 1 warnings
            """.trimIndent(),
        )
    }

    fun testJavaOverrideBelowTarget36SaysItWillStop() {
        lint().files(
            *Stubs.all,
            Stubs.manifestWith(34, ""),
            java(
                """
                package test.pkg;
                import android.app.Activity;
                public class Main extends Activity {
                    @Override public void onBackPressed() {}
                }
                """,
            ).indented(),
        ).run().expectWarningCount(1).expectContains("once the app targets API 36 (currently 34)")
    }

    fun testDialogSubclassIsReported() {
        lint().files(
            *Stubs.all,
            Stubs.manifestWith(36, ""),
            java(
                """
                package test.pkg;
                import android.app.Dialog;
                public class MyDialog extends Dialog {
                    @Override public void onBackPressed() {}
                }
                """,
            ).indented(),
        ).run().expectWarningCount(1)
    }

    fun testSameNameInAnUnrelatedClassIsNotReported() {
        lint().files(
            *Stubs.all,
            Stubs.manifestWith(36, ""),
            kotlin(
                """
                package test.pkg
                open class Scene { open fun onBackPressed() {} }
                class Title : Scene() { override fun onBackPressed() {} }
                interface Handler { fun onBackPressed(): Boolean }
                class H : Handler { override fun onBackPressed(): Boolean = false }
                """,
            ).indented(),
        ).run().expectClean()
    }

    fun testOverloadWithParametersAndCallsAreNotReported() {
        lint().files(
            *Stubs.all,
            Stubs.manifestWith(36, ""),
            java(
                """
                package test.pkg;
                import android.app.Activity;
                public class Main extends Activity {
                    public void onBackPressed(int x) {}
                    void go() { finishIt(); }
                    void finishIt() {}
                }
                """,
            ).indented(),
        ).run().expectClean()
    }

    fun testSuppressionWorks() {
        lint().files(
            *Stubs.all,
            Stubs.manifestWith(36, ""),
            kotlin(
                """
                package test.pkg
                import android.app.Activity
                class Main : Activity() {
                    @Suppress("OnBackPressedOverride")
                    override fun onBackPressed() {}
                }
                """,
            ).indented(),
        ).run().expectClean()
    }

    /** Regression: found by running the pack over public repos; AndroidX base classes override onBackPressed themselves. */
    fun testSubclassOfAnAndroidxStyleBaseClassIsReported() {
        lint().files(
            *Stubs.all,
            Stubs.manifestWith(36, ""),
            java(
                """
                package androidx.activity;
                public class ComponentActivity extends android.app.Activity {
                    @Override public void onBackPressed() { super.onBackPressed(); }
                }
                """,
            ).indented(),
            kotlin(
                """
                package test.pkg
                import androidx.activity.ComponentActivity
                class Main : ComponentActivity() {
                    override fun onBackPressed() { }
                }
                """,
            ).indented(),
        ).run().expectWarningCount(1).expectContains("Main.kt:4")
    }
}
