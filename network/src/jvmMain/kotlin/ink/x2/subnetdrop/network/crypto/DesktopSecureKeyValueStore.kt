package ink.x2.subnetdrop.network.crypto

import com.github.javakeyring.Keyring
import com.github.javakeyring.KeyringStorageType
import com.github.javakeyring.PasswordAccessException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

class DesktopSecureKeyValueStore internal constructor(
    private val createCredentialStore: () -> DesktopCredentialStore,
) : SecureKeyValueStore {
    constructor() : this({ JavaKeyringCredentialStore(Keyring.create()) })

    override suspend fun read(key: String): ByteArray? = withContext(Dispatchers.IO) {
        createCredentialStore().use { credentials ->
            val current = credentials.read(SERVICE_NAME, key)
            val encoded = current ?: credentials.read(LEGACY_SERVICE_NAME, key)?.also { legacy ->
                credentials.write(SERVICE_NAME, key, legacy)
            }
            encoded?.let(Base64.getDecoder()::decode)
        }
    }

    override suspend fun write(key: String, value: ByteArray) {
        withContext(Dispatchers.IO) {
            createCredentialStore().use { credentials ->
                credentials.write(
                    SERVICE_NAME,
                    key,
                    Base64.getEncoder().encodeToString(value),
                )
            }
        }
    }

    private companion object {
        const val SERVICE_NAME = "ink.x2.subnetdrop"
        const val LEGACY_SERVICE_NAME = "ink.x2.lanchat"
    }
}

internal interface DesktopCredentialStore : AutoCloseable {
    fun read(serviceName: String, key: String): String?
    fun write(serviceName: String, key: String, value: String)
}

private class JavaKeyringCredentialStore(
    private val keyring: Keyring,
) : DesktopCredentialStore {
    override fun read(serviceName: String, key: String): String? = try {
        keyring.getPassword(serviceName, key)
    } catch (error: PasswordAccessException) {
        if (isMissingCredential(keyring.keyringStorageType, error.message)) null else throw error
    }

    override fun write(serviceName: String, key: String, value: String) {
        keyring.setPassword(serviceName, key, value)
    }

    override fun close() {
        keyring.close()
    }
}

internal fun isMissingCredential(storageType: KeyringStorageType, message: String?): Boolean = when (storageType) {
    KeyringStorageType.OSX_KEYCHAIN -> message?.startsWith("No stored credentials match ") == true
    KeyringStorageType.WINDOWS_CREDENTIAL_STORE -> message == "Error code 1168"
    else -> false
}
