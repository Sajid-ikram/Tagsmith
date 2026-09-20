package com.tagsmith.ui.lock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagsmith.core.nfc.NfcPhase
import com.tagsmith.core.nfc.TagOperation
import com.tagsmith.core.nfc.TagSnapshot
import com.tagsmith.ui.LocalAppContainer
import com.tagsmith.ui.components.PrimaryAction
import com.tagsmith.ui.components.StrongRule
import com.tagsmith.ui.components.TagsmithIcons
import com.tagsmith.ui.theme.LockAccent
import com.tagsmith.ui.theme.LockGround
import com.tagsmith.ui.theme.LockLabel
import com.tagsmith.ui.theme.LockOn
import com.tagsmith.ui.theme.LockOnFaint
import com.tagsmith.ui.theme.LockOnMuted
import com.tagsmith.ui.theme.LockRule
import com.tagsmith.ui.theme.LockTrackBorder
import com.tagsmith.ui.theme.LockTrackFill
import com.tagsmith.ui.theme.TagsmithType
import com.tagsmith.ui.write.TapPromptScreen
import com.tagsmith.ui.write.WriteFailureScreen
import com.tagsmith.ui.write.WriteSuccessScreen
import kotlinx.coroutines.launch

private const val HOLD_MILLIS = 2000

/**
 * Deliberately heavy. Not a dialog — a screen, on its own ground, where Cancel
 * is the prominent option and the destructive one has to be held down.
 */
@Composable
fun LockConfirmScreen(
    snapshot: TagSnapshot?,
    onCancel: () -> Unit,
    onLocked: () -> Unit,
) {
    val container = LocalAppContainer.current
    val session = container.nfc
    val phase by session.phase.collectAsStateWithLifecycle()

    // Only a lock draws here; a write still in the session keeps its own screen.
    val current = phase.takeIf { it.forOperation == TagOperation.Lock } ?: NfcPhase.Idle

    when (current) {
        is NfcPhase.Done -> WriteSuccessScreen(
            value = current.result.snapshot.summary,
            chipLabel = current.result.snapshot.chipLabel,
            uid = current.result.snapshot.uid,
            verified = null,
            locked = true,
            onWriteAnother = {
                session.cancel()
                onLocked()
            },
            onLock = {},
            onDone = {
                session.cancel()
                onLocked()
            },
        )

        is NfcPhase.Failed -> WriteFailureScreen(
            failure = current.failure,
            expected = snapshot?.summary.orEmpty(),
            onRetry = { session.retry() },
            onEdit = {
                session.cancel()
                onCancel()
            },
        )

        is NfcPhase.Waiting, is NfcPhase.Detected, is NfcPhase.Working -> TapPromptScreen(
            title = "Tap the tag to lock it",
            detail = "This cannot be undone",
            value = snapshot?.summary ?: "the tag's current contents",
            detected = current.isEngaged,
            onCancel = { session.cancel() },
        )

        NfcPhase.Idle -> LockWarning(
            snapshot = snapshot,
            onCancel = onCancel,
            onConfirm = { session.arm(TagOperation.Lock) },
        )
    }
}

@Composable
private fun LockWarning(
    snapshot: TagSnapshot?,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(LockGround)
            .systemBarsPadding(),
    ) {
        Box(Modifier.padding(start = 24.dp, top = 16.dp)) {
            Box(
                Modifier.size(64.dp).border(3.dp, LockAccent, RectangleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    TagsmithIcons.Warning,
                    contentDescription = null,
                    tint = LockAccent,
                    modifier = Modifier.size(34.dp),
                )
            }
        }

        Column(
            Modifier.padding(horizontal = 24.dp).padding(top = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Locking is permanent", style = TagsmithType.Hero, color = LockOn)
            Text(
                text = "Once locked, this tag can never be rewritten, erased or reformatted " +
                    "by anyone. There is no undo, not even with another app.",
                style = TagsmithType.Body,
                color = LockOnMuted,
            )
        }

        Column(Modifier.padding(horizontal = 24.dp).padding(top = 22.dp)) {
            StrongRule(color = LockRule)
            Column(
                Modifier.padding(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LockDetail("Tag", snapshot?.chipLabel ?: "the tag you tap next")
                LockDetail("UID", snapshot?.uid ?: "read on tap")
                LockDetail("Contents", snapshot?.summary ?: "read on tap")
            }
            StrongRule(color = LockRule)
        }

        Spacer(Modifier.weight(1f))

        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Cancel is the prominent option, on purpose.
            PrimaryAction(
                label = "Cancel — keep it writable",
                onClick = onCancel,
                height = 60.dp,
                background = LockOn,
                contentColor = LockGround,
            )
            HoldToLock(onConfirm = onConfirm)
        }
    }
}

@Composable
private fun LockDetail(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = TagsmithType.BodySmall, color = LockOnFaint)
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            style = TagsmithType.Data,
            color = LockOn,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** Hold for two seconds. A tap does nothing; that is the whole point. */
@Composable
private fun HoldToLock(onConfirm: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var holding by remember { mutableStateOf(false) }
    var fired by remember { mutableStateOf(false) }

    LaunchedEffect(holding) {
        if (holding && !fired) {
            progress.animateTo(1f, tween(HOLD_MILLIS, easing = { it }))
            if (progress.value >= 1f) {
                fired = true
                onConfirm()
            }
        } else if (!holding) {
            progress.animateTo(0f, tween(220))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .border(2.dp, LockTrackBorder, RectangleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            holding = true
                            tryAwaitRelease()
                            holding = false
                        },
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.value)
                    .background(LockTrackFill)
            )
            Text(
                text = "Hold to lock permanently",
                style = TagsmithType.ButtonSmall,
                color = LockLabel,
                modifier = Modifier.padding(start = 18.dp),
            )
        }
        Text(
            text = if (holding) "Keep holding…" else "Press and hold for two seconds",
            style = TagsmithType.RowMeta,
            color = LockOnFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
