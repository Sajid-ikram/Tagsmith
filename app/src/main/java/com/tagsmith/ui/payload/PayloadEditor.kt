package com.tagsmith.ui.payload

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.tagsmith.core.nfc.NdefPayload
import com.tagsmith.core.nfc.PayloadType
import com.tagsmith.core.nfc.WifiSecurity
import com.tagsmith.core.nfc.issue
import com.tagsmith.ui.components.ChoiceChip
import com.tagsmith.ui.components.LabeledField
import com.tagsmith.ui.components.MiniChip
import com.tagsmith.ui.components.OutlineAction
import com.tagsmith.ui.components.SegmentedControl
import com.tagsmith.ui.components.SettingRow
import com.tagsmith.ui.components.SquareSwitch
import com.tagsmith.ui.components.TagsmithIcons
import com.tagsmith.ui.components.TagsmithSheet
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType
import com.tagsmith.ui.util.rememberClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** The scrollable chip row at the top of every payload editor. */
@Composable
fun PayloadTypeRow(
    selected: PayloadType,
    onSelect: (PayloadType) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PayloadType.entries.forEach { type ->
            ChoiceChip(label = type.label, selected = selected == type, onClick = { onSelect(type) })
        }
    }
}

/** The form for whichever type is selected. */
@Composable
fun PayloadForm(
    draft: PayloadDraft,
    modifier: Modifier = Modifier,
    onOpenReviewBuilder: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (draft.type) {
            PayloadType.URL -> UrlForm(draft, onOpenReviewBuilder)
            PayloadType.TEXT -> LabeledField(
                label = "Text to write",
                value = draft.text,
                onValueChange = { draft.text = it },
                placeholder = "Anything you like",
                singleLine = false,
                minLines = 3,
                capitalization = KeyboardCapitalization.Sentences,
            )

            PayloadType.CONTACT -> ContactForm(draft)
            PayloadType.WIFI -> WifiForm(draft)
            PayloadType.TEL -> LabeledField(
                label = "Phone number",
                value = draft.phone.number,
                onValueChange = { draft.phone = draft.phone.copy(number = it) },
                placeholder = "+44 117 496 0000",
                mono = true,
                keyboardType = KeyboardType.Phone,
                helper = "Tapping the tag opens the dialler with this number ready.",
            )

            PayloadType.SMS -> SmsForm(draft)
            PayloadType.EMAIL -> EmailForm(draft)
            PayloadType.LOCATION -> LocationForm(draft)
            PayloadType.APP -> AppForm(draft)
            PayloadType.RAW -> RawForm(draft)
        }
    }
}

@Composable
private fun UrlForm(draft: PayloadDraft, onOpenReviewBuilder: (() -> Unit)?) {
    val clipboard = rememberClipboard()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledField(
            label = "Destination URL",
            value = draft.url,
            onValueChange = { draft.url = it },
            placeholder = "https://",
            mono = true,
            keyboardType = KeyboardType.Uri,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniChip("Paste", onClick = { clipboard.paste { draft.url = it } })
            if (onOpenReviewBuilder != null) MiniChip("Review link builder", onClick = onOpenReviewBuilder, accent = true)
            if (draft.url.isNotBlank()) MiniChip("Clear", onClick = { draft.url = "" })
        }
    }
}

@Composable
private fun ContactForm(draft: PayloadDraft) {
    val c = draft.contact
    LabeledField(
        "Name", c.name, { draft.contact = c.copy(name = it) },
        placeholder = "Sam Rowe", capitalization = KeyboardCapitalization.Words,
    )
    LabeledField(
        "Phone", c.phone, { draft.contact = c.copy(phone = it) },
        placeholder = "+44 117 496 0000", mono = true, keyboardType = KeyboardType.Phone,
    )
    LabeledField(
        "Email", c.email, { draft.contact = c.copy(email = it) },
        placeholder = "sam@example.co", mono = true, keyboardType = KeyboardType.Email,
        error = c.issue(),
    )
    LabeledField(
        "Company", c.company, { draft.contact = c.copy(company = it) },
        placeholder = "Oakwell Coffee", capitalization = KeyboardCapitalization.Words,
    )
    LabeledField(
        "Website", c.website, { draft.contact = c.copy(website = it) },
        placeholder = "oakwell.co", mono = true, keyboardType = KeyboardType.Uri,
        helper = "A name or a company is enough. Every extra field costs bytes.",
    )
}

@Composable
private fun WifiForm(draft: PayloadDraft) {
    val w = draft.wifi
    LabeledField(
        "Network name (SSID)", w.ssid, { draft.wifi = w.copy(ssid = it) },
        placeholder = "Oakwell_Guest", mono = true,
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        com.tagsmith.ui.components.Kicker("Security")
        SegmentedControl(
            options = WifiSecurity.entries.map { it.label },
            selectedIndex = WifiSecurity.entries.indexOf(w.security),
            onSelect = { draft.wifi = w.copy(security = WifiSecurity.entries[it]) },
        )
    }
    if (w.security == WifiSecurity.WPA) {
        LabeledField(
            "Password", w.password, { draft.wifi = w.copy(password = it) },
            mono = true, secret = true, error = w.issue(),
            helper = "WPA3-only networks may make the phone finish joining by hand; mixed WPA2/WPA3 routers join on tap.",
        )
    }
    Column {
        SettingRow("Hidden network", divider = false) {
            SquareSwitch(w.hidden, { draft.wifi = w.copy(hidden = it) })
        }
        if (w.hidden) {
            Text(
                "Saved with the template, but no NFC Wi-Fi standard carries a hidden flag — " +
                    "a phone tapping this tag can only join if it can see the network.",
                style = TagsmithType.RowMeta,
                color = Tagsmith.colors.inkFaint,
            )
        }
    }
}

@Composable
private fun SmsForm(draft: PayloadDraft) {
    val s = draft.sms
    LabeledField(
        "Send to", s.number, { draft.sms = s.copy(number = it) },
        placeholder = "+44 117 496 0000", mono = true, keyboardType = KeyboardType.Phone,
    )
    LabeledField(
        "Message", s.body, { draft.sms = s.copy(body = it) },
        placeholder = "Hi! Table for two tonight?", singleLine = false, minLines = 3,
        capitalization = KeyboardCapitalization.Sentences,
        helper = "Opens the messaging app with this text ready to send.",
    )
}

@Composable
private fun EmailForm(draft: PayloadDraft) {
    val e = draft.email
    LabeledField(
        "Address", e.address, { draft.email = e.copy(address = it) },
        placeholder = "hello@oakwell.co", mono = true, keyboardType = KeyboardType.Email, error = e.issue(),
    )
    LabeledField(
        "Subject", e.subject, { draft.email = e.copy(subject = it) },
        placeholder = "Booking enquiry", capitalization = KeyboardCapitalization.Sentences,
    )
    LabeledField(
        "Body", e.body, { draft.email = e.copy(body = it) },
        singleLine = false, minLines = 3, capitalization = KeyboardCapitalization.Sentences,
    )
}

@Composable
private fun LocationForm(draft: PayloadDraft) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val l = draft.location
    var lookup by remember { mutableStateOf<String?>(null) }
    var looking by remember { mutableStateOf(false) }

    SegmentedControl(
        options = listOf("Coordinates", "Address"),
        selectedIndex = if (l.byAddress) 1 else 0,
        onSelect = { draft.location = l.copy(byAddress = it == 1) },
    )
    if (l.byAddress) {
        LabeledField(
            "Address", l.address, { draft.location = l.copy(address = it) },
            placeholder = "14 Perry Rd, Bristol BS1", singleLine = false, minLines = 2,
            capitalization = KeyboardCapitalization.Words,
            helper = "Written as a maps search — the tapping phone finds the place itself.",
        )
        if (Geocoder.isPresent() && l.address.isNotBlank()) {
            OutlineAction(
                label = if (looking) "Looking up…" else "Convert to coordinates",
                onClick = {
                    looking = true
                    scope.launch {
                        val found = geocode(context, l.address)
                        looking = false
                        if (found == null) {
                            lookup = "Couldn't find that address. Check it, or enter coordinates."
                        } else {
                            lookup = null
                            draft.location = l.copy(
                                latitude = "%.6f".format(java.util.Locale.ROOT, found.first),
                                longitude = "%.6f".format(java.util.Locale.ROOT, found.second),
                                byAddress = false,
                            )
                        }
                    }
                },
                enabled = !looking,
                trailingIcon = TagsmithIcons.Locate,
            )
        }
        lookup?.let { Text(it, style = TagsmithType.RowMeta, color = Tagsmith.colors.danger) }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledField(
                "Latitude", l.latitude, { draft.location = l.copy(latitude = it) },
                placeholder = "51.4545", mono = true, keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
            LabeledField(
                "Longitude", l.longitude, { draft.location = l.copy(longitude = it) },
                placeholder = "-2.5879", mono = true, keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
        }
        l.issue()?.let { Text(it, style = TagsmithType.RowMeta, color = Tagsmith.colors.danger) }
    }
}

@Composable
private fun AppForm(draft: PayloadDraft) {
    val colors = Tagsmith.colors
    var picking by remember { mutableStateOf(false) }
    val app = draft.app
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        com.tagsmith.ui.components.Kicker("App to open")
        Row(
            Modifier
                .fillMaxWidth()
                .border(2.dp, colors.rule, RectangleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = { picking = true },
                )
                .heightIn(min = 60.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (app.packageName.isBlank()) {
                Text("Choose an installed app", style = TagsmithType.Body, color = colors.inkFainter, modifier = Modifier.weight(1f))
            } else {
                AppIcon(app.packageName, 32)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label.ifBlank { app.packageName }, style = TagsmithType.RowTitle, color = colors.ink)
                    Text(app.packageName, style = TagsmithType.DataTiny, color = colors.inkFaint)
                }
            }
            Icon(TagsmithIcons.ChevronDown, null, tint = colors.inkFaint, modifier = Modifier.size(18.dp))
        }
        Text(
            "An Android Application Record. Tapping opens the app — or its Play Store page on a phone without it. iPhones ignore it.",
            style = TagsmithType.RowMeta,
            color = colors.inkFaint,
        )
    }
    if (picking) {
        AppPickerSheet(
            onPick = { label, pkg ->
                draft.app = NdefPayload.App(pkg, label)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun RawForm(draft: PayloadDraft) {
    val r = draft.raw
    LabeledField(
        "MIME type", r.mimeType, { draft.raw = r.copy(mimeType = it) },
        placeholder = "application/x-oakwell", mono = true, keyboardType = KeyboardType.Uri,
        error = r.issue()?.takeIf { it.startsWith("A MIME") },
    )
    LabeledField(
        "Payload", r.payload, { draft.raw = r.copy(payload = it) },
        placeholder = if (r.isHex) "DE AD BE EF" else "Payload text", mono = true,
        singleLine = false, minLines = 3,
        error = r.issue()?.takeIf { !it.startsWith("A MIME") },
    )
    SettingRow("Payload is hex", divider = false) {
        SquareSwitch(r.isHex, { draft.raw = r.copy(isHex = it) })
    }
}

private data class InstalledApp(val label: String, val packageName: String)

@Composable
private fun AppPickerSheet(onPick: (String, String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = Tagsmith.colors
    val apps by produceState<List<InstalledApp>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { launchableApps(context) }
    }
    TagsmithSheet(onDismiss = onDismiss, title = "Choose an app") {
        val list = apps
        if (list == null) {
            Text("Reading installed apps…", style = TagsmithType.BodySmall, color = colors.inkMuted, modifier = Modifier.padding(20.dp))
        } else {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                items(list, key = { it.packageName }) { app ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onPick(app.label, app.packageName) },
                            )
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app.packageName, 36)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = TagsmithType.RowTitle, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(app.packageName, style = TagsmithType.DataTiny, color = colors.inkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(packageName: String, sizeDp: Int) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
    }
    Box(Modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(sizeDp.dp)) }
            ?: Icon(TagsmithIcons.App, null, tint = Tagsmith.colors.inkFaint, modifier = Modifier.size((sizeDp * 0.7f).dp))
    }
}

/** Apps with a launcher icon — the ones an operator would recognise by name. */
private fun launchableApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(intent, 0)
    }
    return resolved
        .map { InstalledApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

/** Address to coordinates, using whatever geocoder the phone ships. */
private suspend fun geocode(context: Context, address: String): Pair<Double, Double>? {
    val geocoder = Geocoder(context)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { continuation ->
            geocoder.getFromLocationName(
                address,
                1,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<android.location.Address>) {
                        val first = addresses.firstOrNull()
                        continuation.resume(first?.let { it.latitude to it.longitude })
                    }

                    override fun onError(errorMessage: String?) {
                        continuation.resume(null)
                    }
                },
            )
        }
    } else {
        withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(address, 1)?.firstOrNull()?.let { it.latitude to it.longitude }
            }.getOrNull()
        }
    }
}
