package com.tagsmith.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tagsmith.core.nfc.PayloadType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeChoice(val label: String) { LIGHT("Light"), DARK("Dark"), SYSTEM("System") }

data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val hapticOnDetect: Boolean = true,
    val hapticOnSuccess: Boolean = true,
    val sounds: Boolean = false,
    val defaultPayloadType: PayloadType = PayloadType.URL,
    val verifyAfterWrite: Boolean = true,
    val lockAfterWrite: Boolean = false,
    val onboardingComplete: Boolean = false,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("tagsmith_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val hapticDetect = booleanPreferencesKey("haptic_detect")
        val hapticSuccess = booleanPreferencesKey("haptic_success")
        val sounds = booleanPreferencesKey("sounds")
        val payloadType = stringPreferencesKey("default_payload")
        val verify = booleanPreferencesKey("verify_after_write")
        val lock = booleanPreferencesKey("lock_after_write")
        val onboarded = booleanPreferencesKey("onboarding_complete")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            theme = prefs[Keys.theme]?.let { runCatching { ThemeChoice.valueOf(it) }.getOrNull() }
                ?: ThemeChoice.SYSTEM,
            hapticOnDetect = prefs[Keys.hapticDetect] ?: true,
            hapticOnSuccess = prefs[Keys.hapticSuccess] ?: true,
            sounds = prefs[Keys.sounds] ?: false,
            defaultPayloadType = prefs[Keys.payloadType]
                ?.let { runCatching { PayloadType.valueOf(it) }.getOrNull() }
                ?: PayloadType.URL,
            verifyAfterWrite = prefs[Keys.verify] ?: true,
            lockAfterWrite = prefs[Keys.lock] ?: false,
            onboardingComplete = prefs[Keys.onboarded] ?: false,
        )
    }

    suspend fun setTheme(value: ThemeChoice) = put(Keys.theme, value.name)
    suspend fun setHapticOnDetect(value: Boolean) = put(Keys.hapticDetect, value)
    suspend fun setHapticOnSuccess(value: Boolean) = put(Keys.hapticSuccess, value)
    suspend fun setSounds(value: Boolean) = put(Keys.sounds, value)
    suspend fun setDefaultPayloadType(value: PayloadType) = put(Keys.payloadType, value.name)
    suspend fun setVerifyAfterWrite(value: Boolean) = put(Keys.verify, value)
    suspend fun setLockAfterWrite(value: Boolean) = put(Keys.lock, value)
    suspend fun setOnboardingComplete(value: Boolean) = put(Keys.onboarded, value)

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}
