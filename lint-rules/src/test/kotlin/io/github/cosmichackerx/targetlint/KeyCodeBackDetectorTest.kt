package io.github.cosmichackerx.targetlint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

class KeyCodeBackDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = KeyCodeBackDetector()
    override fun getIssues(): List<Issue> = listOf(KeyCodeBackDetector.ISSUE)

    fun testOnKeyDownInActivityIsReported() {
        lint().files(
            *Stubs.all,
            Stubs.manifestWith(36, ""),
            java(
                """
                package test.pkg;
                import android.app.Activity;
                import android.view.KeyEvent;
                public class Main extends Activity {
                    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
                        if (keyCode == KeyEvent.KEYCODE_BACK) { return true; }
                        return super.onKeyDown(keyCode, event);
                    }
                }
                """,
            ).indented(),
        ).run().expectWarningCount(1).expectContains("KEYCODE_BACK handled in onKeyDown")
    }

    fun testKotlinWhenBranchInDispatchKeyEvent() {
        lint().files(
            *Stubs.all,
            Stubs.manifestWith(35, ""),
            kotlin(
                """
                package test.pkg
                import android.app.Activity
                import android.view.KeyEvent
                class Main : Activity() {
                    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                        return when (event.hashCode()) {
                            KeyEvent.KEYCODE_BACK -> true
                            else -> super.dispatchKeyEvent(event)
                        }
                    }
                }
                """,
            ).indented(),
        ).run().expectWarningCount(1).expectContains("once the app targets API 36 (currently 35)")
    }

    private val customView = java(
        """
        package test.pkg;
        import android.content.Context;
        import android.view.KeyEvent;
        import android.view.View;
        public class PlayerView extends View {
            public PlayerView(Context c) { super(c); }
            public boolean onKeyDown(int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK) { return true; }
                return false;
            }
        }
        """,
    ).indented()

    fun testViewSubclassIsNotReportedByDefault() {
        lint().files(*Stubs.all, Stubs.manifestWith(36, ""), customView).run().expectClean()
    }

    fun testViewSubclassIsReportedWithCheckViewsOption() {
        lint().files(*Stubs.all, Stubs.manifestWith(36, ""), customView)
            .configureOption(KeyCodeBackDetector.CHECK_VIEWS, true)
            .run().expectWarningCount(1).expectContains("KEYCODE_BACK handled in onKeyDown")
    }

    fun testKeyMappingInAnUnrelatedClassIsNotReported() {
        lint().files(
            *Stubs.all,
            Stubs.manifestWith(36, ""),
            java(
                """
                package test.pkg;
                import android.view.KeyEvent;
                public class KeyHandler {
                    public boolean onKeyDown(int keyCode, KeyEvent event) {
                        switch (keyCode) { case KeyEvent.KEYCODE_BACK: return true; }
                        return false;
                    }
                }
                """,
            ).indented(),
        ).run().expectClean()
    }

    fun testOtherKeysAndOtherMethodsAreNotReported() {
        lint().files(
            *Stubs.all,
            Stubs.manifestWith(36, ""),
            java(
                """
                package test.pkg;
                import android.app.Activity;
                import android.view.KeyEvent;
                public class Main extends Activity {
                    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
                        return keyCode == KeyEvent.KEYCODE_HOME;
                    }
                    int helper() { return KeyEvent.KEYCODE_BACK; }
                }
                """,
            ).indented(),
        ).run().expectClean()
    }

    fun testKotlinLambdaGivenToSetOnKeyListenerIsReported() {
        lint().files(
            *Stubs.all,
            Stubs.manifestWith(36, ""),
            kotlin(
                """
                package test.pkg
                import android.app.Dialog
                import android.view.KeyEvent
                class Show {
                    fun go(d: Dialog) {
                        d.setOnKeyListener { _, keyCode, event ->
                            keyCode == KeyEvent.KEYCODE_BACK
                        }
                    }
                }
                """,
            ).indented(),
        ).run().expectWarningCount(1).expectContains("KEYCODE_BACK handled in setOnKeyListener")
    }

    fun testSetOnKeyListenerLambdaOnAnUnresolvedReceiverTypeIsReported() {
        // e.g. a BottomSheetDialog whose library is not on the lint classpath (seen on microg in the corpus comparison)
        lint().files(
            *Stubs.all,
            Stubs.manifestWith(36, ""),
            kotlin(
                """
                package test.pkg
                import android.view.KeyEvent
                import com.example.missing.SheetDialog
                class Show {
                    fun go(d: SheetDialog) {
                        d.setOnKeyListener { _, keyCode, event ->
                            if (keyCode == KeyEvent.KEYCODE_BACK) { return@setOnKeyListener true }
                            return@setOnKeyListener false
                        }
                    }
                }
                """,
            ).indented(),
        ).allowCompilationErrors().run().expectWarningCount(1).expectContains("KEYCODE_BACK handled in setOnKeyListener")
    }

    fun testOnKeyOfAnOnKeyListenerIsReportedButAUnrelatedOnKeyIsNot() {
        lint().files(
            *Stubs.all,
            Stubs.manifestWith(36, ""),
            java(
                """
                package test.pkg;
                import android.view.KeyEvent;
                import android.view.View;
                public class L implements View.OnKeyListener {
                    @Override public boolean onKey(View v, int keyCode, KeyEvent event) {
                        return keyCode == KeyEvent.KEYCODE_BACK;
                    }
                }
                class Table {
                    boolean onKey(int keyCode) { return keyCode == KeyEvent.KEYCODE_BACK; }
                    boolean lookup(int keyCode) { return keyCode == KeyEvent.KEYCODE_BACK; }
                }
                """,
            ).indented(),
        ).run().expectWarningCount(1).expectContains("L.java:6")
    }
}
