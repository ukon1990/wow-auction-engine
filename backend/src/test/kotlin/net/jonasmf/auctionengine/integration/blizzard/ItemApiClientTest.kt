package net.jonasmf.auctionengine.integration.blizzard

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.testsupport.BlizzardApiCallSupport.Companion.buildWebClient
import net.jonasmf.auctionengine.testsupport.BlizzardApiCallSupport.Companion.createSupport
import net.jonasmf.auctionengine.testsupport.BlizzardApiCallSupport.Companion.okJson
import net.jonasmf.auctionengine.testsupport.loadFixture
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import reactor.core.publisher.Mono
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ItemApiClientTest {
    @Test
    fun `getById returns item details from fixtures`() {
        val webClient = buildWebClient { handleRequest(it) }
        val client = ItemApiClient(createSupport(webClient))
        val itemIds = listOf(171374, 171388, 171391, 171412, 171428, 171441, 171828, 172230, 180733, 251285)

        val items = itemIds.map { itemId -> client.getById(itemId, Region.Europe) }

        assertEquals(itemIds, items.map { it.id })
        assertEquals("ON_EQUIP", items.first().binding?.type)
        assertEquals(
            "Armor",
            items
                .first()
                .itemClass.name.en_US,
        )
        assertNotNull(items[1].binding)
        assertEquals(true, items[5].isStackable)
        assertEquals(true, items[0].appearances.isNotEmpty())
    }

    private fun itemById(id: Int): String = loadFixture(this, "/blizzard/item/$id-response.json")

    private fun handleRequest(request: ClientRequest): Mono<ClientResponse> {
        val path = request.url().path
        return when {
            path.matches(Regex(".*/item/\\d+$")) -> okJson(itemById(path.substringAfterLast('/').toInt()))
            else -> error("Unexpected request: ${request.method()} ${request.url()}")
        }
    }
}
