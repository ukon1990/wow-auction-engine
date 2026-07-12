package net.jonasmf.auctionengine.service

import org.springframework.stereotype.Component
import java.sql.Connection
import javax.sql.DataSource

class ProfessionRecipeSyncLock internal constructor(
    internal val connection: Connection,
) {
    fun ensureActive() {
        check(connection.isValid(2)) { "Profession/recipe sync advisory lock connection was lost" }
    }
}

@Component
class ProfessionRecipeSyncGuard(
    private val dataSource: DataSource,
) {
    fun tryAcquire(): ProfessionRecipeSyncLock? {
        val connection = dataSource.connection
        try {
            connection.prepareStatement("SELECT GET_LOCK(?, 0)").use { statement ->
                statement.setString(1, LOCK_NAME)
                statement.executeQuery().use { result ->
                    if (result.next() && result.getInt(1) == 1) {
                        return ProfessionRecipeSyncLock(connection)
                    }
                }
            }
        } catch (error: Throwable) {
            connection.close()
            throw error
        }
        connection.close()
        return null
    }

    fun release(lock: ProfessionRecipeSyncLock) {
        try {
            lock.connection.prepareStatement("SELECT RELEASE_LOCK(?)").use { statement ->
                statement.setString(1, LOCK_NAME)
                statement.executeQuery().close()
            }
        } finally {
            lock.connection.close()
        }
    }
}

private const val LOCK_NAME = "profession-recipe-sync"
