package ink.x2.subnetdrop.data

import app.cash.sqldelight.db.QueryResult
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
        val url = "jdbc:sqlite:${file.path}"
        if (file.exists()) {
            val existingDriver = JdbcSqliteDriver(url)
            if (existingDriver.isLegacyUnversionedDatabase()) {
                ChatDatabase.Schema.migrate(
                    driver = existingDriver,
                    oldVersion = LEGACY_SCHEMA_VERSION,
                    newVersion = ChatDatabase.Schema.version,
                ).value
                existingDriver.execute(
                    identifier = null,
                    sql = "PRAGMA user_version = ${ChatDatabase.Schema.version}",
                    parameters = 0,
                ).value
                return existingDriver
            }
            existingDriver.close()
        }
        return JdbcSqliteDriver(url = url, schema = ChatDatabase.Schema)
    }

    private fun JdbcSqliteDriver.isLegacyUnversionedDatabase(): Boolean {
        val version = executeQuery(
            identifier = null,
            sql = "PRAGMA user_version",
            mapper = { cursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
            },
            parameters = 0,
        ).value
        if (version != 0L) return false
        return executeQuery(
            identifier = null,
            sql = "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'conversationEntity' LIMIT 1",
            mapper = { cursor -> QueryResult.Value(cursor.next().value) },
            parameters = 0,
        ).value
    }

    private companion object {
        const val LEGACY_SCHEMA_VERSION = 1L
    }
}
