package ink.x2.kmp.network.crypto

interface SecureKeyValueStore {
    suspend fun read(key: String): ByteArray?

    suspend fun write(key: String, value: ByteArray)
}
