package ink.x2.kmp.network.crypto

import com.github.javakeyring.Keyring
import com.github.javakeyring.KeyringStorageType
import com.github.javakeyring.PasswordAccessException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

class DesktopSecureKeyValueStore : SecureKeyValueStore {
    override suspend fun read(key: String): ByteArray? = withContext(Dispatchers.IO) {
        Keyring.create().use { keyring ->
            readEncodedValue(keyring, key)?.let(Base64.getDecoder()::decode)
        }
    }

    override suspend fun write(key: String, value: ByteArray) {
        withContext(Dispatchers.IO) {
            Keyring.create().use { keyring ->
                keyring.setPassword(
                    SERVICE_NAME,
                    key,
                    Base64.getEncoder().encodeToString(value),
                )
            }
        }
    }

    private companion object {
        const val SERVICE_NAME = "ink.x2.lanchat"

        fun readEncodedValue(keyring: Keyring, key: String): String? = try {
            keyring.getPassword(SERVICE_NAME, key)
        } catch (error: PasswordAccessException) {
            if (isMissingCredential(keyring.keyringStorageType, error.message)) {
                null
            } else {
                throw error
            }
        }
    }
}

internal fun isMissingCredential(storageType: KeyringStorageType, message: String?): Boolean = when (storageType) {
    KeyringStorageType.OSX_KEYCHAIN -> message?.startsWith("No stored credentials match ") == true
    KeyringStorageType.WINDOWS_CREDENTIAL_STORE -> message == "Error code 1168"
    else -> false
}
