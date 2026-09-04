package ink.x2.subnetdrop.domain.model

fun conversationIdFor(firstDeviceId: String, secondDeviceId: String): String =
    listOf(firstDeviceId, secondDeviceId).sorted().joinToString(":")
