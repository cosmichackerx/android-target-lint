package io.github.cosmichackerx.targetlint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

/** Registers the rules of this pack. Loaded through the `Lint-Registry-v2` entry in the jar manifest. */
class TargetLintRegistry : IssueRegistry() {
    override val issues: List<Issue> = listOf(
        BackPressedOverrideDetector.ISSUE,
        KeyCodeBackDetector.ISSUE,
        EdgeToEdgeOptOutDetector.ISSUE,
        PredictiveBackOptOutDetector.ISSUE,
        FixedOrientationDetector.ISSUE_MANIFEST,
        FixedOrientationDetector.ISSUE_CODE,
        LargeScreenRestrictionsDetector.ISSUE,
        LargeScreenRestrictionsDetector.ISSUE_PROPERTY,
        ContentCaptureDetector.ISSUE,
        BackgroundActivityStartDetector.ISSUE,
    )

    override val api: Int = CURRENT_API

    // Rules only use APIs that exist in older lint versions; 14 is the lowest level lint accepts.
    override val minApi: Int = 14

    override val vendor: Vendor = Vendor(
        vendorName = "android-target-lint",
        identifier = "io.github.cosmichackerx:android-target-lint",
        feedbackUrl = "https://github.com/cosmichackerx/android-target-lint/issues",
        contact = "https://github.com/cosmichackerx/android-target-lint",
    )
}
