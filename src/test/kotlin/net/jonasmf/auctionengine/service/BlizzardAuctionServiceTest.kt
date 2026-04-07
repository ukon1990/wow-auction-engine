package net.jonasmf.auctionengine.service

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.AuctionTimeLeft
import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.dynamodb.AuctionHouseDynamo
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dto.Link
import net.jonasmf.auctionengine.dto.Links
import net.jonasmf.auctionengine.dto.auction.AuctionDTO
import net.jonasmf.auctionengine.dto.auction.AuctionData
import net.jonasmf.auctionengine.dto.auction.AuctionDataResponse
import net.jonasmf.auctionengine.dto.auction.AuctionItemDTO
import net.jonasmf.auctionengine.integration.blizzard.BlizzardApiClientException
import net.jonasmf.auctionengine.integration.blizzard.BlizzardAuctionApiClient
import net.jonasmf.auctionengine.repository.rds.AuctionItemModifierRepository
import net.jonasmf.auctionengine.repository.rds.AuctionItemRepository
import net.jonasmf.auctionengine.repository.rds.AuctionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.ZonedDateTime

class BlizzardAuctionServiceTest {
    private val properties =
        BlizzardApiProperties(
            baseUrl = "https://example.test/",
            tokenUrl = "https://example.test/token",
            clientId = "id",
            clientSecret = "secret",
            regions = listOf(Region.Europe),
        )

    private val blizzardAuctionApiClient = mockk<BlizzardAuctionApiClient>()
    private val authService = mockk<AuthService>(relaxed = true)
    private val amazonS3 = mockk<AmazonS3Service>()
    private val auctionRepository = mockk<AuctionRepository>(relaxed = true)
    private val auctionItemRepository = mockk<AuctionItemRepository>(relaxed = true)
    private val hourlyPriceStatisticsService = mockk<HourlyPriceStatisticsService>()
    private val realmService = mockk<ConnectedRealmService>()
    private val auctionItemModifierRepository = mockk<AuctionItemModifierRepository>(relaxed = true)
    private val updateHistoryService = mockk<ConnectedRealmUpdateHistoryService>(relaxed = true)
    private val auctionHouseService = mockk<AuctionHouseService>()
    private val runtimeHealthTracker = RuntimeHealthTracker(Duration.ofMinutes(20))

    private fun createService() =
        BlizzardAuctionService(
            properties = properties,
            blizzardAuctionApiClient = blizzardAuctionApiClient,
            authService = authService,
            amazonS3 = amazonS3,
            auctionRepository = auctionRepository,
            auctionItemRepository = auctionItemRepository,
            hourlyPriceStatisticsService = hourlyPriceStatisticsService,
            realmService = realmService,
            auctionItemModifierRepository = auctionItemModifierRepository,
            updateHistoryService = updateHistoryService,
            auctionHouseService = auctionHouseService,
            runtimeHealthTracker = runtimeHealthTracker,
        )

    @Test
    fun `updateAuctionHouses processes houses strictly one by one`() {
        val events = mutableListOf<String>()
        val service = createService()
        val firstLastModified = ZonedDateTime.now().minusMinutes(5)
        val secondLastModified = ZonedDateTime.now().minusMinutes(10)
        val firstRealm = createRealm(1, firstLastModified.minusMinutes(1))
        val secondRealm = createRealm(2, secondLastModified.minusMinutes(1))
        val firstData = createAuctionData(101)
        val secondData = createAuctionData(202)

        every { blizzardAuctionApiClient.getLatestAuctionDump(1, Region.Europe, any()) } answers {
            events += "dump-1"
            Mono.just(
                AuctionDataResponse(firstLastModified.toInstant().toEpochMilli(), "url-1", GameBuildVersion.RETAIL),
            )
        }
        every { blizzardAuctionApiClient.getLatestAuctionDump(2, Region.Europe, any()) } answers {
            events += "dump-2"
            Mono.just(
                AuctionDataResponse(secondLastModified.toInstant().toEpochMilli(), "url-2", GameBuildVersion.RETAIL),
            )
        }
        every { realmService.getById(1) } returns firstRealm
        every { realmService.getById(2) } returns secondRealm
        every { blizzardAuctionApiClient.downloadAuctionData("url-1") } answers {
            Mono.fromCallable {
                events += "download-1"
                firstData
            }
        }
        every { blizzardAuctionApiClient.downloadAuctionData("url-2") } answers {
            Mono.fromCallable {
                events += "download-2"
                secondData
            }
        }
        every { amazonS3.uploadFile(eq(Region.Europe), any(), any<AuctionDataResponse>()) } answers {
            val path = secondArg<String>()
            events += if (path.contains("/1/")) "dump-s3-1" else "dump-s3-2"
            "s3://$path"
        }
        every { amazonS3.uploadFile(eq(Region.Europe), any(), any<AuctionData>()) } answers {
            val path = secondArg<String>()
            events += if (path.contains("/1/")) "s3-1" else "s3-2"
            "s3://$path"
        }
        every {
            hourlyPriceStatisticsService.processHourlyPriceStatistics(eq(firstRealm), eq(firstData.auctions), any())
        } answers {
            events += "stats-1"
            HourlyPriceStatisticsSummary(insertedRows = 1, groupedRows = 1)
        }
        every {
            hourlyPriceStatisticsService.processHourlyPriceStatistics(eq(secondRealm), eq(secondData.auctions), any())
        } answers {
            events += "stats-2"
            HourlyPriceStatisticsSummary(insertedRows = 1, groupedRows = 1)
        }
        every { auctionHouseService.updateTimes(eq(1), any(), eq(true), any()) } answers {
            events += "update-1"
        }
        every { auctionHouseService.updateTimes(eq(2), any(), eq(true), any()) } answers {
            events += "update-2"
        }

        service.updateAuctionHouses(
            Region.Europe,
            listOf(
                AuctionHouseDynamo(connectedId = 1, region = Region.Europe),
                AuctionHouseDynamo(connectedId = 2, region = Region.Europe),
            ),
        )

        assertEquals(
            listOf(
                "dump-1",
                "dump-s3-1",
                "download-1",
                "s3-1",
                "stats-1",
                "update-1",
                "dump-2",
                "dump-s3-2",
                "download-2",
                "s3-2",
                "stats-2",
                "update-2",
            ),
            events,
        )
    }

    @Test
    fun `updateAuctionHouses continues to next house after a failure`() {
        val events = mutableListOf<String>()
        val service = createService()
        val firstLastModified = ZonedDateTime.now().minusMinutes(5)
        val secondLastModified = ZonedDateTime.now().minusMinutes(10)
        val firstRealm = createRealm(1, firstLastModified.minusMinutes(1))
        val secondRealm = createRealm(2, secondLastModified.minusMinutes(1))
        val secondData = createAuctionData(202)

        every { blizzardAuctionApiClient.getLatestAuctionDump(1, Region.Europe, any()) } returns
            Mono.just(
                AuctionDataResponse(firstLastModified.toInstant().toEpochMilli(), "url-1", GameBuildVersion.RETAIL),
            )
        every { blizzardAuctionApiClient.getLatestAuctionDump(2, Region.Europe, any()) } answers {
            events += "dump-2"
            Mono.just(
                AuctionDataResponse(secondLastModified.toInstant().toEpochMilli(), "url-2", GameBuildVersion.RETAIL),
            )
        }
        every { realmService.getById(1) } returns firstRealm
        every { realmService.getById(2) } returns secondRealm
        every { amazonS3.uploadFile(eq(Region.Europe), any(), any<AuctionDataResponse>()) } returns "s3://dump"
        every { blizzardAuctionApiClient.downloadAuctionData("url-1") } returns Mono.error(RuntimeException("boom"))
        every { blizzardAuctionApiClient.downloadAuctionData("url-2") } answers {
            Mono.fromCallable {
                events += "download-2"
                secondData
            }
        }
        every { auctionHouseService.updateTimes(eq(1), any(), eq(false), any()) } answers {
            events += "failure-1"
        }
        every { amazonS3.uploadFile(eq(Region.Europe), any(), any<AuctionData>()) } answers {
            events += "s3-2"
            "s3://second"
        }
        every {
            hourlyPriceStatisticsService.processHourlyPriceStatistics(eq(secondRealm), eq(secondData.auctions), any())
        } answers {
            events += "stats-2"
            HourlyPriceStatisticsSummary(insertedRows = 1, groupedRows = 1)
        }
        every { auctionHouseService.updateTimes(eq(2), any(), eq(true), any()) } answers {
            events += "update-2"
        }

        service.updateAuctionHouses(
            Region.Europe,
            listOf(
                AuctionHouseDynamo(connectedId = 1, region = Region.Europe),
                AuctionHouseDynamo(connectedId = 2, region = Region.Europe),
            ),
        )

        assertEquals(listOf("failure-1", "dump-2", "download-2", "s3-2", "stats-2", "update-2"), events)
    }

    @Test
    fun `updateAuctionHouses waits for processing before returning`() {
        val service = createService()
        val lastModified = ZonedDateTime.now().minusMinutes(5)
        val realm = createRealm(1, lastModified.minusMinutes(1))
        val events = mutableListOf<String>()
        val completionMarker = slot<String>()
        val data = createAuctionData(101)

        every { blizzardAuctionApiClient.getLatestAuctionDump(1, Region.Europe, any()) } returns
            Mono.just(AuctionDataResponse(lastModified.toInstant().toEpochMilli(), "url-1", GameBuildVersion.RETAIL))
        every { realmService.getById(1) } returns realm
        every { amazonS3.uploadFile(eq(Region.Europe), any(), any<AuctionDataResponse>()) } returns "s3://dump"
        every { blizzardAuctionApiClient.downloadAuctionData("url-1") } returns Mono.just(data)
        every { amazonS3.uploadFile(eq(Region.Europe), any(), any<AuctionData>()) } returns "s3://first"
        every { hourlyPriceStatisticsService.processHourlyPriceStatistics(eq(realm), eq(data.auctions), any()) } answers
            {
                events += "stats"
                HourlyPriceStatisticsSummary(insertedRows = 1, groupedRows = 1)
            }
        every { auctionHouseService.updateTimes(eq(1), any(), eq(true), capture(completionMarker)) } answers {
            events += "complete"
        }

        service.updateAuctionHouses(Region.Europe, listOf(AuctionHouseDynamo(connectedId = 1, region = Region.Europe)))

        assertEquals(listOf("stats", "complete"), events)
        assertEquals("s3://first", completionMarker.captured)
    }

    @Test
    fun `updateAuctionHouses marks update as failed when auction payload upload does not return a url`() {
        val service = createService()
        val lastModified = ZonedDateTime.now().minusMinutes(5)
        val realm = createRealm(1, lastModified.minusMinutes(1))
        val data = createAuctionData(101)

        every { blizzardAuctionApiClient.getLatestAuctionDump(1, Region.Europe, any()) } returns
            Mono.just(AuctionDataResponse(lastModified.toInstant().toEpochMilli(), "url-1", GameBuildVersion.RETAIL))
        every { realmService.getById(1) } returns realm
        every { amazonS3.uploadFile(eq(Region.Europe), any(), any<AuctionDataResponse>()) } returns "s3://dump"
        every { blizzardAuctionApiClient.downloadAuctionData("url-1") } returns Mono.just(data)
        every { amazonS3.uploadFile(eq(Region.Europe), any(), any<AuctionData>()) } returns null
        every { auctionHouseService.updateTimes(eq(1), any(), eq(false), any()) } returns Unit

        service.updateAuctionHouses(Region.Europe, listOf(AuctionHouseDynamo(connectedId = 1, region = Region.Europe)))

        io.mockk.verify(exactly = 0) {
            hourlyPriceStatisticsService.processHourlyPriceStatistics(any(), any(), any())
        }
        io.mockk.verify(exactly = 1) { auctionHouseService.updateTimes(eq(1), any(), eq(false), any()) }
        io.mockk.verify(exactly = 0) { auctionHouseService.updateTimes(eq(1), any(), eq(true), any()) }
    }

    @Test
    fun `updateAuctionHouses logs known client failures without error stack trace`() {
        val appender = attachAppender()
        val service = createService()
        val lastModified = ZonedDateTime.now().minusMinutes(5)
        val realm = createRealm(1, lastModified.minusMinutes(1))

        every { blizzardAuctionApiClient.getLatestAuctionDump(1, Region.Europe, any()) } returns
            Mono.just(AuctionDataResponse(lastModified.toInstant().toEpochMilli(), "url-1", GameBuildVersion.RETAIL))
        every { realmService.getById(1) } returns realm
        every { amazonS3.uploadFile(eq(Region.Europe), any(), any<AuctionDataResponse>()) } returns "s3://dump"
        every { blizzardAuctionApiClient.downloadAuctionData("url-1") } returns
            Mono.error(
                BlizzardApiClientException(
                    operation = "download auction payload",
                    url = "url-1",
                    summary = "Unexpected end-of-input",
                    exceptionType = "DecodingException",
                    cause = RuntimeException("Unexpected end-of-input"),
                ),
            )
        every { auctionHouseService.updateTimes(eq(1), any(), eq(false), any()) } returns Unit

        service.updateAuctionHouses(Region.Europe, listOf(AuctionHouseDynamo(connectedId = 1, region = Region.Europe)))

        val warnEvent = appender.list.single { it.level == Level.WARN }
        assertTrue(warnEvent.formattedMessage.contains("Failed to process auction data for realm 1"))
        assertTrue(warnEvent.formattedMessage.contains("Unexpected end-of-input"))
        assertNull(warnEvent.throwableProxy)
        assertTrue(appender.list.none { it.level == Level.ERROR })
        detachAppender(appender)
    }

    private fun createRealm(
        id: Int,
        lastModified: ZonedDateTime?,
    ) = ConnectedRealm(
        id = id,
        auctionHouse =
            AuctionHouse(
                lastModified = lastModified,
                lastRequested = null,
                nextUpdate = ZonedDateTime.now(),
                lowestDelay = 60,
                averageDelay = 60,
                highestDelay = 60,
                tsmFile = null,
                statsFile = null,
                auctionFile = null,
                failedAttempts = 0,
            ),
        realms = mutableListOf(),
    )

    private fun createAuctionData(itemId: Int) =
        AuctionData(
            _links = Links(Link("self-$itemId")),
            auctions =
                listOf(
                    AuctionDTO(
                        id = itemId.toLong(),
                        item = AuctionItemDTO(id = itemId),
                        quantity = 1,
                        unit_price = 100,
                        buyout = 100,
                        time_left = AuctionTimeLeft.LONG,
                    ),
                ),
        )

    private fun attachAppender(): ListAppender<ILoggingEvent> {
        val logger = LoggerFactory.getLogger(BlizzardAuctionService::class.java) as Logger
        return ListAppender<ILoggingEvent>().also {
            it.start()
            logger.addAppender(it)
        }
    }

    private fun detachAppender(appender: ListAppender<ILoggingEvent>) {
        val logger = LoggerFactory.getLogger(BlizzardAuctionService::class.java) as Logger
        logger.detachAppender(appender)
        appender.stop()
    }
}
