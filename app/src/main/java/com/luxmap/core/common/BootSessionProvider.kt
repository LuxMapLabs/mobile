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

// Reads Settings.Global.BOOT_COUNT, separated out so the "device cannot report it" case (some OEM
// builds omit the setting) can be unit tested without a real ContentResolver (review feedback,
// 2026-10-08). Returns null when the read fails, not a magic sentinel int.
fun interface BootCountReader {
    fun read(): Int?
}

class RealBootCountReader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : BootCountReader {
        override fun read(): Int? =
            try {
                Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
            } catch (error: Settings.SettingNotFoundException) {
                null
            }
    }

// Persists just the (boot count, uuid) pair, separated from RealBootSessionProvider so a fake
// in-memory store can stand in for DataStore in a unit test (review feedback, 2026-10-08).
interface BootSessionStore {
    suspend fun read(): Pair<Int?, String?>

    suspend fun write(
        bootCount: Int,
        uuid: String,
    )
}

class DataStoreBootSessionStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : BootSessionStore {
        override suspend fun read(): Pair<Int?, String?> {
            val prefs = context.bootSessionDataStore.data.first()
            return prefs[KEY_BOOT_COUNT] to prefs[KEY_BOOT_SESSION_ID]
        }

        override suspend fun write(
            bootCount: Int,
            uuid: String,
        ) {
            context.bootSessionDataStore.edit { store ->
                store[KEY_BOOT_COUNT] = bootCount
                store[KEY_BOOT_SESSION_ID] = uuid
            }
        }
    }

// Pure decision logic, shared by RealBootSessionProvider and its test (review feedback,
// 2026-10-08) instead of a hand-duplicated copy in the test file. Returns the existing uuid when
// the boot count did not change, null when a new one must be minted.
//
// currentBootCount == null (BOOT_COUNT unreadable on this device) always returns null, even if a
// saved uuid exists: reusing the same uuid forever on such a device would silently defeat the
// whole point of boot_session_id (a real reboot would then never be detected), which is worse than
// minting a fresh uuid more often than strictly needed.
fun resolveExistingBootSessionId(
    currentBootCount: Int?,
    savedBootCount: Int?,
    savedUuid: String?,
): String? {
    if (currentBootCount == null) return null
    if (savedBootCount != currentBootCount) return null
    return savedUuid
}

// Settings.Global.BOOT_COUNT increments by one on every real device boot (not app restart) — used
// as the signal to end an in-progress sweep and start a new one if the phone reboots mid-session
// (spec: survey-ingest-p2a.md, boot_session_id).
@Singleton
class RealBootSessionProvider
    @Inject
    constructor(
        private val store: BootSessionStore,
        private val bootCountReader: BootCountReader,
    ) : BootSessionProvider {
        override suspend fun currentBootSessionId(): String {
            val currentBootCount = bootCountReader.read()
            val (savedBootCount, savedUuid) = store.read()
            resolveExistingBootSessionId(currentBootCount, savedBootCount, savedUuid)?.let { return it }

            val newUuid = UUID.randomUUID().toString()
            // Nothing meaningful to persist when BOOT_COUNT cannot be read - every future call
            // takes the currentBootCount == null branch above and mints again regardless.
            if (currentBootCount != null) store.write(currentBootCount, newUuid)
            return newUuid
        }
    }
