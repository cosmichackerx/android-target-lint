package io.github.cosmichackerx.targetlint

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

/**
 * `<item name="android:windowOptOutEdgeToEdgeEnforcement">true</item>` in a style or theme. The opt-out exists for
 * apps that target API 35; it is disabled on Android 16 devices once the app targets API 36.
 */
class EdgeToEdgeOptOutDetector : ResourceXmlDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean = folderType == ResourceFolderType.VALUES

    override fun getApplicableElements(): Collection<String> = listOf("item")

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.getAttribute("name") != "android:windowOptOutEdgeToEdgeEnforcement") return
        val value = element.textContent.trim()
        if (value != "true") return
        val target = context.project.targetSdk
        val note = if (target >= 36) "This app targets API $target, so the opt-out is ignored on Android 16 devices."
        else "It stops working on Android 16 devices once the app targets API 36 (currently $target)."
        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "`windowOptOutEdgeToEdgeEnforcement` is a temporary opt-out. $note Handle window insets instead.",
            LintFix.create().replace().text("true").with("false").name("Set the opt-out to false").build(),
        )
    }

    companion object {

        val ISSUE: Issue = Issue.create(
            id = "EdgeToEdgeOptOut",
            briefDescription = "Theme opts out of edge-to-edge enforcement",
            explanation = """
                Android 15 enforces edge-to-edge for apps that target API 35, with the attribute \
                `android:windowOptOutEdgeToEdgeEnforcement` as a temporary opt-out. The opt-out is disabled on \
                Android 16 devices for apps that target API 36. Draw behind the system bars and apply \
                `WindowInsets` (for example with `enableEdgeToEdge()` and `ViewCompat.setOnApplyWindowInsetsListener`).
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(EdgeToEdgeOptOutDetector::class.java, Scope.RESOURCE_FILE_SCOPE),
        ).setAndroidSpecific(true).addMoreInfo(DOCS_A16)
    }
}
