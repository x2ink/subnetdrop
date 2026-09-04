package ink.x2.subnetdrop.network.crypto

import com.github.javakeyring.KeyringStorageType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSecureKeyValueStoreTest {
    @Test
    fun `migrates a legacy credential without changing key material`() = runBlocking {
        val credentials = FakeDesktopCredentialStore().apply {
            write("ink.x2.lanchat", "identity.hpke.private", "AQIDBA==")
        }
        val store = DesktopSecureKeyValueStore { credentials }

        val migrated = store.read("identity.hpke.private")

        assertContentEquals(byteArrayOf(1, 2, 3, 4), migrated)
        assertEquals("AQIDBA==", credentials.read("ink.x2.subnetdrop", "identity.hpke.private"))
    }

    @Test
    fun `recognizes missing macOS credential`() {
        assertTrue(
            isMissingCredential(
                KeyringStorageType.OSX_KEYCHAIN,
                "No stored credentials match ink.x2.lanchat account: identity.hpke.private",
            ),
        )
    }

    @Test
    fun `recognizes missing Windows credential`() {
        assertTrue(
            isMissingCredential(
                KeyringStorageType.WINDOWS_CREDENTIAL_STORE,
                "Error code 1168",
            ),
        )
    }

    @Test
    fun `does not hide keychain access failures`() {
        assertFalse(
            isMissingCredential(
                KeyringStorageType.OSX_KEYCHAIN,
                "Failed to get credential. User interaction is not allowed",
            ),
        )
        assertFalse(
            isMissingCredential(
                KeyringStorageType.WINDOWS_CREDENTIAL_STORE,
                "Error code 5",
            ),
        )
    }
}

private class FakeDesktopCredentialStore : DesktopCredentialStore {
    private val values = mutableMapOf<Pair<String, String>, String>()

    override fun read(serviceName: String, key: String): String? = values[serviceName to key]

    override fun write(serviceName: String, key: String, value: String) {
        values[serviceName to key] = value
    }

    override fun close() = Unit
}
