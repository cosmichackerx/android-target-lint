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

/** `android:enableOnBackInvokedCallback="false"` on `<application>` or `<activity>`: the documented, temporary opt-out. */
class PredictiveBackOptOutDetector : Detector(), XmlScanner {

    override fun getApplicableAttributes(): Collection<String> = listOf("enableOnBackInvokedCallback")

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (attribute.namespaceURI != ANDROID_URI || attribute.value != "false") return
        val owner = attribute.ownerElement?.tagName
        if (owner != "application" && owner != "activity") return
        context.report(
            ISSUE,
            attribute,
            context.getLocation(attribute),
            "Predictive back is switched off here (`enableOnBackInvokedCallback=\"false\"`). This is a temporary opt-out; migrate the back handling to `OnBackPressedCallback`.",
            LintFix.create().set().android().attribute("enableOnBackInvokedCallback").value("true")
                .name("Enable predictive back (set to true)").build(),
        )
    }

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "PredictiveBackOptOut",
            briefDescription = "Predictive back opt-out in the manifest",
            explanation = """
                `android:enableOnBackInvokedCallback="false"` turns predictive back off for the application or an \
                activity. It is meant as a stop-gap while back handling is migrated; keep track of it so it does \
                not become permanent.
            """,
            category = Category.CORRECTNESS,
            priority = 3,
            severity = Severity.INFORMATIONAL,
            implementation = Implementation(PredictiveBackOptOutDetector::class.java, Scope.MANIFEST_SCOPE),
        ).setAndroidSpecific(true).addMoreInfo(DOCS_PREDICTIVE_BACK)
    }
}
