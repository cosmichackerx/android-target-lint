package io.github.cosmichackerx.targetlint

import com.android.SdkConstants.ANDROID_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Attr

/**
 * Orientation locks that apps targeting API 36 can no longer rely on: on screens of at least 600dp the system ignores
 * `screenOrientation` portrait/landscape (and `setRequestedOrientation` with such a value), `resizeableActivity=false`
 * and aspect ratio limits. There is no opt-out when the app targets API 37. Games (`android:appCategory="game"`) are exempt.
 */
class FixedOrientationDetector : Detector(), XmlScanner, SourceCodeScanner {

    // ---- manifest -------------------------------------------------------------------------------------------
    override fun getApplicableAttributes(): Collection<String> = listOf("screenOrientation")

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (attribute.namespaceURI != ANDROID_URI) return
        if (attribute.value !in MANIFEST_VALUES || isGameApp(attribute.ownerElement)) return
        context.report(
            ISSUE_MANIFEST,
            attribute,
            context.getLocation(attribute),
            "`screenOrientation=\"${attribute.value}\"` is ignored on screens of 600dp and wider for apps that target API 36 or higher (this app: targetSdk ${context.project.targetSdk}); ${NO_OPT_OUT}",
            LintFix.create().set().android().attribute("screenOrientation").value("unspecified")
                .name("Set screenOrientation to unspecified").build(),
        )
    }

    // ---- code -----------------------------------------------------------------------------------------------
    override fun getApplicableMethodNames(): List<String> = listOf("setRequestedOrientation")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInSubClassOf(method, "android.app.Activity", false)) return
        val arg = node.valueArguments.firstOrNull() ?: return
        val value = ConstantEvaluator.evaluate(context, arg) as? Int ?: return
        if (value !in CODE_VALUES) return
        context.report(
            ISSUE_CODE,
            node,
            context.getLocation(node),
            "`setRequestedOrientation` with a fixed portrait/landscape value is ignored on screens of 600dp and wider for apps that target API 36 or higher (this app: targetSdk ${context.project.targetSdk}); ${NO_OPT_OUT}",
        )
    }

    companion object {
        private const val NO_OPT_OUT = "there is no opt-out at API 37. Support both orientations or use a size-class based layout."
        private val MANIFEST_VALUES = setOf(
            "portrait", "landscape", "reversePortrait", "reverseLandscape",
            "sensorPortrait", "sensorLandscape", "userPortrait", "userLandscape",
        )

        // ActivityInfo.SCREEN_ORIENTATION_*: LANDSCAPE 0, PORTRAIT 1, SENSOR_LANDSCAPE 6, SENSOR_PORTRAIT 7,
        // REVERSE_LANDSCAPE 8, REVERSE_PORTRAIT 9, USER_LANDSCAPE 11, USER_PORTRAIT 12
        private val CODE_VALUES = setOf(0, 1, 6, 7, 8, 9, 11, 12)

        private const val EXPLANATION = """
            On displays of at least 600dp (tablets, foldables, desktop windowing), Android 16 ignores fixed orientation, \
            non-resizeable and aspect ratio restrictions for apps that target API 36, with a temporary opt-out; \
            apps that target API 37 have no opt-out. Games are exempt.
        """

        val ISSUE_MANIFEST: Issue = Issue.create(
            id = "FixedOrientationManifest",
            briefDescription = "Fixed screenOrientation is ignored on large screens",
            explanation = EXPLANATION,
            category = Category.USABILITY,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(FixedOrientationDetector::class.java, Scope.MANIFEST_SCOPE),
        ).setAndroidSpecific(true).addMoreInfo(DOCS_A16)

        val ISSUE_CODE: Issue = Issue.create(
            id = "FixedOrientationCode",
            briefDescription = "setRequestedOrientation with a fixed orientation is ignored on large screens",
            explanation = EXPLANATION,
            category = Category.USABILITY,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(FixedOrientationDetector::class.java, Scope.JAVA_FILE_SCOPE),
        ).setAndroidSpecific(true).addMoreInfo(DOCS_A16)
    }
}
