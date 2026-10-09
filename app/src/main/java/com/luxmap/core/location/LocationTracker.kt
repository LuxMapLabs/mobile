package com.luxmap.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.geometry.LatLng
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

// Fused Location, gets the current location once (not continuous tracking) - enough for F12's
// "locate me" button. Continuous location tracking during night survey (F03/F04) is a
// different job, not part of this task yet.
@Singleton
class LocationTracker
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val client = LocationServices.getFusedLocationProviderClient(context)

        fun hasLocationPermission(): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        // Returns null when there is no permission or the location fetch fails - the caller
        // (ViewModel) decides what to show on null, instead of throwing for a predictable case.
        @SuppressLint("MissingPermission")
        suspend fun getCurrentLocation(): LatLng? {
            if (!hasLocationPermission()) return null

            val cancellationTokenSource = CancellationTokenSource()
            return suspendCancellableCoroutine { continuation ->
                client
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
                    .addOnSuccessListener { location ->
                        continuation.resume(location?.let { LatLng(it.latitude, it.longitude) })
                    }.addOnFailureListener {
                        continuation.resume(null)
                    }
                continuation.invokeOnCancellation { cancellationTokenSource.cancel() }
            }
        }
    }
