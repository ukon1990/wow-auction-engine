package net.jonasmf.auctionengine.integration.blizzard

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.testsupport.loadFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeoutException
import kotlin.io.path.deleteIfExists

class BlizzardAuctionApiClientTest {
    private val filesToDelete = mutableListOf<Path>()

    @AfterEach
    fun cleanupFiles() {
        filesToDelete.forEach { it.deleteIfExists() }
        filesToDelete.clear()
    }

    @Test
    fun `getLatestAuctionDump builds connected realm uri with regional namespace and header`() {
        var capturedRequest: ClientRequest? = null
        val webClient =
            webClient { request ->
                capturedRequest = request
                response(auctionDumpMetadataBody(), 1_700_000_000_000)
            }

        val client = BlizzardAuctionApiClient(createSupport(webClient))

        val result = client.getLatestAuctionDump(123, Region.Europe, GameBuildVersion.CLASSIC).block()!!

        assertEquals(
            "https://eu.api.blizzard.test/data/wow/connected-realm/123/auctions/index?namespace=dynamic-eu&locale=en_US",
            result.url,
        )
        assertEquals(
            "Sat, 14 Mar 3000 20:07:10 GMT",
            capturedRequest!!.headers()[HttpHeaders.IF_MODIFIED_SINCE]?.single(),
        )
    }

    @Test
    fun `getLatestAuctionDump builds commodity uri for region`() {
        var capturedRequest: ClientRequest? = null
        val webClient =
            webClient { request ->
                capturedRequest = request
                response(auctionDumpMetadataBody(), 1_700_000_000_000)
            }

        val client = BlizzardAuctionApiClient(createSupport(webClient))

        val result = client.getLatestAuctionDump(-1, Region.NorthAmerica).block()!!

        assertEquals(
            "https://us.api.blizzard.test/data/wow/auctions/commodities?namespace=dynamic-us&locale=en_US",
            result.url,
        )
        assertEquals(result.url, capturedRequest!!.url().toString())
    }

    @Test
    fun `downloadAuctionData streams auction fixture to a temp file`() {
        var capturedRequest: ClientRequest? = null
        val webClient =
            webClient { request ->
                capturedRequest = request
                response(auctionDataBody())
            }

        val client = BlizzardAuctionApiClient(createSupport(webClient))

        val result =
            client
                .downloadAuctionData(
                    "https://eu.api.blizzard.test/data/wow/connected-realm/123/auctions",
                ).block()!!
        filesToDelete.add(result.path)
        val fileContents = Files.readString(result.path)

        assertEquals(
            "https://eu.api.blizzard.test/data/wow/connected-realm/123/auctions",
            capturedRequest!!.url().toString(),
        )
        assertTrue(Files.exists(result.path))
        assertTrue(fileContents.contains("\"auctions\""))
        assertTrue(fileContents.contains("19019"))
        assertTrue(fileContents.contains("pet_species_id"))
    }

    @Test
    fun `downloadAuctionData streams malformed json without decoding it`() {
        val client =
            BlizzardAuctionApiClient(
                createSupport(
                    webClient {
                        response("""{"auctions":[{"id":1""")
                    },
                ),
            )

        val result =
            client
                .downloadAuctionData(
                    "https://eu.api.blizzard.test/data/wow/connected-realm/123/auctions",
                ).block()!!
        filesToDelete.add(result.path)

        assertTrue(Files.exists(result.path))
        assertEquals("""{"auctions":[{"id":1""", Files.readString(result.path))
    }

    @Test
    fun `downloadAuctionData propagates non-2xx responses as web client exceptions`() {
        val appender = attachAppender()
        val webClient =
            webClient {
                Mono.just(
                    ClientResponse
                        .create(HttpStatus.BAD_GATEWAY)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("""{"error":"upstream"}""")
                        .build(),
                )
            }

        val client = BlizzardAuctionApiClient(createSupport(webClient))

        val error =
            assertThrows(BlizzardApiClientException::class.java) {
                client.downloadAuctionData("https://eu.api.blizzard.test/data/wow/connected-realm/123/auctions").block()
            }

        assertEquals("download auction payload", error.operation)
        assertEquals("502 Bad Gateway", error.summary)
        val errorEvent = appender.list.single { it.level == Level.ERROR }
        assertTrue(errorEvent.formattedMessage.contains("Blizzard API download auction payload failed"))
        assertTrue(errorEvent.formattedMessage.contains("502 Bad Gateway"))
        assertNull(errorEvent.throwableProxy)
        detachAppender(appender)
    }

    @Test
    fun `downloadAuctionData logs concise timeout failure without stack trace`() {
        val appender = attachAppender()
        val client =
            BlizzardAuctionApiClient(
                createSupport(
                    webClient {
                        Mono.error(TimeoutException("simulated upstream timeout"))
                    },
                ),
            )

        val error =
            assertThrows(BlizzardApiClientException::class.java) {
                client.downloadAuctionData("https://eu.api.blizzard.test/data/wow/connected-realm/123/auctions").block()
            }

        assertEquals("request timed out after 180s", error.summary)
        val errorEvent = appender.list.single { it.level == Level.ERROR }
        assertTrue(errorEvent.formattedMessage.contains("TimeoutException"))
        assertTrue(errorEvent.formattedMessage.contains("request timed out after 180s"))
        assertNull(errorEvent.throwableProxy)
        detachAppender(appender)
    }

    private fun createSupport(webClient: WebClient) =
        BlizzardApiSupport(
            properties =
                BlizzardApiProperties(
                    baseUrl = "api.blizzard.test/data/wow/",
                    tokenUrl = "https://oauth.blizzard.test/token",
                    clientId = "id",
                    clientSecret = "secret",
                    regions = listOf(Region.Europe),
                ),
            webClientWithAuth = webClient,
        )

    private fun webClient(handler: (ClientRequest) -> Mono<ClientResponse>): WebClient =
        WebClient
            .builder()
            .exchangeFunction(ExchangeFunction(handler))
            .build()

    private fun response(
        body: String,
        lastModified: Long? = null,
    ): Mono<ClientResponse> =
        Mono.just(
            ClientResponse
                .create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .apply {
                    if (lastModified != null) {
                        header(HttpHeaders.LAST_MODIFIED, lastModified.toString())
                    }
                }.body(body)
                .build(),
        )

    private fun auctionDataBody(): String = loadFixture(this, "/blizzard/auction/auction-data-response.json")

    private fun auctionDumpMetadataBody(): String =
        loadFixture(this, "/blizzard/auction/auction-dump-metadata-response.json")

    private fun attachAppender(): ListAppender<ILoggingEvent> {
        val logger = LoggerFactory.getLogger(BlizzardAuctionApiClient::class.java) as Logger
        return ListAppender<ILoggingEvent>().also {
            it.start()
            logger.addAppender(it)
        }
    }

    private fun detachAppender(appender: ListAppender<ILoggingEvent>) {
        val logger = LoggerFactory.getLogger(BlizzardAuctionApiClient::class.java) as Logger
        logger.detachAppender(appender)
        appender.stop()
    }
}
