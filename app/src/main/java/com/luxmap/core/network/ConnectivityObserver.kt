package com.luxmap.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

// Whether the device currently has a validated, internet-capable network (F12/FM-38: drives the
// "offline basemap" banner). This only tells us the DEVICE is online, not whether the map tile
// server itself is reachable — checking that would need an actual request, which plain
// ConnectivityManager can't do — good enough for "hiện banner khi mất mạng" per FM-38's scope.
@Singleton
class ConnectivityObserver
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val isOnline: Flow<Boolean> =
            callbackFlow {
                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            trySend(hasInternetCapability())
                        }

                        override fun onLost(network: Network) {
                            trySend(hasInternetCapability())
                        }

                        override fun onCapabilitiesChanged(
                            network: Network,
                            networkCapabilities: NetworkCapabilities,
                        ) {
                            trySend(hasInternetCapability())
                        }
                    }
                connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
                trySend(hasInternetCapability())
                awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
            }.distinctUntilChanged()

        private fun hasInternetCapability(): Boolean {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    }
