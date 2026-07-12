package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.config.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import javax.sql.DataSource

class ProfessionRecipeSyncGuardIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var dataSource: DataSource

    @Test
    fun `holds MariaDB advisory lock until owning sync releases it`() {
        val firstReplica = ProfessionRecipeSyncGuard(dataSource)
        val secondReplica = ProfessionRecipeSyncGuard(dataSource)
        val firstLock = firstReplica.tryAcquire()

        assertThat(firstLock).isNotNull
        assertThat(secondReplica.tryAcquire()).isNull()

        firstReplica.release(requireNotNull(firstLock))

        val secondLock = secondReplica.tryAcquire()
        assertThat(secondLock).isNotNull
        secondReplica.release(requireNotNull(secondLock))
    }
}
