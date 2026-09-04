package ink.x2.kmp.domain.port

import ink.x2.kmp.domain.model.DeviceProfile
import ink.x2.kmp.domain.model.PublicIdentity

interface DeviceProfileRepository {
    suspend fun getOrCreate(defaultDeviceId: String, defaultDisplayName: String): DeviceProfile

    suspend fun updateDisplayName(displayName: String)
}

interface TrustedIdentityRepository {
    suspend fun find(peerId: String): PublicIdentity?

    suspend fun save(identity: PublicIdentity, verifiedAt: Long)
}
