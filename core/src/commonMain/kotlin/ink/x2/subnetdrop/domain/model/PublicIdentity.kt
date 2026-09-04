package ink.x2.subnetdrop.domain.model

data class PublicIdentity(
    val deviceId: String,
    val displayName: String,
    val encryptionPublicKey: ByteArray,
    val signingPublicKey: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PublicIdentity) return false

        return deviceId == other.deviceId &&
            displayName == other.displayName &&
            encryptionPublicKey.contentEquals(other.encryptionPublicKey) &&
            signingPublicKey.contentEquals(other.signingPublicKey)
    }

    override fun hashCode(): Int {
        var result = deviceId.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + encryptionPublicKey.contentHashCode()
        result = 31 * result + signingPublicKey.contentHashCode()
        return result
    }
}
