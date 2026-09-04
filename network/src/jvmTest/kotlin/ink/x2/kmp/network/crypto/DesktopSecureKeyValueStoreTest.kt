package ink.x2.kmp.network.crypto

import com.github.javakeyring.KeyringStorageType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSecureKeyValueStoreTest {
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
