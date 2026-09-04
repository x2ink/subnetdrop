package ink.x2.subnetdrop.network.discovery

import ink.x2.subnetdrop.domain.model.Peer
import ink.x2.subnetdrop.domain.model.PeerAvailability
import ink.x2.subnetdrop.domain.model.TrustState
import ink.x2.subnetdrop.domain.port.DiscoveryEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

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
        assertEquals(emptyList(), tracker.peers())
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
