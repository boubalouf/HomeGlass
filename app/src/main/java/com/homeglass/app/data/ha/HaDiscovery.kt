package com.homeglass.app.data.ha

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Instance Home Assistant repérée en mDNS. */
data class DiscoveredInstance(
    val name: String,
    val baseUrl: String,
    val internalUrl: String?,
    val externalUrl: String?,
    val version: String?,
    val locationName: String?,
    val uuid: String?,
)

/**
 * Découverte mDNS de `_home-assistant._tcp`.
 *
 * Les TXT records donnent les URL internes/externes et le nom de l'installation. Android
 * n'autorise qu'une résolution à la fois par `NsdManager` : les services trouvés sont donc
 * mis en file et résolus **séquentiellement** par un seul consommateur. La découverte est
 * arrêtée à la fin du flux (timeout ou fermeture de l'écran) et le verrou multicast est
 * relâché ; sans lui, certains Wi-Fi filtrent le trafic mDNS.
 */
class HaDiscovery(private val context: Context) {

    fun discover(timeoutMillis: Long = DISCOVERY_TIMEOUT_MILLIS): Flow<List<DiscoveredInstance>> = callbackFlow {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            ?: run {
                trySend(emptyList())
                awaitClose { }
                return@callbackFlow
            }

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = runCatching {
            wifiManager?.createMulticastLock(MULTICAST_LOCK_TAG)?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()

        val found = mutableListOf<DiscoveredInstance>()
        val queue = Channel<NsdServiceInfo>(Channel.UNLIMITED)

        val resolver = launch {
            for (info in queue) {
                val resolved = resolve(nsdManager, info) ?: continue
                if (found.none { it.uuid == resolved.uuid && it.baseUrl == resolved.baseUrl }) {
                    found += resolved
                    trySend(found.toList())
                }
            }
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close()
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.startsWith(SERVICE_TYPE)) {
                    queue.trySend(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
        }

        val started = runCatching {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }.isSuccess
        if (!started) {
            // Un scan précédent peut encore être en cours d'arrêt : on rend la main proprement.
            close()
        }

        val timeout = launch {
            delay(timeoutMillis)
            close()
        }

        awaitClose {
            timeout.cancel()
            resolver.cancel()
            queue.close()
            runCatching { nsdManager.stopServiceDiscovery(listener) }
            runCatching { multicastLock?.release() }
        }
    }

    private suspend fun resolve(nsdManager: NsdManager, info: NsdServiceInfo): DiscoveredInstance? =
        suspendCancellableCoroutine { continuation ->
            nsdManager.resolveService(
                info,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        if (continuation.isActive) continuation.resume(toInstance(serviceInfo))
                    }
                },
            )
        }

    private fun toInstance(info: NsdServiceInfo): DiscoveredInstance? {
        val attributes = info.attributes.orEmpty().mapValues { (_, value) ->
            runCatching { String(value, Charsets.UTF_8) }.getOrDefault("")
        }
        val host = info.host?.hostAddress?.substringBefore('%') ?: attributes["host"] ?: return null
        val formattedHost = if (host.contains(':')) "[$host]" else host
        val inferred = "http://$formattedHost:${info.port}"

        val internalUrl = attributes["internal_url"]?.takeIf { it.isNotBlank() }
        val externalUrl = attributes["external_url"]?.takeIf { it.isNotBlank() }
        val baseUrl = attributes["base_url"]?.takeIf { it.isNotBlank() } ?: internalUrl ?: inferred

        return DiscoveredInstance(
            name = info.serviceName,
            baseUrl = baseUrl.trimEnd('/'),
            internalUrl = internalUrl?.trimEnd('/') ?: inferred,
            externalUrl = externalUrl?.trimEnd('/'),
            version = attributes["version"]?.takeIf { it.isNotBlank() },
            locationName = attributes["location_name"]?.takeIf { it.isNotBlank() },
            uuid = attributes["uuid"]?.takeIf { it.isNotBlank() },
        )
    }

    private companion object {
        const val SERVICE_TYPE = "_home-assistant._tcp"
        const val DISCOVERY_TIMEOUT_MILLIS = 6_000L
        const val MULTICAST_LOCK_TAG = "homeglass-mdns"
    }
}
