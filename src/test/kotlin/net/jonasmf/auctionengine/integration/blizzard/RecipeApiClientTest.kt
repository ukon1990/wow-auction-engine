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

class RecipeApiClientTest {
    @Test
    fun `getById returns mapped recipe`() {
        val webClient = buildWebClient { handleRequest(it) }
        val client = RecipeApiClient(createSupport(webClient))

        val recipe = client.getById(42363, Region.Europe)

        assertEquals(42363, recipe.id)
        assertEquals("Ceremonious Breastplate", recipe.name.en_US)
        assertEquals(
            "https://us.api.blizzard.com/data/wow/media/recipe/42363?namespace=static-12.0.1_65617-us",
            recipe.mediaUrl,
        )
        assertEquals(171374, recipe.craftedItemId)
        assertEquals(1, recipe.craftedQuantity)
        assertEquals(2, recipe.reagents.size)
        assertEquals(171828, recipe.reagents.first().itemId)
        assertEquals(12, recipe.reagents.first().quantity)
        assertEquals(1, recipe.modifiedCraftingSlots.size)
        assertEquals(46, recipe.modifiedCraftingSlots.first().id)
        assertEquals(0, recipe.modifiedCraftingSlots.first().displayOrder)
    }

    private fun recipeById(id: Int): String = loadFixture(this, "/blizzard/recipe/$id-response.json")

    private fun handleRequest(request: ClientRequest): Mono<ClientResponse> {
        val path = request.url().path

        return when {
            path.matches(Regex(".*/recipe/\\d+$")) -> okJson(recipeById(path.substringAfterLast('/').toInt()))
            else -> error("Unexpected request: ${request.method()} ${request.url()}")
        }
    }
}
