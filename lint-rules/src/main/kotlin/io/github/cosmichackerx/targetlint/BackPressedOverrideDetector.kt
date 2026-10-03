package io.github.cosmichackerx.targetlint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod

/**
 * `onBackPressed()` overridden in an Activity or Dialog. With predictive back on by default for apps that target
 * Android 16 (API 36) the system no longer calls it on devices running Android 16.
 *
 * Unlike a text search this resolves the class: only real subclasses of android.app.Activity / android.app.Dialog
 * (directly or through androidx classes) are reported, not methods that merely share the name.
 */
class BackPressedOverrideDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = object : UElementHandler() {
        override fun visitMethod(node: UMethod) {
            if (node.name != "onBackPressed" || node.uastParameters.isNotEmpty() || node.isConstructor) return
            val cls = node.javaPsi.containingClass ?: return
            val evaluator = context.evaluator
            if (BACK_HOSTS.none { evaluator.extendsClass(cls, it, true) }) return
            // A no-argument onBackPressed() in a subclass of Activity/Dialog always overrides the inherited method, even when
            // an intermediate class (androidx ComponentActivity, AppCompatActivity, ...) overrides it first.
            val target = context.project.targetSdk
            val note = if (target >= 36) "This app targets API $target, so it is no longer called on Android 16 devices."
            else "It will stop being called on Android 16 devices once the app targets API 36 (currently $target)."
            context.report(
                ISSUE,
                node,
                context.getNameLocation(node),
                "`onBackPressed()` is deprecated with predictive back. $note Use `OnBackPressedDispatcher.addCallback` / `OnBackPressedCallback` instead.",
            )
        }
    }

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "OnBackPressedOverride",
            briefDescription = "Activity or Dialog overrides onBackPressed()",
            explanation = """
                Apps that target Android 16 (API 36) get predictive back by default, and the system no longer calls \
                `Activity.onBackPressed()` or dispatches `KeyEvent.KEYCODE_BACK` to the app on Android 16 devices. \
                Register an `OnBackPressedCallback` with the activity's `onBackPressedDispatcher` instead.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(BackPressedOverrideDetector::class.java, Scope.JAVA_FILE_SCOPE),
        ).setAndroidSpecific(true).addMoreInfo(DOCS_A16).addMoreInfo(DOCS_PREDICTIVE_BACK)
    }
}
