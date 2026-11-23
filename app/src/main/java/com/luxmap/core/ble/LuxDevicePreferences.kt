package com.luxmap.core.ble

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.luxDeviceDataStore: DataStore<Preferences> by preferencesDataStore(name = "lux_device")

@Singleton
class LuxDevicePreferences
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        val pairedDevice: Flow<LuxDevice?> =
            context.luxDeviceDataStore.data.map { prefs ->
                prefs[ADDRESS_KEY]?.let { address -> LuxDevice(name = prefs[NAME_KEY], address = address) }
            }

        suspend fun save(device: LuxDevice) {
            context.luxDeviceDataStore.edit { prefs ->
                prefs[ADDRESS_KEY] = device.address
                if (device.name != null) prefs[NAME_KEY] = device.name else prefs.remove(NAME_KEY)
            }
        }

        suspend fun clear() {
            context.luxDeviceDataStore.edit { prefs ->
                prefs.remove(ADDRESS_KEY)
                prefs.remove(NAME_KEY)
            }
        }

        private companion object {
            val ADDRESS_KEY = stringPreferencesKey("address")
            val NAME_KEY = stringPreferencesKey("name")
        }
    }
