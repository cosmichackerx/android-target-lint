package io.github.cosmichackerx.targetlint

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.manifest

/** Minimal framework stubs so the tests do not need an Android SDK. */
object Stubs {
    val activity: TestFile = java(
        """
        package android.app;
        import android.view.KeyEvent;
        public class Activity {
            public void onBackPressed() {}
            public boolean onKeyDown(int keyCode, KeyEvent event) { return false; }
            public boolean onKeyUp(int keyCode, KeyEvent event) { return false; }
            public boolean dispatchKeyEvent(KeyEvent event) { return false; }
            public void setRequestedOrientation(int requestedOrientation) {}
        }
        """,
    ).indented()

    val dialog: TestFile = java(
        """
        package android.app;
        public class Dialog {
            public void onBackPressed() {}
        }
        """,
    ).indented()

    val keyEvent: TestFile = java(
        """
        package android.view;
        public class KeyEvent {
            public static final int KEYCODE_BACK = 4;
            public static final int KEYCODE_HOME = 3;
        }
        """,
    ).indented()

    val activityInfo: TestFile = java(
        """
        package android.content.pm;
        public class ActivityInfo {
            public static final int SCREEN_ORIENTATION_LANDSCAPE = 0;
            public static final int SCREEN_ORIENTATION_PORTRAIT = 1;
            public static final int SCREEN_ORIENTATION_UNSPECIFIED = -1;
            public static final int SCREEN_ORIENTATION_SENSOR_LANDSCAPE = 6;
            public static final int SCREEN_ORIENTATION_FULL_SENSOR = 10;
        }
        """,
    ).indented()

    val all: Array<TestFile> = arrayOf(activity, dialog, keyEvent, activityInfo)

    fun manifestWith(target: Int, body: String): TestFile = manifest(
        """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
            <uses-sdk android:minSdkVersion="24" android:targetSdkVersion="$target" />
            $body
        </manifest>
        """,
    ).indented()
}
