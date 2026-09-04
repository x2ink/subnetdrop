package ink.x2.subnetdrop.data

import ink.x2.subnetdrop.data.db.ChatDatabase

class DatabaseFactory(
    private val driverFactory: DatabaseDriverFactory,
) {
    fun create(): ChatDatabase = ChatDatabase(driverFactory.createDriver())
}
