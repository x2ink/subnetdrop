package ink.x2.subnetdrop.data

import ink.x2.subnetdrop.data.db.ChatDatabase
import ink.x2.subnetdrop.domain.model.DeviceProfile
import ink.x2.subnetdrop.domain.model.PublicIdentity
import ink.x2.subnetdrop.domain.port.DeviceProfileRepository
import ink.x2.subnetdrop.domain.port.TrustedIdentityRepository

class SqlDelightDeviceProfileRepository(
    database: ChatDatabase,
) : DeviceProfileRepository {
    private val queries = database.chatQueries

    override suspend fun getOrCreate(
        defaultDeviceId: String,
        defaultDisplayName: String,
    ): DeviceProfile {
        queries.insertDeviceProfile(defaultDeviceId, defaultDisplayName)
        return queries.selectDeviceProfile(::DeviceProfile).executeAsOne()
    }

    override suspend fun updateDisplayName(displayName: String) {
        val normalizedName = displayName.trim()
        require(normalizedName.isNotEmpty()) { "Display name cannot be blank" }
        require(normalizedName.length <= MAX_DISPLAY_NAME_LENGTH) { "Display name is too long" }
        queries.updateDeviceDisplayName(normalizedName)
    }

    private companion object {
        const val MAX_DISPLAY_NAME_LENGTH = 64
    }
}

class SqlDelightTrustedIdentityRepository(
    database: ChatDatabase,
) : TrustedIdentityRepository {
    private val queries = database.chatQueries

    override suspend fun find(peerId: String): PublicIdentity? = queries
        .findTrustedIdentity(peerId, ::PublicIdentity)
        .executeAsOneOrNull()

    override suspend fun save(identity: PublicIdentity, verifiedAt: Long) {
        queries.transaction {
            queries.saveTrustedIdentity(
                peer_id = identity.deviceId,
                encryption_public_key = identity.encryptionPublicKey,
                signing_public_key = identity.signingPublicKey,
                verified_at = verifiedAt,
            )
            queries.markPeerTrusted(identity.deviceId)
        }
    }
}
