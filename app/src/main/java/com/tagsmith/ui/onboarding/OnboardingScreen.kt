package com.tagsmith.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.tagsmith.core.data.Client
import com.tagsmith.core.nfc.NfcAvailability
import com.tagsmith.ui.LocalAppContainer
import com.tagsmith.ui.components.ClientAvatar
import com.tagsmith.ui.components.ColorSwatches
import com.tagsmith.ui.components.LabeledField
import com.tagsmith.ui.components.paletteColorFor
import kotlinx.coroutines.launch
import com.tagsmith.ui.components.BarIcon
import com.tagsmith.ui.components.DataRow
import com.tagsmith.ui.components.InfoNote
import com.tagsmith.ui.components.OutlineAction
import com.tagsmith.ui.components.PhoneMark
import com.tagsmith.ui.components.PrimaryAction
import com.tagsmith.ui.components.StrongRule
import com.tagsmith.ui.components.TagsmithIcons
import com.tagsmith.ui.components.TextAction
import com.tagsmith.ui.theme.OnTapGround
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType

/**
 * Three light slides, skippable. The second one is really a device check, so it
 * changes shape depending on what the radio reports; the third offers to add a
 * first client, and can be passed over.
 */
@Composable
fun OnboardingScreen(
    availability: NfcAvailability,
    onOpenNfcSettings: () -> Unit,
    onFinish: () -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var slide by remember { mutableIntStateOf(0) }
    var clientName by remember { mutableStateOf("") }
    var clientColor by remember { mutableIntStateOf(paletteColorFor(0).toArgb()) }

    val finish: () -> Unit = {
        val name = clientName.trim()
        if (name.isNotEmpty()) {
            scope.launch {
                container.clients.save(Client(name = name, color = clientColor, createdAt = System.currentTimeMillis()))
                onFinish()
            }
        } else {
            onFinish()
        }
    }

    // A phone with no radio is a dead end, not a slide — say so and stop.
    if (availability == NfcAvailability.ABSENT) {
        NoNfcHardwareScreen(onContinue = onFinish)
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Tagsmith.colors.ground)
            .systemBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextAction("Skip", onClick = onFinish, color = Tagsmith.colors.inkFaint)
        }

        when (slide) {
            0 -> IntroSlide(Modifier.weight(1f))
            1 -> if (availability == NfcAvailability.DISABLED) {
                NfcOffSlide(Modifier.weight(1f))
            } else {
                NfcReadySlide(Modifier.weight(1f))
            }

            else -> FirstClientSlide(
                name = clientName,
                onNameChange = { clientName = it },
                color = clientColor,
                onColorChange = { clientColor = it },
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ProgressTicks(current = slide, total = 3)
            if (slide == 1 && availability == NfcAvailability.DISABLED) {
                PrimaryAction(
                    label = "Open NFC settings",
                    onClick = onOpenNfcSettings,
                    trailingIcon = TagsmithIcons.ExternalLink,
                    height = 56.dp,
                )
                Spacer(Modifier.height(0.dp))
                OutlineAction(label = "Continue without NFC", onClick = { slide = 2 })
            } else {
                PrimaryAction(
                    label = when {
                        slide < 2 -> "Continue"
                        clientName.isNotBlank() -> "Add client & start"
                        else -> "Start scanning"
                    },
                    onClick = { if (slide == 2) finish() else slide++ },
                    trailingIcon = TagsmithIcons.ArrowRight,
                    height = 56.dp,
                )
            }
        }
    }
}

@Composable
private fun IntroSlide(modifier: Modifier = Modifier) {
    val colors = Tagsmith.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(220.dp).border(2.dp, Color(0xFFE6D6C4), CircleShape))
            Box(Modifier.size(152.dp).border(2.dp, Color(0xFFEFE2D2), CircleShape))
            PhoneMark(
                width = 96.dp,
                height = 150.dp,
                outline = colors.ink,
                fill = Color.White,
                bob = true,
                alignBottom = true,
            )
        }
        Spacer(Modifier.height(34.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Program a tag\nin one tap.", style = TagsmithType.Hero, color = colors.ink)
            Text(
                text = "Write review links, contact cards and Wi-Fi to the tags you sell — " +
                    "and remember exactly which card went to which client.",
                style = TagsmithType.Body,
                color = colors.inkMuted,
            )
        }
    }
}

@Composable
private fun NfcOffSlide(modifier: Modifier = Modifier) {
    val colors = Tagsmith.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(76.dp).background(colors.accentTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(TagsmithIcons.NfcOff, null, tint = colors.accent, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(26.dp))
        Text("NFC is switched off", style = TagsmithType.HeroSmall, color = colors.ink)
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Tagsmith can't read or write anything until NFC is on. " +
                "It takes two taps in system settings.",
            style = TagsmithType.Body,
            color = colors.inkMuted,
        )
        Spacer(Modifier.height(26.dp))
        InfoNote("We'll bring you straight back here.")
    }
}

@Composable
private fun NfcReadySlide(modifier: Modifier = Modifier) {
    val colors = Tagsmith.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(76.dp).background(colors.accentTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(TagsmithIcons.Nfc, null, tint = colors.accent, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(26.dp))
        Text("The radio is ready", style = TagsmithType.HeroSmall, color = colors.ink)
        Spacer(Modifier.height(10.dp))
        Text(
            text = "This phone can read and write tags. Hold a card flat against the back " +
                "of the handset, near the top, and keep it still for about a second.",
            style = TagsmithType.Body,
            color = colors.inkMuted,
        )
        Spacer(Modifier.height(26.dp))
        InfoNote("Nothing is written until you ask for it.")
    }
}

/** Optional: the first business, so the first write can already be for someone. */
@Composable
private fun FirstClientSlide(
    name: String,
    onNameChange: (String) -> Unit,
    color: Int,
    onColorChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Tagsmith.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        ClientAvatar(name = name.ifBlank { "?" }, color = androidx.compose.ui.graphics.Color(color), size = 76.dp)
        Spacer(Modifier.height(26.dp))
        Text("Add your first client", style = TagsmithType.HeroSmall, color = colors.ink)
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Every card you write for them lands on their record — so a card that " +
                "left the workshop months ago still answers \"whose is this?\". Optional.",
            style = TagsmithType.Body,
            color = colors.inkMuted,
        )
        Spacer(Modifier.height(22.dp))
        LabeledField(
            label = "Business name",
            value = name,
            onValueChange = onNameChange,
            placeholder = "Oakwell Coffee",
            capitalization = KeyboardCapitalization.Words,
        )
        Spacer(Modifier.height(14.dp))
        ColorSwatches(selected = color, onSelect = onColorChange)
    }
}

@Composable
private fun ProgressTicks(current: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { index ->
            Box(
                Modifier
                    .width(if (index == current) 26.dp else 10.dp)
                    .height(4.dp)
                    .background(if (index == current) Tagsmith.colors.accent else Tagsmith.colors.trackOff)
            )
        }
    }
}

/**
 * The dead end. Drawn on the dark ground because it is a statement, not a form:
 * the app still opens, but the tap will never work here.
 */
@Composable
fun NoNfcHardwareScreen(
    onContinue: () -> Unit,
    deviceName: String = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
) = OnTapGround {
    val colors = Tagsmith.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.ground)
            .systemBarsPadding(),
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("DEVICE CHECK FAILED", style = TagsmithType.KickerLoud, color = colors.accentLight)
            Spacer(Modifier.height(24.dp))
            Text("This phone has no NFC radio", style = TagsmithType.Hero, color = colors.ink)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Tagsmith can't read or write tags here. You can still browse the " +
                    "ledger and your history — reading and writing need an NFC phone.",
                style = TagsmithType.Body,
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(24.dp))
            StrongRule()
            DataRow("Device", deviceName.trim())
            DataRow("NFC adapter", "absent", valueColor = colors.accentLight, divider = false)
        }
        Box(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            OutlineAction(
                label = "Browse in read-only mode",
                onClick = onContinue,
                height = 56.dp,
                borderColor = colors.ink,
                contentColor = colors.ink,
                pressedFill = colors.neutralTint,
            )
        }
    }
}

/**
 * The recoverable version, shown as a full screen when the operator tries to
 * scan with the radio switched off.
 */
@Composable
fun NfcDisabledScreen(
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = Tagsmith.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            BarIcon(TagsmithIcons.Close, "Close", onBack)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(76.dp).background(colors.accentTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(TagsmithIcons.NfcOff, null, tint = colors.accent, modifier = Modifier.size(38.dp))
            }
            Spacer(Modifier.height(26.dp))
            Text("NFC is switched off", style = TagsmithType.HeroSmall, color = colors.ink)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Tagsmith can't read or write anything until NFC is on. " +
                    "It takes two taps in system settings.",
                style = TagsmithType.Body,
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(26.dp))
            InfoNote("We'll bring you straight back here.")
        }
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryAction(
                label = "Open NFC settings",
                onClick = onOpenSettings,
                trailingIcon = TagsmithIcons.ExternalLink,
                height = 56.dp,
            )
            OutlineAction(label = "Not now", onClick = onBack)
        }
    }
}
