package ink.x2.subnetdrop.network.identity

import ink.x2.subnetdrop.domain.model.DeviceProfile
import ink.x2.subnetdrop.domain.model.PublicIdentity
import ink.x2.subnetdrop.domain.port.DeviceProfileRepository
import ink.x2.subnetdrop.domain.port.IdGenerator
import ink.x2.subnetdrop.domain.port.SecureMessageCodec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LocalIdentityService(
    private val deviceProfileRepository: DeviceProfileRepository,
    private val secureMessageCodec: SecureMessageCodec,
    private val idGenerator: IdGenerator,
    private val defaultDisplayName: String,
) {
    private val mutex = Mutex()
    private var cachedProfile: DeviceProfile? = null
    private var cachedIdentity: PublicIdentity? = null

    suspend fun getProfile(): DeviceProfile = cachedProfile ?: mutex.withLock {
        cachedProfile ?: createProfile().also { cachedProfile = it }
    }

    suspend fun get(): PublicIdentity = cachedIdentity ?: mutex.withLock {
        cachedIdentity ?: createIdentity(currentProfile()).also { cachedIdentity = it }
    }

    suspend fun updateDisplayName(displayName: String): DeviceProfile = mutex.withLock {
        val normalizedName = displayName.trim()
        require(normalizedName.isNotEmpty()) { "Display name cannot be blank" }
        val updated = currentProfile().copy(displayName = normalizedName)
        deviceProfileRepository.updateDisplayName(normalizedName)
        cachedProfile = updated
        cachedIdentity = cachedIdentity?.let {
            secureMessageCodec.createPublicIdentity(updated.deviceId, updated.displayName)
        }
        updated
    }

    private suspend fun currentProfile(): DeviceProfile = cachedProfile ?: createProfile().also { cachedProfile = it }

    private suspend fun createProfile(): DeviceProfile = deviceProfileRepository.getOrCreate(
        defaultDeviceId = idGenerator.generate(),
        defaultDisplayName = defaultDisplayName,
    )

    private suspend fun createIdentity(profile: DeviceProfile): PublicIdentity = secureMessageCodec.createPublicIdentity(
        deviceId = profile.deviceId,
        displayName = profile.displayName,
    )
}
