package net.jonasmf.auctionengine.integration.tsm

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Instant
import net.jonasmf.auctionengine.config.TsmProperties
import net.jonasmf.auctionengine.constant.Region

class TsmRegionCsvParserTest {
    @Test
    fun `parse items csv maps metrics and ignores name`() {
        val csv =
            """
            itemId,name,marketValue,historical,avgSalePrice,saleRate,soldPerDay,updatedAt
            39,"Recruit's Pants",1000,2000,1500,0.25,1.5,1718000000
            40,"Item, with comma",,,,0.1,0.01,2024-06-10T12:00:00Z
            """.trimIndent()

        val rows = parseTsmRegionCsv(csv, "itemId")

        assertThat(rows).hasSize(2)
        assertThat(rows[0]).isEqualTo(
            TsmRegionCsvRow(
                subjectId = 39,
                saleRate = BigDecimal("0.25"),
                soldPerDay = BigDecimal("1.5"),
                marketValue = 1000L,
                historical = 2000L,
                avgSalePrice = 1500L,
                sourceUpdatedAt = Instant.ofEpochSecond(1_718_000_000L),
            ),
        )
        assertThat(rows[1]).isEqualTo(
            TsmRegionCsvRow(
                subjectId = 40,
                saleRate = BigDecimal("0.1"),
                soldPerDay = BigDecimal("0.01"),
                marketValue = null,
                historical = null,
                avgSalePrice = null,
                sourceUpdatedAt = Instant.parse("2024-06-10T12:00:00Z"),
            ),
        )
    }

    @Test
    fun `parse pets csv uses petSpeciesId`() {
        val csv =
            """
            petSpeciesId,name,marketValue,historical,avgSalePrice,saleRate,soldPerDay,updatedAt
            39,Mechanical Squirrel,5000,4000,4500,0.5,2.0,1718000000000
            """.trimIndent()

        val rows = parseTsmRegionCsv(csv, "petSpeciesId")

        assertThat(rows).containsExactly(
            TsmRegionCsvRow(
                subjectId = 39,
                saleRate = BigDecimal("0.5"),
                soldPerDay = BigDecimal("2.0"),
                marketValue = 5000L,
                historical = 4000L,
                avgSalePrice = 4500L,
                sourceUpdatedAt = Instant.ofEpochMilli(1_718_000_000_000L),
            ),
        )
    }

    @Test
    fun `parse rejects missing required column`() {
        val csv =
            """
            itemId,name,saleRate,soldPerDay,updatedAt
            1,x,0.1,0.2,1718000000
            """.trimIndent()

        assertThatThrownBy { parseTsmRegionCsv(csv, "itemId") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("marketValue")
    }
}

class TsmPublicDataClientTest {
    @Test
    fun `downloadItems and downloadPets build region paths and parse bodies`() {
        val itemsCsv =
            """
            itemId,name,marketValue,historical,avgSalePrice,saleRate,soldPerDay,updatedAt
            39,Recruit's Pants,1000,2000,1500,0.25,1.5,1718000000
            """.trimIndent()
        val petsCsv =
            """
            petSpeciesId,name,marketValue,historical,avgSalePrice,saleRate,soldPerDay,updatedAt
            39,Mechanical Squirrel,5000,4000,4500,0.5,2.0,1718000000
            """.trimIndent()

        val requestedUrls = mutableListOf<String>()
        val webClient =
            WebClient
                .builder()
                .exchangeFunction(
                    ExchangeFunction { request: ClientRequest ->
                        requestedUrls += request.url().toString()
                        val body =
                            when {
                                request.url().path.endsWith("/items.csv") -> itemsCsv
                                request.url().path.endsWith("/pets.csv") -> petsCsv
                                else -> error("Unexpected URL: ${request.url()}")
                            }
                        Mono.just(
                            ClientResponse
                                .create(HttpStatus.OK)
                                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                                .body(body)
                                .build(),
                        )
                    },
                ).build()

        val client =
            TsmPublicDataClient(
                webClient = webClient,
                properties = TsmProperties(publicDataBaseUrl = "https://public-data.example.test/retail"),
            )

        val items = client.downloadItems(Region.Europe)
        val pets = client.downloadPets(Region.Europe)

        assertThat(requestedUrls).containsExactly(
            "https://public-data.example.test/retail/eu/region/items.csv",
            "https://public-data.example.test/retail/eu/region/pets.csv",
        )
        assertThat(items).hasSize(1)
        assertThat(items[0].subjectId).isEqualTo(39)
        assertThat(items[0].saleRate).isEqualByComparingTo("0.25")
        assertThat(pets).hasSize(1)
        assertThat(pets[0].subjectId).isEqualTo(39)
        assertThat(pets[0].saleRate).isEqualByComparingTo("0.5")
    }
}
