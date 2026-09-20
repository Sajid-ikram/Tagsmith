package com.tagsmith

import android.app.Application
import android.content.Context
import com.tagsmith.core.data.LedgerRepository
import com.tagsmith.core.data.TagsmithDatabase
import com.tagsmith.core.feedback.SystemFeedback
import com.tagsmith.core.nfc.NfcEvent
import com.tagsmith.core.nfc.NfcSession
import com.tagsmith.core.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Hand-wired dependencies. One operator, one phone, five collaborators —
 * a DI framework would cost more than it saves here.
 */
class AppContainer(context: Context) {

    private val applicationScope = CoroutineScope(SupervisorJob())

    private val database = TagsmithDatabase.build(context)

    val settings = SettingsRepository(context)
    val feedback = SystemFeedback(context)
    val ledger = LedgerRepository(database.tags(), database.history())
    val nfc = NfcSession(applicationScope, feedback)

    init {
        // Every completed tap lands in the ledger, wherever it was started from.
        nfc.events
            .onEach { event ->
                when (event) {
                    is NfcEvent.Completed -> ledger.record(event.result)
                    is NfcEvent.Failed -> ledger.recordFailure(
                        event.operation,
                        event.failure,
                        nfc.lastSnapshot.value?.takeIf { it.uid == event.uid },
                    )
                }
            }
            .launchIn(applicationScope)

        // Feedback settings are read on a binder thread, so mirror them into fields.
        applicationScope.launch {
            settings.settings.collect { current ->
                feedback.hapticOnDetect = current.hapticOnDetect
                feedback.hapticOnSuccess = current.hapticOnSuccess
                feedback.soundsEnabled = current.sounds
            }
        }
    }
}

class TagsmithApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as TagsmithApplication).container
