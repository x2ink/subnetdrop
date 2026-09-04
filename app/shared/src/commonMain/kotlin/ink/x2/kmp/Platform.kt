package ink.x2.kmp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform