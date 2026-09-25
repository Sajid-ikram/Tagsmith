package com.tagsmith.ui.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.core.net.toUri
import kotlinx.coroutines.launch

/** Opens a decoded record's link, failing quietly rather than crashing the scan. */
fun openLink(context: Context, url: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Nothing on this phone opens that link", Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, "That link can't be opened from here", Toast.LENGTH_SHORT).show()
    }
}

/** The deep link into the system NFC page, with a settings-root fallback. */
fun openNfcSettings(context: Context) {
    val intents = listOf(
        Intent(Settings.ACTION_NFC_SETTINGS),
        Intent(Settings.ACTION_WIRELESS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            continue
        }
    }
    Toast.makeText(context, "Couldn't open system settings", Toast.LENGTH_SHORT).show()
}

/** Hands a CSV or JSON export to whatever the operator wants to send it with. */
fun shareText(context: Context, subject: String, body: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(
        Intent.createChooser(intent, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/** Copying a UID or a payload is a one-liner at the call site, not a coroutine. */
@Composable
fun rememberClipboard(): TagsmithClipboard {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, scope) {
        object : TagsmithClipboard {
            override fun copy(text: String) {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Tagsmith", text)))
                }
            }

            override fun paste(onResult: (String) -> Unit) {
                scope.launch {
                    val text = clipboard.getClipEntry()
                        ?.clipData
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.text
                        ?.toString()
                    if (!text.isNullOrBlank()) onResult(text.trim())
                }
            }
        }
    }
}

interface TagsmithClipboard {
    fun copy(text: String)
    fun paste(onResult: (String) -> Unit)
}

/**
 * The locale to format dates in, read so that a language change recomposes.
 * `Locale.getDefault()` inside a composable would keep the old one.
 */
@Composable
fun currentLocale(): java.util.Locale =
    androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
