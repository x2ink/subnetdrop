package ink.x2.kmp.data

import app.cash.sqldelight.db.SqlDriver

fun interface DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
