package ink.x2.subnetdrop.network.identity

import ink.x2.subnetdrop.domain.model.DeviceProfile
import ink.x2.subnetdrop.domain.port.DeviceProfileRepository
import ink.x2.subnetdrop.domain.port.IdGenerator
import ink.x2.subnetdrop.network.crypto.SecureKeyValueStore
import ink.x2.subnetdrop.network.crypto.TinkSecureMessageCodec
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class LocalIdentityServiceTest {
    @Test
    fun updatesNameWithoutRotatingIdentityKeys() {
        runBlocking {
            val repository = MemoryDeviceProfileRepository()
            val service = LocalIdentityService(
                deviceProfileRepository = repository,
                secureMessageCodec = TinkSecureMessageCodec(IdentityMemoryStore()),
                idGenerator = IdGenerator { "device-1" },
                defaultDisplayName = "Original",
            )
            val original = service.get()

            service.updateDisplayName("  Renamed  ")
            val updated = service.get()

            assertEquals("Renamed", repository.profile?.displayName)
            assertEquals(original.deviceId, updated.deviceId)
            assertContentEquals(original.encryptionPublicKey, updated.encryptionPublicKey)
            assertContentEquals(original.signingPublicKey, updated.signingPublicKey)
            assertEquals(updated, service.get())
        }
    }

    @Test
    fun loadsDiscoveryProfileWithoutGeneratingCryptographicKeys() {
        runBlocking {
            val store = IdentityMemoryStore()
            val service = LocalIdentityService(
                deviceProfileRepository = MemoryDeviceProfileRepository(),
                secureMessageCodec = TinkSecureMessageCodec(store),
                idGenerator = IdGenerator { "device-1" },
                defaultDisplayName = "Device",
            )

            val profile = service.getProfile()

            assertEquals("device-1", profile.deviceId)
            assertEquals("Device", profile.displayName)
            assertEquals(0, store.writeCount)
            service.get()
            assertEquals(2, store.writeCount)
        }
    }
}

private class MemoryDeviceProfileRepository : DeviceProfileRepository {
    var profile: DeviceProfile? = null

    override suspend fun getOrCreate(defaultDeviceId: String, defaultDisplayName: String): DeviceProfile {
        return profile ?: DeviceProfile(defaultDeviceId, defaultDisplayName).also { profile = it }
    }

    override suspend fun updateDisplayName(displayName: String) {
        val current = requireNotNull(profile) { "Profile must exist before it can be updated" }
        profile = current.copy(displayName = displayName)
    }
}

private class IdentityMemoryStore : SecureKeyValueStore {
    private val values = mutableMapOf<String, ByteArray>()
    var writeCount: Int = 0
        private set

    override suspend fun read(key: String): ByteArray? = values[key]?.copyOf()

    override suspend fun write(key: String, value: ByteArray) {
        values[key] = value.copyOf()
        writeCount += 1
    }
}
