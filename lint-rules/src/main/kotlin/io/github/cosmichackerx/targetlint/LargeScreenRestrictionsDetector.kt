package io.github.cosmichackerx.targetlint

import com.android.SdkConstants.ANDROID_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Element

/**
 * Manifest settings the Android 16 large-screen change ignores on displays of at least 600dp for apps that target
 * API 36: `resizeableActivity="false"`, `minAspectRatio`, `maxAspectRatio`. Also the temporary opt-out property
 * `android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`, which does not apply when the app targets API 37.
 * Games (`android:appCategory="game"`) are exempt from the change itself.
 */
class LargeScreenRestrictionsDetector : Detector(), XmlScanner {

    override fun getApplicableAttributes(): Collection<String> = listOf("resizeableActivity", "minAspectRatio", "maxAspectRatio")

    override fun getApplicableElements(): Collection<String> = listOf("property")

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (attribute.namespaceURI != ANDROID_URI) return
        val owner = attribute.ownerElement ?: return
        if (owner.tagName != "application" && owner.tagName != "activity") return
        if (isGameApp(owner)) return
        val name = attribute.localName
        val fix: LintFix
        if (name == "resizeableActivity") {
            if (attribute.value != "false") return
            fix = LintFix.create().set().android().attribute(name).value("true").name("Set resizeableActivity to true").build()
        } else {
            fix = LintFix.create().unset().android().attribute(name).name("Remove $name").build()
        }
        val shown = if (name == "resizeableActivity") "resizeableActivity=\"false\"" else name
        context.report(
            ISSUE,
            attribute,
            context.getLocation(attribute),
            "`$shown` has no effect on screens of 600dp and wider for apps that target API 36 or higher (this app: targetSdk ${context.project.targetSdk}); the system ignores it there. Make the layout adapt to the window size instead.",
            fix,
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.getAttributeNS(ANDROID_URI, "name") != PROPERTY) return
        if (element.getAttributeNS(ANDROID_URI, "value") != "true") return
        val parent = element.parentNode as? Element ?: return
        if (parent.tagName != "application" && parent.tagName != "activity") return
        val target = context.project.targetSdk
        val note = if (target >= 37) "This app targets API $target, so the property is ignored."
        else "It does not apply once the app targets API 37 (currently $target)."
        context.report(
            ISSUE_PROPERTY,
            element,
            context.getLocation(element),
            "`PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` is a temporary opt-out from the large-screen orientation, resizability and aspect ratio changes. $note",
            LintFix.create().replace().all().with("").name("Remove the opt-out property").build(),
        )
    }

    companion object {
        private const val PROPERTY = "android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY"

        val ISSUE: Issue = Issue.create(
            id = "LargeScreenRestrictionsIgnored",
            briefDescription = "resizeableActivity=false or aspect ratio limits are ignored on large screens",
            explanation = """
                For apps that target Android 16 (API 36), `android:resizeableActivity="false"`, \
                `android:minAspectRatio` and `android:maxAspectRatio` have no effect on screens of at least 600dp \
                (tablets, foldables, desktop windowing). There is a temporary opt-out for API 36 and none for \
                apps that target API 37. Games are exempt.
            """,
            category = Category.USABILITY,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(LargeScreenRestrictionsDetector::class.java, Scope.MANIFEST_SCOPE),
        ).setAndroidSpecific(true).addMoreInfo(DOCS_A16)

        val ISSUE_PROPERTY: Issue = Issue.create(
            id = "LargeScreenOptOutProperty",
            briefDescription = "Temporary large-screen opt-out property",
            explanation = """
                The manifest property `android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` opts an \
                activity or the application out of the Android 16 large-screen changes. The Android docs say \
                the opt-out is temporary and does not apply when the app targets API 37.
            """,
            category = Category.USABILITY,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(LargeScreenRestrictionsDetector::class.java, Scope.MANIFEST_SCOPE),
        ).setAndroidSpecific(true).addMoreInfo(DOCS_A16)
    }
}
