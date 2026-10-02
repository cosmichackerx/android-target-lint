package io.github.cosmichackerx.targetlint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/**
 * `ContentCaptureManager.setContentCaptureEnabled(false)`: deprecated in Android 17, and for apps that target API 37
 * it no longer disables Content Capture. The documented replacement is `FLAG_SECURE` on the window.
 */
class ContentCaptureDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("setContentCaptureEnabled")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "android.view.contentcapture.ContentCaptureManager")) return
        val arg = node.valueArguments.firstOrNull() ?: return
        if (ConstantEvaluator.evaluate(context, arg) == true) return
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "`setContentCaptureEnabled` is deprecated and no longer disables Content Capture once the app targets API 37 (currently ${context.project.targetSdk}). Set `FLAG_SECURE` on the window instead.",
        )
    }

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "ContentCaptureEnabledDeprecated",
            briefDescription = "setContentCaptureEnabled no longer disables Content Capture on API 37",
            explanation = """
                Android 17 deprecates `ContentCaptureManager.setContentCaptureEnabled(boolean)`. For apps that \
                target API 37, calling `setContentCaptureEnabled(false)` no longer disables Content Capture. To \
                keep screen contents out of Content Capture, set `WindowManager.LayoutParams.FLAG_SECURE` on the window.
            """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(ContentCaptureDetector::class.java, Scope.JAVA_FILE_SCOPE),
        ).setAndroidSpecific(true).addMoreInfo(DOCS_A17)
    }
}
