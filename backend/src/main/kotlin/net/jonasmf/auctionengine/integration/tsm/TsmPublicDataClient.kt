package net.jonasmf.auctionengine.integration.tsm

import net.jonasmf.auctionengine.config.TsmProperties
import net.jonasmf.auctionengine.constant.Region
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Duration

private const val ITEMS_CSV = "items.csv"
private const val PETS_CSV = "pets.csv"
private const val ITEM_ID_COLUMN = "itemId"
private const val PET_SPECIES_ID_COLUMN = "petSpeciesId"
private val DOWNLOAD_TIMEOUT: Duration = Duration.ofMinutes(2)

@Component
class TsmPublicDataClient(
    @Qualifier("tsmPublicDataWebClient")
    private val webClient: WebClient,
    private val properties: TsmProperties,
) {
    fun downloadItems(region: Region): List<TsmRegionCsvRow> =
        parseTsmRegionCsv(downloadCsv(region, ITEMS_CSV), ITEM_ID_COLUMN)

    fun downloadPets(region: Region): List<TsmRegionCsvRow> =
        parseTsmRegionCsv(downloadCsv(region, PETS_CSV), PET_SPECIES_ID_COLUMN)

    private fun downloadCsv(
        region: Region,
        fileName: String,
    ): String {
        val url = "${properties.publicDataBaseUrl.trimEnd('/')}/${region.code}/region/$fileName"
        return try {
            webClient
                .get()
                .uri(url)
                .retrieve()
                .bodyToMono(String::class.java)
                .block(DOWNLOAD_TIMEOUT)
                ?: throw IllegalStateException("Empty response body from $url")
        } catch (error: WebClientResponseException) {
            throw IllegalStateException(
                "Failed to download TSM CSV from $url: ${error.statusCode.value()} ${error.statusText}",
                error,
            )
        }
    }
}
