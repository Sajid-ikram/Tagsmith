package com.tagsmith.ui.batch

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tagsmith.AppContainer
import com.tagsmith.core.data.Batch
import com.tagsmith.core.data.BatchStatus
import com.tagsmith.core.data.Client
import com.tagsmith.core.data.HistoryEntry
import com.tagsmith.core.data.payload
import com.tagsmith.core.nfc.NfcAvailability
import com.tagsmith.core.nfc.NfcEvent
import com.tagsmith.core.nfc.NfcFailure
import com.tagsmith.core.nfc.NfcPhase
import com.tagsmith.core.nfc.TagOperation
import com.tagsmith.core.nfc.WriteContext
import com.tagsmith.ui.components.OutlineAction
import com.tagsmith.ui.components.PrimaryAction
import com.tagsmith.ui.components.PulsingRings
import com.tagsmith.ui.components.TagsmithIcons
import com.tagsmith.ui.components.TextAction
import com.tagsmith.ui.containerViewModel
import com.tagsmith.ui.theme.DarkOnWarn
import com.tagsmith.ui.theme.OnTapGround
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BatchRunViewModel(private val container: AppContainer, private val batchId: Long) : ViewModel() {
    private val session = container.nfc

    val batch: StateFlow<Batch?> = container.batches.batch(batchId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val writes: StateFlow<List<HistoryEntry>> = container.batches.writes(batchId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val client: StateFlow<Client?> = combine(batch.filterNotNull(), container.clients.all()) { b, all ->
        all.firstOrNull { it.id == b.clientId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The last attempt that did not count, and which card it was for. */
    var failure by mutableStateOf<Pair<Int, NfcFailure>?>(null)
        private set

    private var armed = false

    init {
        // Results arrive as events: in continuous mode the phase goes straight
        // back to waiting, so there is no Done state to watch.
        viewModelScope.launch {
            session.events.collect { event ->
                when (event) {
                    is NfcEvent.Completed -> if (event.result.operation.batchId() == batchId) failure = null

                    // A failure never moves the count, so the card it was for is the next one.
                    is NfcEvent.Failed -> if (event.operation.batchId() == batchId) {
                        val slot = (batch.value?.writtenCount ?: 0) + 1
                        failure = slot to event.failure
                    }
                }
            }
        }
        // Keep the session's duplicate guard current as cards land.
        viewModelScope.launch {
            writes.collect { if (armed) session.retarget(operation() ?: return@collect) }
        }
    }

    private fun TagOperation.batchId(): Long? = (this as? TagOperation.Write)?.context?.batchId

    private fun operation(): TagOperation.Write? {
        val b = batch.value ?: return null
        val payload = b.payload() ?: return null
        return TagOperation.Write(
            payload = payload,
            verify = b.autoVerify,
            lockAfter = b.autoLock,
            context = WriteContext(clientId = b.clientId, templateId = b.templateId, batchId = b.id),
            alreadyWritten = writes.value.mapIndexed { index, entry -> entry.uid to index + 1 }.toMap(),
        )
    }

    /** Opens the batch for writing, resuming it if it was paused. */
    fun enter() = viewModelScope.launch {
        val b = batch.filterNotNull().first()
        if (!b.status.isOpen) return@launch
        if (b.status == BatchStatus.PAUSED) container.batches.resume(batchId)
        // Wait for the resumed row so the operation carries the right state.
        batch.filterNotNull().first { it.status == BatchStatus.RUNNING }
        arm()
    }

    private fun arm() {
        val op = operation() ?: return
        armed = true
        session.arm(op, continuous = true)
    }

    fun rearmIfNeeded() {
        if (batch.value?.status == BatchStatus.RUNNING) arm()
    }

    fun pause() {
        armed = false
        session.cancel()
        viewModelScope.launch { container.batches.pause(batchId) }
    }

    fun resume() = viewModelScope.launch {
        container.batches.resume(batchId)
        batch.filterNotNull().first { it.status == BatchStatus.RUNNING }
        arm()
    }

    fun finish() {
        armed = false
        session.cancel()
        viewModelScope.launch { container.batches.finish(batchId) }
    }

    fun dismissFailure() {
        failure = null
    }

    /** Leaving the screen pauses — a batch only runs while you're watching it. */
    fun leave() {
        if (!armed) return
        armed = false
        session.cancel()
        // Not viewModelScope: this must survive the screen being torn down.
        container.pauseBatchInBackground(batchId)
    }

    val phase get() = session.phase
    val availability get() = session.availability
}

/**
 * The batch, running. A huge counter, a grid of cards filling in, and a tap
 * prompt that never goes away — twenty cards without leaving the screen.
 */
@Composable
fun BatchRunScreen(
    batchId: Long,
    onComplete: () -> Unit,
    onLeave: () -> Unit,
) = OnTapGround {
    val viewModel: BatchRunViewModel = containerViewModel(key = "batch-run-$batchId") { BatchRunViewModel(it, batchId) }
    val batch by viewModel.batch.collectAsStateWithLifecycle()
    val client by viewModel.client.collectAsStateWithLifecycle()
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val availability by viewModel.availability.collectAsStateWithLifecycle()
    val colors = Tagsmith.colors

    DisposableEffect(Unit) {
        viewModel.enter()
        onDispose { viewModel.leave() }
    }
    // Switching NFC back on mid-batch picks up where it left off.
    LaunchedEffect(availability) {
        if (availability == NfcAvailability.READY) viewModel.rearmIfNeeded()
    }

    val current = batch
    LaunchedEffect(current?.status) {
        if (current != null && !current.status.isOpen) onComplete()
    }
    BackHandler {
        viewModel.pause()
        onLeave()
    }

    if (current == null) {
        Box(Modifier.fillMaxSize().background(colors.ground))
        return@OnTapGround
    }

    val paused = current.status == BatchStatus.PAUSED
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(paused) {
        while (!paused) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val failure = viewModel.failure
    val engaged = phase.isEngaged

    // The newest card pops when the count actually moves — read from the batch
    // row, which the ledger updates in the same transaction as the write.
    var lastCount by remember(batchId) { mutableIntStateOf(current.writtenCount) }
    var popped by remember(batchId) { mutableIntStateOf(0) }
    LaunchedEffect(current.writtenCount) {
        if (current.writtenCount > lastCount) popped = current.writtenCount
        lastCount = current.writtenCount
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                listOfNotNull("BATCH ${current.code}", client?.name?.uppercase()).joinToString(" · "),
                style = TagsmithType.KickerLoud,
                color = colors.accentLight,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            TextAction(
                if (paused) "Resume" else "Pause",
                onClick = { if (paused) viewModel.resume() else viewModel.pause() },
                color = colors.inkMuted,
            )
        }

        // All three on one baseline: the count, "/ 20", and the meta at the right.
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp)) {
            Text(
                current.writtenCount.toString().padStart(2, '0'),
                style = TagsmithType.Counter,
                color = colors.ink,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "/ ${current.targetCount}",
                style = TagsmithType.HeroSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                color = colors.inkFaint,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.weight(1f))
            Text(
                listOfNotNull(
                    current.failedCount.takeIf { it > 0 }?.let { "$it failed" },
                    formatDuration(current.elapsedMillis(now)),
                ).joinToString(" · "),
                style = TagsmithType.RowMeta,
                color = colors.inkFaint,
                modifier = Modifier.alignByBaseline(),
            )
        }

        CardGrid(
            target = current.targetCount,
            written = current.writtenCount,
            failedCurrent = failure != null && failure.second !is NfcFailure.AlreadyInBatch,
            active = !paused,
            poppedSlot = popped,
            modifier = Modifier.weight(1f),
        )

        if (failure != null) {
            FailureStrip(
                slot = failure.first,
                failure = failure.second,
                onRetry = viewModel::dismissFailure,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp),
            )
        }

        TapDock(
            paused = paused,
            engaged = engaged,
            nextCard = current.writtenCount + 1,
            autoVerify = current.autoVerify,
            autoLock = current.autoLock,
            availability = availability,
            onResume = { viewModel.resume() },
            onFinish = { viewModel.finish() },
        )
    }
}

@Composable
private fun CardGrid(
    target: Int,
    written: Int,
    failedCurrent: Boolean,
    active: Boolean,
    poppedSlot: Int,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyGridState()
    // Keep the card being written in view on long batches.
    LaunchedEffect(written) {
        if (written > 10) state.animateScrollToItem((written - 5).coerceAtLeast(0))
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        state = state,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(target, key = { it }) { index ->
            val slot = index + 1
            CardChip(
                slot = slot,
                state = when {
                    slot <= written -> ChipState.WRITTEN
                    slot == written + 1 && failedCurrent -> ChipState.FAILED
                    slot == written + 1 && active -> ChipState.CURRENT
                    else -> ChipState.PENDING
                },
                pop = slot == poppedSlot,
            )
        }
    }
}

private enum class ChipState { WRITTEN, FAILED, CURRENT, PENDING }

@Composable
private fun CardChip(slot: Int, state: ChipState, pop: Boolean) {
    val colors = Tagsmith.colors
    val label = slot.toString().padStart(2, '0')
    // Each landing card pops, once — the micro-moment that goes with the haptic.
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pop, state) {
        if (pop && state == ChipState.WRITTEN) {
            scale.snapTo(1.3f)
            scale.animateTo(1f, tween(320, easing = EaseOutBack))
        }
    }
    val blink by rememberInfiniteTransition(label = "current").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "currentAlpha",
    )
    val base = Modifier.height(38.dp).fillMaxWidth().scale(scale.value)
    val chip = when (state) {
        ChipState.WRITTEN -> base.background(colors.accent)
        ChipState.FAILED -> base.border(2.dp, colors.danger, RectangleShape)
        ChipState.CURRENT -> base.border(2.dp, colors.ink.copy(alpha = blink), RectangleShape)
        ChipState.PENDING -> base.drawBehind {
            drawRect(
                color = colors.border,
                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
            )
        }
    }
    Box(chip, contentAlignment = Alignment.Center) {
        Text(
            label,
            style = TagsmithType.DataSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            color = when (state) {
                ChipState.WRITTEN -> Color.White
                ChipState.FAILED -> colors.danger
                ChipState.CURRENT -> colors.ink
                ChipState.PENDING -> colors.border
            },
        )
    }
}

@Composable
private fun FailureStrip(slot: Int, failure: NfcFailure, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Tagsmith.colors
    val repeat = failure is NfcFailure.AlreadyInBatch
    val edge = if (repeat) colors.accentLight else colors.danger
    Row(
        modifier.fillMaxWidth().background(colors.accentTint),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(56.dp).background(edge))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                if (repeat) failure.headline else "Card ${slot.toString().padStart(2, '0')} failed — ${shortReason(failure)}",
                style = TagsmithType.RowTitleSmall.copy(fontSize = TagsmithType.ChipSelected.fontSize),
                color = edge,
            )
            Text(
                if (repeat) "not written again · not counted" else "not counted",
                style = TagsmithType.DataTiny,
                color = DarkOnWarn,
            )
        }
        TextAction(if (repeat) "OK" else "RETRY", onClick = onRetry, color = edge)
    }
}

private fun shortReason(failure: NfcFailure): String = when (failure) {
    NfcFailure.TagLost -> "tag moved too soon"
    NfcFailure.Locked -> "tag is locked"
    NfcFailure.Unformatted -> "tag isn't formatted"
    is NfcFailure.TooSmall -> "tag too small (${failure.capacity} bytes)"
    is NfcFailure.Unsupported -> "unsupported tag"
    is NfcFailure.VerificationMismatch -> "read back wrong"
    is NfcFailure.AlreadyInBatch -> "already written"
    is NfcFailure.Io -> "couldn't write"
}

/** The persistent prompt at the bottom: small rings, the next card's number. */
@Composable
private fun TapDock(
    paused: Boolean,
    engaged: Boolean,
    nextCard: Int,
    autoVerify: Boolean,
    autoLock: Boolean,
    availability: NfcAvailability,
    onResume: () -> Unit,
    onFinish: () -> Unit,
) {
    val colors = Tagsmith.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.groundSubtle),
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(colors.accent))
        if (paused) {
            Column(
                Modifier.navigationBarsPadding().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Paused", style = TagsmithType.ButtonLoud.copy(fontSize = TagsmithType.HeroSmall.fontSize), color = colors.ink)
                Text("The radio is off. Nothing will be written until you resume.", style = TagsmithType.BodyTiny, color = DarkOnWarn)
                PrimaryAction(label = "Resume batch", onClick = onResume, trailingIcon = TagsmithIcons.Play, height = 56.dp)
                OutlineAction(
                    label = "End batch here",
                    onClick = onFinish,
                    borderColor = colors.border,
                    contentColor = colors.ink,
                    pressedFill = colors.neutralTint,
                )
            }
        } else {
            Row(
                Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 26.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                    if (availability == NfcAvailability.READY) {
                        PulsingRings(diameter = 76.dp, ringCount = 2, periodMillis = 2400)
                    }
                    Icon(TagsmithIcons.Nfc, null, tint = colors.ink, modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.width(18.dp))
                Column {
                    Text(
                        when {
                            availability != NfcAvailability.READY -> "Switch NFC on"
                            engaged -> "Writing card ${nextCard.toString().padStart(2, '0')}…"
                            else -> "Tap card ${nextCard.toString().padStart(2, '0')}"
                        },
                        style = TagsmithType.ButtonLoud.copy(fontSize = 20.sp),
                        color = colors.ink,
                    )
                    Text(
                        "Auto-verify ${if (autoVerify) "on" else "off"} · auto-lock ${if (autoLock) "on" else "off"}",
                        style = TagsmithType.BodyTiny,
                        color = DarkOnWarn,
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }
    }
}

/** `4m 12s`, or `1h 03m` for the long ones. */
fun formatDuration(millis: Long): String {
    val seconds = millis / 1000
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "${h}h ${m.toString().padStart(2, '0')}m" else "${m}m ${s.toString().padStart(2, '0')}s"
}
