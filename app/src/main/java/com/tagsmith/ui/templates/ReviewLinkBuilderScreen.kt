package com.tagsmith.ui.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tagsmith.core.nfc.NdefPayload
import com.tagsmith.core.nfc.ReviewLink
import com.tagsmith.core.nfc.byteSize
import com.tagsmith.ui.LocalAppContainer
import com.tagsmith.ui.components.BarIcon
import com.tagsmith.ui.components.Kicker
import com.tagsmith.ui.components.LabeledField
import com.tagsmith.ui.components.MiniChip
import com.tagsmith.ui.components.OutlineAction
import com.tagsmith.ui.components.PrimaryAction
import com.tagsmith.ui.components.StrongRule
import com.tagsmith.ui.components.TagsmithIcons
import com.tagsmith.ui.components.TextAction
import com.tagsmith.ui.payload.SMALLEST_STOCKED
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType
import com.tagsmith.ui.util.openLink
import com.tagsmith.ui.util.rememberClipboard
import kotlinx.coroutines.launch

/**
 * The single most common thing Tagsmith writes, given its own screen. Paste a
 * Place ID — or a review link the Business Profile already gave out — and the
 * review URL is built and sized for you.
 */
@Composable
fun ReviewLinkBuilderScreen(
    onClose: () -> Unit,
    onUse: (String) -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val clipboard = rememberClipboard()
    val scope = rememberCoroutineScope()
    val colors = Tagsmith.colors

    var input by remember { mutableStateOf("") }
    var business by remember { mutableStateOf("") }
    var savedAs by remember { mutableStateOf<String?>(null) }

    val result = ReviewLink.parse(input)
    val ready = result as? ReviewLink.Result.Ready
    val bytes = ready?.let { NdefPayload.Url(it.url).byteSize() } ?: 0

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BarIcon(TagsmithIcons.Close, "Close", onClose)
            Text("Review link builder", style = TagsmithType.RowTitle, color = colors.ink, modifier = Modifier.weight(1f))
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Find the business", style = TagsmithType.HeroSmall, color = colors.ink)
                Text(
                    "Paste a Place ID, or the \"Ask for reviews\" link from the Business Profile — we build the review URL for you.",
                    style = TagsmithType.BodySmall,
                    color = colors.inkMuted,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledField(
                    label = "Place ID or review link",
                    value = input,
                    onValueChange = {
                        input = it
                        savedAs = null
                    },
                    placeholder = "ChIJN1t_tDeuEmsRUsoyG83frY4",
                    mono = true,
                    keyboardType = KeyboardType.Uri,
                    error = (result as? ReviewLink.Result.Problem)?.message,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiniChip("Paste", onClick = { clipboard.paste { input = it; savedAs = null } })
                    if (input.isNotEmpty()) MiniChip("Clear", onClick = { input = "" })
                    Spacer(Modifier.weight(1f))
                    TextAction("Find a Place ID", onClick = { openLink(context, ReviewLink.PLACE_ID_FINDER) })
                }
            }

            LabeledField(
                label = "Business name",
                value = business,
                onValueChange = {
                    business = it
                    savedAs = null
                },
                placeholder = "Oakwell Coffee",
                capitalization = KeyboardCapitalization.Words,
                helper = "Only used to name the template.",
            )

            if (ready != null) {
                Column {
                    StrongRule()
                    Spacer(Modifier.height(12.dp))
                    Kicker("Generated URL")
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().background(colors.groundSubtle).padding(12.dp)) {
                        Text(ready.url, style = TagsmithType.Data, color = colors.ink)
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("$bytes bytes", style = TagsmithType.DataSmall, color = colors.inkMuted)
                        Text(
                            if (bytes <= SMALLEST_STOCKED) "fits every chip you stock" else "needs an NTAG215 or larger",
                            style = TagsmithType.DataSmall,
                            color = if (bytes <= SMALLEST_STOCKED) colors.success else colors.inkMuted,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    MiniChip("Open it to check", onClick = { openLink(context, ready.url) })
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryAction(
                label = "Use in write screen",
                onClick = { ready?.let { onUse(it.url) } },
                enabled = ready != null,
                trailingIcon = TagsmithIcons.ArrowRight,
                height = 56.dp,
            )
            OutlineAction(
                label = savedAs?.let { "Saved as “$it”" } ?: "Save as template",
                onClick = {
                    val url = ready?.url ?: return@OutlineAction
                    val name = business.trim().takeIf { it.isNotEmpty() }?.let { "$it review" } ?: "Google review link"
                    scope.launch {
                        container.templates.save(0, name, NdefPayload.Url(url), clientId = null, favourite = true)
                        savedAs = name
                    }
                },
                enabled = ready != null && savedAs == null,
                trailingIcon = if (savedAs != null) TagsmithIcons.Check else TagsmithIcons.Plus,
                height = 50.dp,
            )
        }
    }
}
