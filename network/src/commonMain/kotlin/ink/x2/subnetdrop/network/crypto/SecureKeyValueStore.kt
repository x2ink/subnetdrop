package ink.x2.subnetdrop.network.crypto

interface SecureKeyValueStore {
    suspend fun read(key: String): ByteArray?

    suspend fun write(key: String, value: ByteArray)
}
