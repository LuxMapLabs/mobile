package com.luxmap.core.ble

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.luxDeviceDataStore: DataStore<Preferences> by preferencesDataStore(name = "lux_device")

// Remembers the last BLE lux sensor picked in Bước 0, so a Field Engineer using the same sensor
// every day does not have to scan and pick again on every survey. Not sensitive data, so plain
// DataStore is enough - unlike TokenStore, no encryption is needed here.
@Singleton
class LuxDevicePreferences
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend fun lastDevice(): LuxDevice? {
            val prefs = context.luxDeviceDataStore.data.first()
            val address = prefs[ADDRESS_KEY] ?: return null
            return LuxDevice(name = prefs[NAME_KEY], address = address)
        }

        suspend fun saveLastDevice(device: LuxDevice) {
            context.luxDeviceDataStore.edit { prefs ->
                prefs[ADDRESS_KEY] = device.address
                if (device.name != null) {
                    prefs[NAME_KEY] = device.name
                } else {
                    prefs.remove(NAME_KEY)
                }
            }
        }

        private companion object {
            val ADDRESS_KEY = stringPreferencesKey("address")
            val NAME_KEY = stringPreferencesKey("name")
        }
    }
