package ink.x2.subnetdrop.network.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import ink.x2.subnetdrop.domain.model.Peer
import ink.x2.subnetdrop.domain.port.DiscoveryEvent
import ink.x2.subnetdrop.domain.port.PeerDiscovery
import ink.x2.subnetdrop.domain.port.PeerReachabilityProbe
import ink.x2.subnetdrop.domain.port.TimestampProvider
import kotlinx.coroutines.flow.Flow
import java.net.Inet4Address

class AndroidPeerDiscovery(
    context: Context,
    timestampProvider: TimestampProvider,
    reachabilityProbe: PeerReachabilityProbe,
) : PeerDiscovery {
    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
    private val connectivityManager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private var multicastLock: WifiManager.MulticastLock? = null
    private var processNetworkBinding: ProcessNetworkBinding? = null
    private val delegate = UdpPeerDiscovery(
        timestampProvider = timestampProvider,
        reachabilityProbe = reachabilityProbe,
        acquireMulticast = ::acquireMulticastLock,
        releaseMulticast = ::releaseMulticastLock,
    )

    override val events: Flow<DiscoveryEvent> = delegate.events

    override suspend fun start(
        localDeviceId: String,
        displayName: String,
        servicePort: Int,
        knownPeers: List<Peer>,
    ) {
        bindProcessToWifiIfAvailable()
        try {
            delegate.start(localDeviceId, displayName, servicePort, knownPeers)
        } catch (exception: Exception) {
            restorePreviousProcessNetwork()
            throw exception
        }
    }

    override suspend fun stop() {
        try {
            delegate.stop()
        } finally {
            restorePreviousProcessNetwork()
        }
    }

    override suspend fun refresh() {
        delegate.refresh()
    }

    override suspend fun forget(peerId: String) {
        delegate.forget(peerId)
    }

    private fun bindProcessToWifiIfAvailable() {
        if (processNetworkBinding != null) return
        processNetworkBinding = runCatching {
            val wifiNetwork = findIpv4WifiNetwork() ?: return@runCatching null
            val previousNetwork = connectivityManager.boundNetworkForProcess
            if (previousNetwork == wifiNetwork) return@runCatching null
            if (connectivityManager.bindProcessToNetwork(wifiNetwork)) {
                ProcessNetworkBinding(previousNetwork)
            } else {
                null
            }
        }.getOrNull()
    }

    // Discovery sockets must be bound before startup; an asynchronous NetworkCallback can arrive too late.
    @Suppress("DEPRECATION")
    private fun findIpv4WifiNetwork(): Network? =
        connectivityManager.allNetworks.firstOrNull(::isIpv4WifiNetwork)

    private fun isIpv4WifiNetwork(network: Network): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return false
        return connectivityManager.getLinkProperties(network)
            ?.linkAddresses
            ?.any { it.address is Inet4Address } == true
    }

    private fun restorePreviousProcessNetwork() {
        val binding = processNetworkBinding ?: return
        processNetworkBinding = null
        val restored = runCatching {
            connectivityManager.bindProcessToNetwork(binding.previousNetwork)
        }.getOrDefault(false)
        if (!restored) {
            runCatching { connectivityManager.bindProcessToNetwork(null) }
        }
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        multicastLock = wifiManager.createMulticastLock(MULTICAST_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseMulticastLock() {
        multicastLock?.takeIf(WifiManager.MulticastLock::isHeld)?.release()
        multicastLock = null
    }

    private companion object {
        const val MULTICAST_LOCK_TAG = "subnetdrop-discovery"
    }

    private data class ProcessNetworkBinding(
        val previousNetwork: Network?,
    )
}
