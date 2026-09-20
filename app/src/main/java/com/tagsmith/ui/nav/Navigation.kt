package com.tagsmith.ui.nav

import androidx.compose.ui.graphics.vector.ImageVector
import com.tagsmith.ui.components.TagsmithIcons

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val TAGS = "tags"
    const val CLIENTS = "clients"
    const val HISTORY = "history"
    const val SCAN = "scan"
    const val DETAILS = "details"
    const val SETTINGS = "settings"
    const val LOCK = "lock"
    const val ERASE = "erase"

    const val WRITE_ARG_URL = "url"
    const val WRITE = "write"
    const val WRITE_WITH_URL = "write?$WRITE_ARG_URL={$WRITE_ARG_URL}"

    fun write(prefill: String? = null): String =
        if (prefill.isNullOrBlank()) WRITE else "$WRITE?$WRITE_ARG_URL=${android.net.Uri.encode(prefill)}"

    const val HISTORY_DETAIL_ARG = "entryId"
    const val HISTORY_DETAIL = "history_detail/{$HISTORY_DETAIL_ARG}"
    fun historyDetail(id: Long): String = "history_detail/$id"
}

/** The four bottom destinations. Scan is not one of them — it is the button between them. */
enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME(Routes.HOME, "Home", TagsmithIcons.Home),
    TAGS(Routes.TAGS, "Tags", TagsmithIcons.Tag),
    CLIENTS(Routes.CLIENTS, "Clients", TagsmithIcons.Clients),
    HISTORY(Routes.HISTORY, "History", TagsmithIcons.History),
}
