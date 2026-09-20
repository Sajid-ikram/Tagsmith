package com.tagsmith

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tagsmith.core.nfc.NfcAvailability
import com.tagsmith.core.settings.ThemeChoice
import com.tagsmith.ui.LocalAppContainer
import com.tagsmith.ui.nav.TagsmithNavHost
import com.tagsmith.ui.theme.TagsmithTheme
import kotlinx.coroutines.launch

/**
 * The single activity. It owns two things the rest of the app cannot: the NFC
 * reader mode, which needs a resumed Activity, and the incoming share intent.
 */
class MainActivity : ComponentActivity() {

    private val container: AppContainer by lazy { (application as TagsmithApplication).container }
    private var adapter: NfcAdapter? = null

    private val readerCallback = NfcAdapter.ReaderCallback { tag ->
        container.nfc.onTagDiscovered(tag)
    }

    /** The system tells us when the radio is switched on or off underneath us. */
    private val adapterStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == NfcAdapter.ACTION_ADAPTER_STATE_CHANGED) {
                publishAvailability()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        adapter = NfcAdapter.getDefaultAdapter(this)
        publishAvailability()

        var sharedUrl by mutableStateOf(extractSharedText(intent))

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    container.nfc.armed.collect { armed ->
                        if (armed) enableReaderMode() else disableReaderMode()
                    }
                } finally {
                    disableReaderMode()
                }
            }
        }

        setContent {
            val settings by container.settings.settings
                .collectAsStateWithLifecycle(initialValue = com.tagsmith.core.settings.AppSettings())

            val dark = when (settings.theme) {
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK -> true
                ThemeChoice.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            CompositionLocalProvider(LocalAppContainer provides container) {
                TagsmithTheme(darkTheme = dark) {
                    TagsmithNavHost(
                        startAtOnboarding = !settings.onboardingComplete,
                        versionName = versionName(),
                        sharedUrl = sharedUrl,
                        onOnboardingComplete = {
                            lifecycleScope.launch { container.settings.setOnboardingComplete(true) }
                        },
                        onSharedUrlConsumed = { sharedUrl = null },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        publishAvailability()
        ContextCompat.registerReceiver(
            this,
            adapterStateReceiver,
            IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(adapterStateReceiver) }
    }

    private fun publishAvailability() {
        val hasHardware = packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)
        val current = adapter
        container.nfc.setAvailability(
            when {
                current == null || !hasHardware -> NfcAvailability.ABSENT
                !current.isEnabled -> NfcAvailability.DISABLED
                else -> NfcAvailability.READY
            }
        )
    }

    /**
     * Reader mode, not foreground dispatch: it suppresses the platform's own
     * NDEF handling, so tapping a URL tag inside Tagsmith never bounces the
     * operator out into a browser.
     */
    private fun enableReaderMode() {
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        runCatching { adapter?.enableReaderMode(this, readerCallback, flags, null) }
    }

    private fun disableReaderMode() {
        runCatching { adapter?.disableReaderMode(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun extractSharedText(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        return intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun versionName(): String = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName
        }
    }.getOrNull() ?: "1.0"
}
