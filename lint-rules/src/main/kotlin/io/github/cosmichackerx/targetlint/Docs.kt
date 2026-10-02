package io.github.cosmichackerx.targetlint

internal const val DOCS_A16 = "https://developer.android.com/about/versions/16/behavior-changes-16"
internal const val DOCS_PREDICTIVE_BACK = "https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture"

/** Classes whose back handling the predictive back changes affect. */
internal val BACK_HOSTS = listOf("android.app.Activity", "android.app.Dialog")

internal const val DOCS_A17 = "https://developer.android.com/about/versions/17/behavior-changes-17"

/** True when the `<application>` that contains [element] declares `android:appCategory="game"` (games are exempt from the large-screen changes). */
internal fun isGameApp(element: org.w3c.dom.Element?): Boolean {
    var node: org.w3c.dom.Node? = element
    while (node != null) {
        if (node is org.w3c.dom.Element && node.tagName == "application") {
            return node.getAttributeNS(com.android.SdkConstants.ANDROID_URI, "appCategory") == "game"
        }
        node = node.parentNode
    }
    return false
}
