package com.luxmap.feature.workorder.polegps.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val FIX_INTERVAL_MILLIS = 1000L

@Singleton
class FusedGpsFixSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : GpsFixSource {
        @SuppressLint("MissingPermission")
        override fun fixes(): Flow<GpsFix> =
            callbackFlow {
                val client = LocationServices.getFusedLocationProviderClient(context)
                val request =
                    LocationRequest
                        .Builder(Priority.PRIORITY_HIGH_ACCURACY, FIX_INTERVAL_MILLIS)
                        .setMinUpdateIntervalMillis(FIX_INTERVAL_MILLIS)
                        .build()
                val callback =
                    object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            val location = result.lastLocation ?: return
                            trySend(
                                GpsFix(
                                    lat = location.latitude,
                                    lng = location.longitude,
                                    accuracyM = location.accuracy.toDouble(),
                                    capturedAt = Instant.now(),
                                ),
                            )
                        }
                    }
                client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                awaitClose { client.removeLocationUpdates(callback) }
            }
    }
