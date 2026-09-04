package ink.x2.kmp.network.identity

import ink.x2.kmp.domain.model.DeviceProfile
import ink.x2.kmp.domain.port.DeviceProfileRepository
import ink.x2.kmp.domain.port.IdGenerator
import ink.x2.kmp.network.crypto.SecureKeyValueStore
import ink.x2.kmp.network.crypto.TinkSecureMessageCodec
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

            val updated = service.updateDisplayName("  Renamed  ")

            assertEquals("Renamed", repository.profile?.displayName)
            assertEquals(original.deviceId, updated.deviceId)
            assertContentEquals(original.encryptionPublicKey, updated.encryptionPublicKey)
            assertContentEquals(original.signingPublicKey, updated.signingPublicKey)
            assertEquals(updated, service.get())
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

    override suspend fun read(key: String): ByteArray? = values[key]?.copyOf()

    override suspend fun write(key: String, value: ByteArray) {
        values[key] = value.copyOf()
    }
}
