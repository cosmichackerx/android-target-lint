package io.github.cosmichackerx.targetlint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiField
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

/**
 * `KeyEvent.KEYCODE_BACK` handled in the key callbacks of an Activity or Dialog. On Android 16 the back key is not
 * delivered to apps that target API 36 (predictive back). Only the key callbacks of real Activity / Dialog subclasses
 * are checked, so a terminal emulator mapping `KEYCODE_BACK` to an escape sequence in its own class is not reported.
 */
class KeyCodeBackDetector : Detector(), SourceCodeScanner {

    override fun getApplicableReferenceNames(): List<String> = listOf("KEYCODE_BACK")

    override fun visitReference(context: JavaContext, reference: org.jetbrains.uast.UReferenceExpression, referenced: com.intellij.psi.PsiElement) {
        val field = referenced as? PsiField ?: return
        if (field.containingClass?.qualifiedName != "android.view.KeyEvent") return
        val method = reference.getParentOfType<UMethod>() ?: return
        if (method.name !in HANDLERS) return
        val cls = method.javaPsi.containingClass ?: return
        if (BACK_HOSTS.none { context.evaluator.extendsClass(cls, it, true) }) return
        val target = context.project.targetSdk
        val note = if (target >= 36) "Back key events are no longer delivered on Android 16 devices for apps targeting API $target."
        else "Back key events stop being delivered on Android 16 devices once the app targets API 36 (currently $target)."
        context.report(
            ISSUE,
            reference as UElement,
            context.getLocation(reference),
            "`KEYCODE_BACK` handled in `${method.name}`. $note Use an `OnBackPressedCallback` instead.",
        )
    }

    companion object {
        private val HANDLERS = setOf("onKeyDown", "onKeyUp", "onKeyPreIme", "onKeyLongPress", "dispatchKeyEvent")

        val ISSUE: Issue = Issue.create(
            id = "KeyCodeBackHandling",
            briefDescription = "KEYCODE_BACK handled in an Activity or Dialog key callback",
            explanation = """
                With predictive back (the default when targeting Android 16, API 36) the system does not dispatch \
                `KeyEvent.KEYCODE_BACK` to the app on Android 16 devices. Code that intercepts the back key in \
                `onKeyDown`, `onKeyUp` or `dispatchKeyEvent` stops working. Register an `OnBackPressedCallback`.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(KeyCodeBackDetector::class.java, Scope.JAVA_FILE_SCOPE),
        ).setAndroidSpecific(true).addMoreInfo(DOCS_A16).addMoreInfo(DOCS_PREDICTIVE_BACK)
    }
}
