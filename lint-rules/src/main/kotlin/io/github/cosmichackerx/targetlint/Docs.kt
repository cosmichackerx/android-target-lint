package io.github.cosmichackerx.targetlint

internal const val DOCS_A16 = "https://developer.android.com/about/versions/16/behavior-changes-16"
internal const val DOCS_PREDICTIVE_BACK = "https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture"

/** Classes whose back handling the predictive back changes affect. */
internal val BACK_HOSTS = listOf("android.app.Activity", "android.app.Dialog")
