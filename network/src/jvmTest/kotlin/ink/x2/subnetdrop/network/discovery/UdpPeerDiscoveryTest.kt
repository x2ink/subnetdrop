package ink.x2.subnetdrop.network.discovery

import ink.x2.subnetdrop.domain.model.Peer
import ink.x2.subnetdrop.domain.model.PeerAvailability
import ink.x2.subnetdrop.domain.model.TrustState
import ink.x2.subnetdrop.domain.port.DiscoveryEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UdpPeerDiscoveryTest {
    @Test
    fun validatesDiscoveryPacketsBeforeCreatingCandidates() {
        val packet = DiscoveryPacket(
            deviceId = "desktop-1",
            displayName = "Desktop",
            servicePort = 45_892,
            replyRequested = true,
        )

        val encoded = DiscoveryPacketCodec.encode(packet)
        assertEquals(packet, DiscoveryPacketCodec.decode(encoded, 0, encoded.size))
        assertNull(DiscoveryPacketCodec.decode("{}".encodeToByteArray(), 0, 2))
    }

    @Test
    fun marksPeerOfflineOnlyAfterConsecutiveProbeFailures() {
        val tracker = PeerLivenessTracker(offlineFailureThreshold = 3)
        val peer = candidatePeer()

        val found = assertIs<DiscoveryEvent.Found>(tracker.record(peer, reachable = true, timestamp = 100L))
        assertEquals(PeerAvailability.ONLINE, found.peer.availability)
        assertEquals(100L, found.peer.lastSeenAt)
        assertNull(tracker.record(peer, reachable = false, timestamp = 101L))
        assertNull(tracker.record(peer, reachable = false, timestamp = 102L))
        assertIs<DiscoveryEvent.Lost>(tracker.record(peer, reachable = false, timestamp = 103L))
        val retryTarget = tracker.probeTargets(timestamp = 5_103L).single()
        assertEquals(PeerAvailability.OFFLINE, retryTarget.availability)
        assertEquals(peer.host, retryTarget.host)
    }

    @Test
    fun rememberedPeerRemainsAProbeTargetAfterInitialFailure() {
        val tracker = PeerLivenessTracker(offlineFailureThreshold = 3)
        val peer = candidatePeer().copy(availability = PeerAvailability.ONLINE, lastSeenAt = 99L)

        tracker.rememberKnown(listOf(peer))

        assertNull(tracker.record(peer, reachable = false, timestamp = 100L))
        assertNull(tracker.record(peer, reachable = false, timestamp = 101L))
        assertNull(tracker.record(peer, reachable = false, timestamp = 102L))
        val retryTarget = tracker.probeTargets(timestamp = 5_102L).single()
        assertEquals(PeerAvailability.OFFLINE, retryTarget.availability)
        assertEquals(99L, retryTarget.lastSeenAt)
    }

    @Test
    fun offlinePeerCanRecoverWithoutAnotherAnnouncement() {
        val tracker = PeerLivenessTracker(offlineFailureThreshold = 3)
        val peer = candidatePeer()
        tracker.rememberKnown(listOf(peer))

        repeat(3) { attempt ->
            tracker.record(peer, reachable = false, timestamp = 100L + attempt)
        }

        val found = assertIs<DiscoveryEvent.Found>(
            tracker.record(tracker.probeTargets(timestamp = 5_102L).single(), reachable = true, timestamp = 5_102L),
        )
        assertEquals(PeerAvailability.ONLINE, found.peer.availability)
        assertEquals(5_102L, found.peer.lastSeenAt)
    }

    @Test
    fun failedUnverifiedEndpointDoesNotDegradeConfirmedEndpoint() {
        val tracker = PeerLivenessTracker(offlineFailureThreshold = 3)
        val peer = candidatePeer()
        assertIs<DiscoveryEvent.Found>(tracker.record(peer, reachable = true, timestamp = 100L))

        repeat(3) {
            assertNull(
                tracker.record(peer.copy(host = "192.168.1.99"), reachable = false, timestamp = 101L + it),
            )
        }

        val confirmed = tracker.probeTargets(timestamp = 5_100L).single()
        assertEquals(PeerAvailability.ONLINE, confirmed.availability)
        assertEquals(peer.host, confirmed.host)
    }

    @Test
    fun displayNameChangeDoesNotHideFailuresOnTheConfirmedNetworkEndpoint() {
        val tracker = PeerLivenessTracker(offlineFailureThreshold = 3)
        val peer = candidatePeer()
        assertIs<DiscoveryEvent.Found>(tracker.record(peer, reachable = true, timestamp = 100L))
        val renamed = peer.copy(displayName = "Renamed phone")

        assertNull(tracker.record(renamed, reachable = false, timestamp = 101L))
        assertNull(tracker.record(renamed, reachable = false, timestamp = 102L))
        assertIs<DiscoveryEvent.Lost>(tracker.record(renamed, reachable = false, timestamp = 103L))
        assertNull(tracker.record(renamed, reachable = false, timestamp = 5_103L))
    }

    @Test
    fun offlineProbeBackoffIsBoundedAndAnnouncementsCanStillProbeImmediately() {
        val tracker = PeerLivenessTracker(
            offlineFailureThreshold = 1,
            onlineProbeIntervalMillis = 5L,
            maxOfflineProbeIntervalMillis = 20L,
        )
        val peer = candidatePeer()
        tracker.rememberKnown(listOf(peer))

        assertEquals(listOf(peer.copy(availability = PeerAvailability.OFFLINE)), tracker.probeTargets(0L))
        assertNull(tracker.record(peer, reachable = false, timestamp = 0L))
        assertEquals(emptyList(), tracker.probeTargets(4L))
        assertEquals(1, tracker.probeTargets(5L).size)

        assertNull(tracker.record(peer, reachable = false, timestamp = 5L))
        assertEquals(emptyList(), tracker.probeTargets(14L))
        assertEquals(1, tracker.probeTargets(15L).size)

        assertNull(tracker.record(peer, reachable = false, timestamp = 15L))
        assertEquals(emptyList(), tracker.probeTargets(34L))
        assertEquals(1, tracker.probeTargets(35L).size)

        val announcedRecovery = assertIs<DiscoveryEvent.Found>(
            tracker.record(peer, reachable = true, timestamp = 16L),
        )
        assertEquals(PeerAvailability.ONLINE, announcedRecovery.peer.availability)
    }

    @Test
    fun recoveryAndEndpointChangesRefreshDatabaseState() {
        val tracker = PeerLivenessTracker(offlineFailureThreshold = 3)
        val peer = candidatePeer()

        assertIs<DiscoveryEvent.Found>(tracker.record(peer, reachable = true, timestamp = 100L))
        assertNull(tracker.record(peer, reachable = true, timestamp = 101L))
        assertNull(tracker.record(peer, reachable = false, timestamp = 102L))
        assertIs<DiscoveryEvent.Found>(tracker.record(peer, reachable = true, timestamp = 103L))
        assertIs<DiscoveryEvent.Found>(
            tracker.record(peer.copy(host = "192.168.1.9"), reachable = true, timestamp = 104L),
        )
    }

    @Test
    fun excludesVpnTunnelAndVirtualInterfacesFromLanDiscovery() {
        val physicalWifi = interfaceCapabilities(name = "wlan0")

        assertTrue(physicalWifi.isUsableForLanDiscovery())
        assertFalse(physicalWifi.copy(name = "tun0", isPointToPoint = true).isUsableForLanDiscovery())
        assertFalse(physicalWifi.copy(name = "tap0").isUsableForLanDiscovery())
        assertFalse(physicalWifi.copy(isVirtual = true).isUsableForLanDiscovery())
        assertFalse(physicalWifi.copy(hasIpv4Address = false).isUsableForLanDiscovery())
        assertFalse(physicalWifi.copy(isUp = false).isUsableForLanDiscovery())
        assertFalse(physicalWifi.copy(isLoopback = true).isUsableForLanDiscovery())
        assertFalse(physicalWifi.copy(supportsMulticast = false).isUsableForLanDiscovery())
    }

    private fun interfaceCapabilities(name: String) = DiscoveryInterfaceCapabilities(
        name = name,
        isUp = true,
        isLoopback = false,
        isPointToPoint = false,
        isVirtual = false,
        supportsMulticast = true,
        hasIpv4Address = true,
    )

    private fun candidatePeer() = Peer(
        id = "phone-1",
        displayName = "Phone",
        host = "192.168.1.8",
        port = 45_892,
        availability = PeerAvailability.OFFLINE,
        trustState = TrustState.UNPAIRED,
        lastSeenAt = 0L,
    )
}
