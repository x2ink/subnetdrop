package ink.x2.kmp.domain.model

fun conversationIdFor(firstDeviceId: String, secondDeviceId: String): String =
    listOf(firstDeviceId, secondDeviceId).sorted().joinToString(":")
