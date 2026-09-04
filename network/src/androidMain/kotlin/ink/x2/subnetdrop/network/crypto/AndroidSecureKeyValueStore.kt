package ink.x2.subnetdrop.network.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidSecureKeyValueStore(
    context: Context,
) : SecureKeyValueStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val legacyPreferences = context.applicationContext.getSharedPreferences(
        LEGACY_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val lock = Any()

    override suspend fun read(key: String): ByteArray? = synchronized(lock) {
        preferences.getString(key, null)?.let { encoded ->
            return@synchronized decrypt(decode(encoded), getOrCreateKey(KEY_ALIAS))
        }
        val legacyEncoded = legacyPreferences.getString(key, null) ?: return@synchronized null
        val legacyKey = requireNotNull(findKey(LEGACY_KEY_ALIAS)) {
            "Legacy identity key is unavailable"
        }
        decrypt(decode(legacyEncoded), legacyKey).also { value -> persist(key, value) }
    }

    override suspend fun write(key: String, value: ByteArray) {
        synchronized(lock) { persist(key, value) }
    }

    private fun persist(key: String, value: ByteArray) {
        val encoded = android.util.Base64.encodeToString(
            encrypt(value, getOrCreateKey(KEY_ALIAS)),
            android.util.Base64.NO_WRAP,
        )
        check(preferences.edit().putString(key, encoded).commit()) {
            "Unable to persist encrypted key material"
        }
    }

    private fun decode(encoded: String): ByteArray =
        android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)

    private fun encrypt(value: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(value)
        return ByteBuffer.allocate(Int.SIZE_BYTES + cipher.iv.size + encrypted.size)
            .putInt(cipher.iv.size)
            .put(cipher.iv)
            .put(encrypted)
            .array()
    }

    private fun decrypt(value: ByteArray, key: SecretKey): ByteArray {
        val buffer = ByteBuffer.wrap(value)
        val ivSize = buffer.int
        require(ivSize in MIN_IV_SIZE..MAX_IV_SIZE && buffer.remaining() > ivSize) {
            "Encrypted key material is malformed"
        }
        val iv = ByteArray(ivSize).also(buffer::get)
        val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
        return Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            doFinal(encrypted)
        }
    }

    private fun findKey(alias: String): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        return keyStore.getKey(alias, null) as? SecretKey
    }

    private fun getOrCreateKey(alias: String): SecretKey {
        findKey(alias)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "subnetdrop_secure_store"
        const val LEGACY_PREFERENCES_NAME = "lan_chat_secure_store"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "subnetdrop-identity-wrap-key-v1"
        const val LEGACY_KEY_ALIAS = "lan-chat-identity-wrap-key-v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val GCM_TAG_BITS = 128
        const val MIN_IV_SIZE = 12
        const val MAX_IV_SIZE = 32
    }
}
