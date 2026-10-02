package com.example.sample

import android.app.ActivityOptions
import android.view.contentcapture.ContentCaptureManager

/** Android 17 findings: the legacy background-activity-start mode and setContentCaptureEnabled(false). */
class Extras {
    fun options(): ActivityOptions =
        ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)

    fun noCapture(manager: ContentCaptureManager) {
        manager.setContentCaptureEnabled(false)
    }
}
