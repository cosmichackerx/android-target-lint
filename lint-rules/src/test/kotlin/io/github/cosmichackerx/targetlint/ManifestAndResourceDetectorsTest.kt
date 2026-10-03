package io.github.cosmichackerx.targetlint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

class EdgeToEdgeOptOutDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = EdgeToEdgeOptOutDetector()
    override fun getIssues(): List<Issue> = listOf(EdgeToEdgeOptOutDetector.ISSUE)

    private fun theme(value: String) = xml(
        "res/values-v35/themes.xml",
        """
        <resources>
            <style name="Base.V35" parent="android:Theme.Material.Light">
                <item name="android:windowOptOutEdgeToEdgeEnforcement">$value</item>
            </style>
        </resources>
        """,
    ).indented()

    fun testOptOutTrueIsReported() {
        lint().files(Stubs.manifestWith(36, "<application/>"), theme("true")).run().expect(
            """
            res/values-v35/themes.xml:3: Warning: windowOptOutEdgeToEdgeEnforcement is a temporary opt-out. This app targets API 36, so the opt-out is ignored on Android 16 devices. Handle window insets instead. [EdgeToEdgeOptOut]
                    <item name="android:windowOptOutEdgeToEdgeEnforcement">true</item>
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """.trimIndent(),
        )
    }

    fun testBelow36TheMessageSaysItWillStop() {
        lint().files(Stubs.manifestWith(35, "<application/>"), theme("true")).run().expectWarningCount(1)
            .expectContains("once the app targets API 36 (currently 35)")
    }

    fun testFalseIsFine() {
        lint().files(Stubs.manifestWith(36, "<application/>"), theme("false")).run().expectClean()
    }

    fun testOtherItemsAreIgnored() {
        lint().files(
            Stubs.manifestWith(36, "<application/>"),
            xml("res/values/themes.xml", """<resources><style name="T"><item name="android:statusBarColor">true</item></style></resources>""").indented(),
        ).run().expectClean()
    }
}

class PredictiveBackOptOutDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = PredictiveBackOptOutDetector()
    override fun getIssues(): List<Issue> = listOf(PredictiveBackOptOutDetector.ISSUE)

    fun testApplicationAndActivityOptOutAreReported() {
        lint().files(
            Stubs.manifestWith(
                36,
                """
                <application android:enableOnBackInvokedCallback="false">
                    <activity android:name=".A" android:enableOnBackInvokedCallback="false"/>
                    <activity android:name=".B" android:enableOnBackInvokedCallback="true"/>
                </application>
                """,
            ),
        ).run().expectContains("[PredictiveBackOptOut]").expectContains("AndroidManifest.xml:4").expectContains("AndroidManifest.xml:5")
    }

    fun testTrueIsFine() {
        lint().files(Stubs.manifestWith(36, """<application android:enableOnBackInvokedCallback="true"/>""")).run().expectClean()
    }
}

class FixedOrientationDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = FixedOrientationDetector()
    override fun getIssues(): List<Issue> = listOf(FixedOrientationDetector.ISSUE_MANIFEST, FixedOrientationDetector.ISSUE_CODE)

    fun testManifestPortraitIsReported() {
        lint().files(
            Stubs.manifestWith(
                36,
                """
                <application>
                    <activity android:name=".A" android:screenOrientation="portrait"/>
                    <activity android:name=".B" android:screenOrientation="unspecified"/>
                    <activity android:name=".C" android:screenOrientation="fullSensor"/>
                    <activity android:name=".D" android:screenOrientation="sensorLandscape"/>
                </application>
                """,
            ),
        ).run().expectWarningCount(2).expectContains("[FixedOrientationManifest]")
    }

    fun testGamesAreExempt() {
        lint().files(
            Stubs.manifestWith(
                36,
                """
                <application android:appCategory="game">
                    <activity android:name=".A" android:screenOrientation="landscape"/>
                </application>
                """,
            ),
        ).run().expectClean()
    }

    fun testSetRequestedOrientationWithFixedValueIsReported() {
        lint().files(
            *Stubs.all,
            Stubs.manifestWith(36, ""),
            java(
                """
                package test.pkg;
                import android.app.Activity;
                import android.content.pm.ActivityInfo;
                public class Main extends Activity {
                    void a() { setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); }
                    void b() { setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED); }
                    void c() { setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR); }
                    void d(int dynamic) { setRequestedOrientation(dynamic); }
                }
                """,
            ).indented(),
        ).run().expectWarningCount(1).expectContains("[FixedOrientationCode]")
    }
}

class FixedOrientationConditionalTest : LintDetectorTest() {
    override fun getDetector(): Detector = FixedOrientationDetector()
    override fun getIssues(): List<Issue> = listOf(FixedOrientationDetector.ISSUE_CODE)

    fun testBranchesOfAConditionalAreChecked() {
        lint().files(
            *Stubs.all,
            Stubs.manifestWith(36, ""),
            kotlin(
                """
                package test.pkg
                import android.app.Activity
                import android.content.pm.ActivityInfo
                class Main : Activity() {
                    fun flip(landscape: Boolean) {
                        setRequestedOrientation(if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
                    }
                    fun viaWhen(mode: Int) {
                        setRequestedOrientation(when (mode) { 1 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED })
                    }
                    fun fine(x: Boolean) {
                        setRequestedOrientation(if (x) ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
                    }
                }
                """,
            ).indented(),
        ).skipTestModes(TestMode.BODY_REMOVAL).run().expectWarningCount(2).expectContains("Main.kt:6").expectContains("Main.kt:9")
    }

    fun testPropertyAssignmentWithAnExplicitOrSafeCallReceiverIsChecked() {
        lint().files(
            *Stubs.all,
            Stubs.manifestWith(36, ""),
            kotlin(
                """
                package test.pkg
                import android.app.Activity
                import android.content.pm.ActivityInfo
                fun lock(activity: Activity?, other: Activity) {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    other.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                class Main : Activity() {
                    fun implicit() { requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                }
                fun viaRun(context: android.content.Context) {
                    val activity = (context as? Activity) ?: run {
                        var ctx = context
                        ctx as? Activity
                    }
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
                """,
            ).indented(),
        ).skipTestModes(TestMode.BODY_REMOVAL, TestMode.PARENTHESIZED).run().expectWarningCount(4)
    }
}
