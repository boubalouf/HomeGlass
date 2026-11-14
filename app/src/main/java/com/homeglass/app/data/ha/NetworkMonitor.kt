package com.homeglass.app.data.ha

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Surveille le réseau pour deux décisions :
 * - sur Wi-Fi/Ethernet, on suppose être sur le réseau de la maison et on essaie l'URL interne ;
 * - tout changement de réseau réévalue l'URL de base et relance la liaison temps réel.
 */
class NetworkMonitor(context: Context) {

    private val connectivity: ConnectivityManager? =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val changes: Flow<Unit> = _changes

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _changes.tryEmit(Unit)
        }

        override fun onLost(network: Network) {
            _changes.tryEmit(Unit)
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            _changes.tryEmit(Unit)
        }
    }

    init {
        runCatching { connectivity?.registerDefaultNetworkCallback(callback) }
    }

    /** Vrai si l'appareil est sur un réseau local (Wi-Fi ou Ethernet). */
    fun isOnLocalNetwork(): Boolean {
        val manager = connectivity ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun close() {
        runCatching { connectivity?.unregisterNetworkCallback(callback) }
    }
}
