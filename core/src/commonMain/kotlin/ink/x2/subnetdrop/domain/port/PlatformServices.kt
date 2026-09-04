package ink.x2.subnetdrop.domain.port

fun interface IdGenerator {
    fun generate(): String
}

fun interface TimestampProvider {
    fun nowMillis(): Long
}
