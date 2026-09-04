package ink.x2.kmp.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import ink.x2.kmp.data.db.ChatDatabase

class AndroidDatabaseDriverFactory(
    private val context: Context,
) : DatabaseDriverFactory {
    override fun createDriver(): SqlDriver = AndroidSqliteDriver(
        schema = ChatDatabase.Schema,
        context = context,
        name = DATABASE_NAME,
    )

    private companion object {
        const val DATABASE_NAME = "lan-chat.db"
    }
}
