package com.tagsmith.ui.nav

import android.net.Uri
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
    const val TEMPLATES = "templates"

    const val ARG_ID = "id"
    const val ARG_URL = "url"
    const val ARG_TEMPLATE = "templateId"
    const val ARG_CLIENT = "clientId"
    const val ARG_PAYLOAD = "payload"
    const val ARG_NAME = "name"
    const val ARG_RETURNS = "returns"

    /** Keys for values a screen hands back to the one that opened it. */
    const val RESULT_REVIEW_URL = "reviewUrl"
    const val RESULT_NEW_CLIENT = "newClientId"

    const val WRITE = "write?$ARG_URL={$ARG_URL}&$ARG_TEMPLATE={$ARG_TEMPLATE}"
    fun write(prefill: String? = null, templateId: Long? = null): String =
        "write" + query(ARG_URL to prefill, ARG_TEMPLATE to templateId?.toString())

    const val TEMPLATE_EDIT = "template_edit?$ARG_ID={$ARG_ID}&$ARG_PAYLOAD={$ARG_PAYLOAD}&$ARG_NAME={$ARG_NAME}"
    fun templateEdit(id: Long? = null, payload: String? = null, name: String? = null): String =
        "template_edit" + query(ARG_ID to id?.toString(), ARG_PAYLOAD to payload, ARG_NAME to name)

    const val REVIEW_BUILDER = "review_builder?$ARG_RETURNS={$ARG_RETURNS}"
    fun reviewBuilder(returnsResult: Boolean): String = "review_builder?$ARG_RETURNS=$returnsResult"

    const val CLIENT = "client/{$ARG_ID}"
    fun client(id: Long): String = "client/$id"

    const val CLIENT_EDIT = "client_edit?$ARG_ID={$ARG_ID}&$ARG_RETURNS={$ARG_RETURNS}"
    fun clientEdit(id: Long? = null, returnsResult: Boolean = false): String =
        "client_edit" + query(ARG_ID to id?.toString(), ARG_RETURNS to returnsResult.toString())

    const val BATCH_SETUP = "batch_setup?$ARG_TEMPLATE={$ARG_TEMPLATE}&$ARG_CLIENT={$ARG_CLIENT}&$ARG_PAYLOAD={$ARG_PAYLOAD}"
    fun batchSetup(templateId: Long? = null, clientId: Long? = null, payload: String? = null): String =
        "batch_setup" + query(ARG_TEMPLATE to templateId?.toString(), ARG_CLIENT to clientId?.toString(), ARG_PAYLOAD to payload)

    const val BATCH_RUN = "batch/{$ARG_ID}"
    fun batchRun(id: Long): String = "batch/$id"

    const val BATCH_SUMMARY = "batch_summary/{$ARG_ID}"
    fun batchSummary(id: Long): String = "batch_summary/$id"

    const val HISTORY_DETAIL_ARG = "entryId"
    const val HISTORY_DETAIL = "history_detail/{$HISTORY_DETAIL_ARG}"
    fun historyDetail(id: Long): String = "history_detail/$id"

    /** Optional arguments as a query string, encoded; absent ones are left out. */
    private fun query(vararg pairs: Pair<String, String?>): String {
        val present = pairs.filter { it.second != null }
        if (present.isEmpty()) return ""
        return "?" + present.joinToString("&") { (k, v) -> "$k=${Uri.encode(v)}" }
    }
}

/** The four bottom destinations. Scan is not one of them — it is the button above them. */
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
