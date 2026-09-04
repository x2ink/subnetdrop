package ink.x2.subnetdrop.network.identity

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
    private var cachedIdentity: PublicIdentity? = null

    suspend fun get(): PublicIdentity = cachedIdentity ?: mutex.withLock {
        cachedIdentity ?: createIdentity().also { cachedIdentity = it }
    }

    suspend fun updateDisplayName(displayName: String): PublicIdentity = mutex.withLock {
        val normalizedName = displayName.trim()
        require(normalizedName.isNotEmpty()) { "Display name cannot be blank" }
        val current = cachedIdentity ?: createIdentity()
        deviceProfileRepository.updateDisplayName(normalizedName)
        secureMessageCodec.createPublicIdentity(
            deviceId = current.deviceId,
            displayName = normalizedName,
        ).also { cachedIdentity = it }
    }

    private suspend fun createIdentity(): PublicIdentity = deviceProfileRepository
        .getOrCreate(
            defaultDeviceId = idGenerator.generate(),
            defaultDisplayName = defaultDisplayName,
        )
        .let { profile ->
            secureMessageCodec.createPublicIdentity(
                deviceId = profile.deviceId,
                displayName = profile.displayName,
            )
        }
}
