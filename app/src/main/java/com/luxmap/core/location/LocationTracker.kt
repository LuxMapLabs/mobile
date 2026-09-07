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

// Fused Location, lấy vị trí hiện tại một lần (không theo dõi liên tục) — đủ cho nút "định vị
// về vị trí hiện tại" của F12. Theo dõi vị trí liên tục khi khảo sát đêm (F03/F04) là việc
// khác, chưa tới lượt trong task này.
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

        // Trả về null nếu chưa có quyền hoặc không lấy được vị trí — người gọi (ViewModel) tự
        // quyết định hiển thị gì khi null, không throw exception cho một luồng có thể đoán trước.
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
