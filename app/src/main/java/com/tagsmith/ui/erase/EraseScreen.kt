package com.tagsmith.ui.erase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagsmith.core.nfc.NfcPhase
import com.tagsmith.core.nfc.TagOperation
import com.tagsmith.core.nfc.TagSnapshot
import com.tagsmith.ui.LocalAppContainer
import com.tagsmith.ui.components.DataRow
import com.tagsmith.ui.components.OutlineAction
import com.tagsmith.ui.components.PrimaryAction
import com.tagsmith.ui.components.StrongRule
import com.tagsmith.ui.components.TagsmithIcons
import com.tagsmith.ui.theme.OnTapGround
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType
import com.tagsmith.ui.write.TapPromptScreen
import com.tagsmith.ui.write.WriteFailureScreen
import com.tagsmith.ui.write.WriteSuccessScreen

/**
 * Erase writes a single empty NDEF record. Reversible — the tag stays formatted
 * and writable — so this confirms once and then asks for the tap.
 */
@Composable
fun EraseScreen(
    snapshot: TagSnapshot?,
    onCancel: () -> Unit,
    onErased: () -> Unit,
) {
    val session = LocalAppContainer.current.nfc
    val phase by session.phase.collectAsStateWithLifecycle()
    val current = phase.takeIf { it.forOperation == TagOperation.Erase } ?: NfcPhase.Idle

    when (current) {
        is NfcPhase.Done -> WriteSuccessScreen(
            value = "Nothing — the tag is empty",
            chipLabel = current.result.snapshot.chipLabel,
            uid = current.result.snapshot.uid,
            verified = null,
            locked = current.result.snapshot.locked,
            onWriteAnother = {
                session.cancel()
                onErased()
            },
            onLock = {},
            onDone = {
                session.cancel()
                onErased()
            },
        )

        is NfcPhase.Failed -> WriteFailureScreen(
            failure = current.failure,
            expected = "an empty NDEF message",
            onRetry = { session.retry() },
            onEdit = {
                session.cancel()
                onCancel()
            },
        )

        is NfcPhase.Waiting, is NfcPhase.Detected, is NfcPhase.Working -> TapPromptScreen(
            title = "Tap the tag to erase it",
            detail = "The tag stays writable",
            value = snapshot?.summary ?: "the tag's current contents",
            detected = current.isEngaged,
            onCancel = { session.cancel() },
        )

        NfcPhase.Idle -> EraseWarning(
            snapshot = snapshot,
            onCancel = onCancel,
            onConfirm = { session.arm(TagOperation.Erase) },
        )
    }
}

@Composable
private fun EraseWarning(
    snapshot: TagSnapshot?,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) = OnTapGround {
    val colors = Tagsmith.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.ground)
            .systemBarsPadding(),
    ) {
        Column(Modifier.weight(1f).padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center) {
            Box(
                Modifier.size(76.dp).background(colors.accentTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(TagsmithIcons.Trash, null, tint = colors.accentLight, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text("Erase this tag", style = TagsmithType.Hero, color = colors.ink)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "An empty NDEF message is written over the contents. The tag stays " +
                    "formatted and writable, so you can put something else on it afterwards.",
                style = TagsmithType.Body,
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(24.dp))
            StrongRule(color = colors.rule)
            DataRow("Tag", snapshot?.chipLabel ?: "read on tap", divider = false)
            DataRow("UID", snapshot?.uid ?: "read on tap", divider = false)
            DataRow("Contents now", snapshot?.summary ?: "read on tap", divider = false)
            StrongRule(color = colors.rule)
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryAction(label = "Erase it", onClick = onConfirm, height = 56.dp)
            OutlineAction(
                label = "Cancel",
                onClick = onCancel,
                borderColor = colors.border,
                contentColor = colors.ink,
                pressedFill = colors.neutralTint,
            )
        }
    }
}
