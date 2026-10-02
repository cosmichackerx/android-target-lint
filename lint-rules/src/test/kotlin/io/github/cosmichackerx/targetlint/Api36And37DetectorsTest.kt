package io.github.cosmichackerx.targetlint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

class LargeScreenRestrictionsDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = LargeScreenRestrictionsDetector()
    override fun getIssues(): List<Issue> =
        listOf(LargeScreenRestrictionsDetector.ISSUE, LargeScreenRestrictionsDetector.ISSUE_PROPERTY)

    fun testResizeableFalseAndAspectRatiosAreReported() {
        lint().files(
            Stubs.manifestWith(
                36,
                """
                <application>
                    <activity android:name=".A" android:resizeableActivity="false"/>
                    <activity android:name=".B" android:resizeableActivity="true"/>
                    <activity android:name=".C" android:maxAspectRatio="1.86"/>
                    <activity android:name=".D" android:minAspectRatio="1.2"/>
                </application>
                """,
            ),
        ).run().expectWarningCount(3).expectContains("[LargeScreenRestrictionsIgnored]")
    }

    fun testGamesAreExemptEvenWhenTheCategoryComesAfter() {
        lint().files(
            Stubs.manifestWith(
                36,
                """
                <application android:resizeableActivity="false" android:appCategory="game">
                    <activity android:name=".A" android:maxAspectRatio="2.1"/>
                </application>
                """,
            ),
        ).run().expectClean()
    }

    fun testOptOutPropertyBelow37IsTemporary() {
        lint().files(
            Stubs.manifestWith(
                36,
                """
                <application>
                    <property android:name="android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY" android:value="true"/>
                </application>
                """,
            ),
        ).run().expectWarningCount(1).expectContains("[LargeScreenOptOutProperty]").expectContains("does not apply once the app targets API 37")
    }

    fun testOtherPropertiesAndFalseAreIgnored() {
        lint().files(
            Stubs.manifestWith(
                36,
                """
                <application>
                    <property android:name="android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY" android:value="false"/>
                    <property android:name="android.window.PROPERTY_SOMETHING_ELSE" android:value="true"/>
                </application>
                """,
            ),
        ).run().expectClean()
    }
}

class ContentCaptureDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = ContentCaptureDetector()
    override fun getIssues(): List<Issue> = listOf(ContentCaptureDetector.ISSUE)

    fun testDisablingIsReported() {
        lint().files(
            Stubs.contentCaptureManager,
            Stubs.manifestWith(36, "<application/>"),
            kotlin(
                """
                package test.pkg
                import android.view.contentcapture.ContentCaptureManager
                class Foo {
                    fun off(m: ContentCaptureManager) { m.setContentCaptureEnabled(false) }
                }
                """,
            ).indented(),
        ).run().expectWarningCount(1).expectContains("[ContentCaptureEnabledDeprecated]")
    }

    fun testEnablingAndOtherClassesAreIgnored() {
        lint().files(
            Stubs.contentCaptureManager,
            Stubs.manifestWith(36, "<application/>"),
            java(
                """
                package test.pkg;
                import android.view.contentcapture.ContentCaptureManager;
                public class Foo {
                    void on(ContentCaptureManager m) { m.setContentCaptureEnabled(true); }
                    static class Mine { void setContentCaptureEnabled(boolean b) {} }
                    void other(Mine m) { m.setContentCaptureEnabled(false); }
                }
                """,
            ).indented(),
        ).run().expectClean()
    }
}

class BackgroundActivityStartDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = BackgroundActivityStartDetector()
    override fun getIssues(): List<Issue> = listOf(BackgroundActivityStartDetector.ISSUE)

    fun testLegacyConstantIsReportedInJavaAndKotlin() {
        lint().files(
            Stubs.activityOptions,
            Stubs.manifestWith(36, "<application/>"),
            java(
                """
                package test.pkg;
                import android.app.ActivityOptions;
                public class J {
                    ActivityOptions o = new ActivityOptions().setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                }
                """,
            ).indented(),
            kotlin(
                """
                package test.pkg
                import android.app.ActivityOptions
                class K {
                    val o = ActivityOptions().setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                }
                """,
            ).indented(),
        ).run().expectWarningCount(2).expectContains("[BackgroundActivityStartLegacyMode]")
    }

    fun testGranularModeAndLookalikeConstantsAreFine() {
        lint().files(
            Stubs.activityOptions,
            Stubs.manifestWith(36, "<application/>"),
            java(
                """
                package test.pkg;
                import android.app.ActivityOptions;
                public class J {
                    static final int MODE_BACKGROUND_ACTIVITY_START_ALLOWED = 1;
                    ActivityOptions o = new ActivityOptions().setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE);
                    int mine = MODE_BACKGROUND_ACTIVITY_START_ALLOWED;
                }
                """,
            ).indented(),
        ).run().expectClean()
    }
}

class QuickFixTest : LintDetectorTest() {
    override fun getDetector(): Detector = PredictiveBackOptOutDetector()
    override fun getIssues(): List<Issue> = listOf(PredictiveBackOptOutDetector.ISSUE)

    private val manifestIssues = arrayOf(
        PredictiveBackOptOutDetector.ISSUE, FixedOrientationDetector.ISSUE_MANIFEST,
        LargeScreenRestrictionsDetector.ISSUE, LargeScreenRestrictionsDetector.ISSUE_PROPERTY,
    )

    fun testManifestFixes() {
        lint().issues(*manifestIssues).files(
            Stubs.manifestWith(
                36,
                """
                <application android:enableOnBackInvokedCallback="false">
                    <activity android:name=".A" android:screenOrientation="portrait" android:maxAspectRatio="1.8"/>
                    <property android:name="android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY" android:value="true"/>
                </application>
                """,
            ),
        ).run().expectFixDiffs(
            """
            Fix for AndroidManifest.xml line 4: Enable predictive back (set to true):
            @@ -9 +9 @@
            -    <application android:enableOnBackInvokedCallback="false" >
            +    <application android:enableOnBackInvokedCallback="true" >
            Fix for AndroidManifest.xml line 5: Set screenOrientation to unspecified:
            @@ -13 +13 @@
            -            android:screenOrientation="portrait" />
            +            android:screenOrientation="unspecified" />
            Fix for AndroidManifest.xml line 5: Remove maxAspectRatio:
            @@ -12 +11,0 @@
            -            android:maxAspectRatio="1.8"
            Fix for AndroidManifest.xml line 6: Remove the opt-out property:
            @@ -6 +5,0 @@
            -            <property android:name="android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY" android:value="true"/>
            """.trimIndent(),
        )
    }

    fun testEdgeToEdgeFix() {
        lint().issues(EdgeToEdgeOptOutDetector.ISSUE).files(
            Stubs.manifestWith(36, "<application/>"),
            xml(
                "res/values-v35/themes.xml",
                """
                <resources>
                    <style name="T"><item name="android:windowOptOutEdgeToEdgeEnforcement">true</item></style>
                </resources>
                """,
            ).indented(),
        ).run().expectFixDiffs(
            """
            Fix for res/values-v35/themes.xml line 2: Set the opt-out to false:
            @@ -2 +2 @@
            -    <style name="T"><item name="android:windowOptOutEdgeToEdgeEnforcement">true</item></style>
            +    <style name="T"><item name="android:windowOptOutEdgeToEdgeEnforcement">false</item></style>
            """.trimIndent(),
        )
    }

    fun testBackgroundActivityStartFix() {
        lint().issues(BackgroundActivityStartDetector.ISSUE).files(
            Stubs.activityOptions,
            Stubs.manifestWith(36, "<application/>"),
            java(
                """
                package test.pkg;
                import android.app.ActivityOptions;
                public class J {
                    int m = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED;
                }
                """,
            ).indented(),
        ).run().expectFixDiffs(
            """
            Fix for src/test/pkg/J.java line 4: Use MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE (activity may start only while your app is visible):
            @@ -4 +4 @@
            -    int m = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED;
            +    int m = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE;
            """.trimIndent(),
        )
    }
}
