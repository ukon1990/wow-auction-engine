package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.repository.rds.ConnectedRealmRepository
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ConnectedRealmUpdateHistoryServiceTest : IntegrationTestBase() {
    @Autowired
    lateinit var connectedRealmRepository: ConnectedRealmRepository

    @Autowired
    lateinit var connectedRealmUpdateHistoryService: ConnectedRealmUpdateHistoryService

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `setUpdateToCompleted updates completed timestamp via modifying query`() {
        val connectedRealm = createConnectedRealm(1401)
        val lastModified = ZonedDateTime.ofInstant(Instant.parse("2026-04-15T06:44:17Z"), ZoneOffset.UTC)
        val history =
            connectedRealmUpdateHistoryService.startUpdate(
                connectedRealm = connectedRealm,
                auctionCount = 34944,
                lastModified = lastModified,
            )

        val updated = connectedRealmUpdateHistoryService.setUpdateToCompleted(connectedRealm.id, lastModified)

        assertTrue(updated)
        val completedTimestamp =
            jdbcTemplate.queryForObject(
                "SELECT completed_timestamp FROM connected_realm_update_history WHERE id = ?",
                { rs, _ -> rs.getTimestamp(1) },
                history.id,
            )

        assertNotNull(completedTimestamp)
    }

    private fun createConnectedRealm(id: Int): ConnectedRealm =
        connectedRealmRepository.save(
            ConnectedRealm(
                id = id,
                auctionHouse =
                    AuctionHouse(
                        connectedId = id,
                        region = Region.Europe,
                        lastModified = null,
                        lastRequested = null,
                        nextUpdate = Instant.EPOCH,
                        lowestDelay = 60,
                        avgDelay = 60,
                        highestDelay = 60,
                        tsmFile = null,
                        statsFile = null,
                        auctionFile = null,
                        updateAttempts = 0,
                    ),
                realms = mutableListOf(),
            ),
        )
}
