package io.github.cosmichackerx.targetlint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import org.jetbrains.uast.UReferenceExpression

/**
 * `ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED`: the Android 17 activity security notes ask developers to
 * migrate away from this legacy constant to granular modes such as `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE`.
 */
class BackgroundActivityStartDetector : Detector(), SourceCodeScanner {

    override fun getApplicableReferenceNames(): List<String> = listOf(LEGACY)

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
        val field = referenced as? PsiField ?: return
        if (field.containingClass?.qualifiedName != "android.app.ActivityOptions") return
        context.report(
            ISSUE,
            reference,
            context.getLocation(reference),
            "`$LEGACY` is the legacy background activity start mode. Android 17 asks apps to use a granular mode such as `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE`.",
            LintFix.create().replace().text(LEGACY).with(REPLACEMENT)
                .name("Use $REPLACEMENT (activity may start only while your app is visible)").build(),
        )
    }

    companion object {
        private const val LEGACY = "MODE_BACKGROUND_ACTIVITY_START_ALLOWED"
        private const val REPLACEMENT = "MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE"

        val ISSUE: Issue = Issue.create(
            id = "BackgroundActivityStartLegacyMode",
            briefDescription = "Legacy MODE_BACKGROUND_ACTIVITY_START_ALLOWED",
            explanation = """
                Android 17 hardens background activity launch restrictions and extends them to `IntentSender`. \
                The Android docs ask developers to migrate away from the legacy \
                `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` constant to granular controls such as \
                `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE`, which allows the start only while the calling \
                app is visible. The quick-fix changes behaviour; check that the activity start still works.
            """,
            category = Category.SECURITY,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(BackgroundActivityStartDetector::class.java, Scope.JAVA_FILE_SCOPE),
        ).setAndroidSpecific(true).addMoreInfo(DOCS_A17)
    }
}
