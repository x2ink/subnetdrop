package ink.x2.subnetdrop

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform