package io.github.cosmichackerx.targetlint

import com.android.tools.lint.detector.api.BooleanOption
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
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod

/**
 * `KeyEvent.KEYCODE_BACK` handled in the key callbacks of an Activity or Dialog, in an `OnKeyListener`, or in a lambda given to `setOnKeyListener`. With the `checkViews` option, key callbacks of `android.view.View` subclasses are checked too. On Android 16 the back key is not
 * delivered to apps that target API 36 (predictive back). Only the key callbacks of real Activity / Dialog subclasses
 * are checked, so a terminal emulator mapping `KEYCODE_BACK` to an escape sequence in its own class is not reported.
 */
class KeyCodeBackDetector : Detector(), SourceCodeScanner {

    override fun getApplicableReferenceNames(): List<String> = listOf("KEYCODE_BACK")

    override fun visitReference(context: JavaContext, reference: org.jetbrains.uast.UReferenceExpression, referenced: com.intellij.psi.PsiElement) {
        val field = referenced as? PsiField ?: return
        if (field.containingClass?.qualifiedName != "android.view.KeyEvent") return
        val checkViews = CHECK_VIEWS.getValue(context.configuration)
        val handler = handlerName(context, reference as UElement, checkViews) ?: return
        val target = context.project.targetSdk
        val note = if (target >= 36) "Back key events are no longer delivered on Android 16 devices for apps targeting API $target."
        else "Back key events stop being delivered on Android 16 devices once the app targets API 36 (currently $target)."
        context.report(
            ISSUE,
            reference as UElement,
            context.getLocation(reference),
            "`KEYCODE_BACK` handled in `$handler`. $note Use an `OnBackPressedCallback` instead.",
        )
    }

    /**
     * The name of the key callback that contains [from], or null when it is not a back-relevant one: a key callback of an
     * Activity/Dialog subclass, the `onKey` of an `OnKeyListener`, or a lambda given to `setOnKeyListener`.
     */
    private fun handlerName(context: JavaContext, from: UElement, checkViews: Boolean): String? {
        var node: UElement? = from.uastParent
        while (node != null) {
            if (node is ULambdaExpression) {
                val call = node.uastParent as? UCallExpression
                // methodName is null when the receiver type does not resolve (library dialogs missing from the classpath)
                val name = call?.methodName ?: call?.methodIdentifier?.name
                if (name in LISTENER_SETTERS) return name
            }
            if (node is UMethod) {
                val cls = node.javaPsi.containingClass ?: return null
                val evaluator = context.evaluator
                val hosts = if (checkViews) BACK_HOSTS + VIEW_HOST else BACK_HOSTS
                if (node.name in HANDLERS && hosts.any { evaluator.extendsClass(cls, it, true) }) return node.name
                if (node.name == "onKey" && KEY_LISTENERS.any { evaluator.implementsInterface(cls, it, false) }) return node.name
                return null
            }
            node = node.uastParent
        }
        return null
    }

    companion object {
        private const val VIEW_HOST = "android.view.View"

        /** Off by default: `View.onKeyDown` overrides are often key-mapping tables (terminals, game input) rather than back handling. */
        val CHECK_VIEWS = BooleanOption(
            "checkViews",
            "Also report KEYCODE_BACK in key callbacks (onKeyDown, onKeyUp, onKeyPreIme, onKeyLongPress, dispatchKeyEvent) of android.view.View subclasses",
            false,
            "Set to true to include View subclasses. They are off by default because many views map every key code, including KEYCODE_BACK, for input handling.",
        )

        private val LISTENER_SETTERS = setOf("setOnKeyListener", "setOnDispatchKeyListener")
        private val KEY_LISTENERS = listOf("android.view.View.OnKeyListener", "android.content.DialogInterface.OnKeyListener")
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
        ).setAndroidSpecific(true).addMoreInfo(DOCS_A16).addMoreInfo(DOCS_PREDICTIVE_BACK).setOptions(listOf(CHECK_VIEWS))
    }
}
