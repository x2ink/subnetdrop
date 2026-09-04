package ink.x2.subnetdrop.data

import app.cash.sqldelight.db.SqlDriver

fun interface DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
