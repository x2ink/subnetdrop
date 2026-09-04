package ink.x2.kmp.network.crypto

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
    private val lock = Any()

    override suspend fun read(key: String): ByteArray? = synchronized(lock) {
        val encoded = preferences.getString(key, null) ?: return@synchronized null
        decrypt(android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP))
    }

    override suspend fun write(key: String, value: ByteArray) {
        synchronized(lock) {
            val encoded = android.util.Base64.encodeToString(encrypt(value), android.util.Base64.NO_WRAP)
            check(preferences.edit().putString(key, encoded).commit()) {
                "Unable to persist encrypted key material"
            }
        }
    }

    private fun encrypt(value: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value)
        return ByteBuffer.allocate(Int.SIZE_BYTES + cipher.iv.size + encrypted.size)
            .putInt(cipher.iv.size)
            .put(cipher.iv)
            .put(encrypted)
            .array()
    }

    private fun decrypt(value: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(value)
        val ivSize = buffer.int
        require(ivSize in MIN_IV_SIZE..MAX_IV_SIZE && buffer.remaining() > ivSize) {
            "Encrypted key material is malformed"
        }
        val iv = ByteArray(ivSize).also(buffer::get)
        val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
        return Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            doFinal(encrypted)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
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
        const val PREFERENCES_NAME = "lan_chat_secure_store"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "lan-chat-identity-wrap-key-v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val GCM_TAG_BITS = 128
        const val MIN_IV_SIZE = 12
        const val MAX_IV_SIZE = 32
    }
}
