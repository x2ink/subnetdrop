package ink.x2.kmp.data

import ink.x2.kmp.data.db.ChatDatabase

class DatabaseFactory(
    private val driverFactory: DatabaseDriverFactory,
) {
    fun create(): ChatDatabase = ChatDatabase(driverFactory.createDriver())
}
