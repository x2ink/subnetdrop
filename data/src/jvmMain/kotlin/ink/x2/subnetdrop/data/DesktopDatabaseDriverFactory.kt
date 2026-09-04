package ink.x2.subnetdrop.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ink.x2.subnetdrop.data.db.ChatDatabase
import java.io.File

class DesktopDatabaseDriverFactory(
    databaseFile: File,
) : DatabaseDriverFactory {
    private val file = databaseFile.absoluteFile

    override fun createDriver(): SqlDriver {
        file.parentFile?.mkdirs()
        val isNewDatabase = !file.exists()
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.path}")
        if (isNewDatabase) {
            ChatDatabase.Schema.create(driver)
        }
        return driver
    }
}
