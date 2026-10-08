package com.luxmap.core.common

import android.content.Context
import android.provider.Settings
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.bootSessionDataStore by preferencesDataStore(name = "boot_session")
private val KEY_BOOT_COUNT = intPreferencesKey("last_boot_count")
private val KEY_BOOT_SESSION_ID = stringPreferencesKey("boot_session_id")

interface BootSessionProvider {
    suspend fun currentBootSessionId(): String
}

// Settings.Global.BOOT_COUNT increments by one on every real device boot (not app restart) — used
// as the signal to end an in-progress sweep and start a new one if the phone reboots mid-session
// (spec: survey-ingest-p2a.md, boot_session_id). SettingNotFoundException is caught, not propagated:
// some OEM builds are known to omit this setting, and the capture flow must not crash over it.
@Singleton
class RealBootSessionProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : BootSessionProvider {
        override suspend fun currentBootSessionId(): String {
            val currentBootCount =
                try {
                    Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
                } catch (error: Settings.SettingNotFoundException) {
                    -1
                }
            val prefs = context.bootSessionDataStore.data.first()
            val savedBootCount = prefs[KEY_BOOT_COUNT]
            val savedUuid = prefs[KEY_BOOT_SESSION_ID]
            if (savedBootCount == currentBootCount && savedUuid != null) return savedUuid

            val newUuid = UUID.randomUUID().toString()
            context.bootSessionDataStore.edit { store ->
                store[KEY_BOOT_COUNT] = currentBootCount
                store[KEY_BOOT_SESSION_ID] = newUuid
            }
            return newUuid
        }
    }
