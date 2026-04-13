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

class ModifiedCraftingApiClientTest {
    @Test
    fun `getAllCategories returns mapped categories`() {
        val client = ModifiedCraftingApiClient(createSupport(buildWebClient { handleRequest(it) }))

        val categories = client.getAllCategories(Region.Europe)

        assertEquals(2, categories.size)
        assertEquals(827, categories[0].id)
        assertEquals("Global Finishing Reagent 02", categories[0].name.en_US)
        assertEquals(828, categories[1].id)
    }

    @Test
    fun `getAllSlotTypes returns mapped slot types with compatible categories`() {
        val client = ModifiedCraftingApiClient(createSupport(buildWebClient { handleRequest(it) }))

        val slots = client.getAllSlotTypes(Region.Europe)

        assertEquals(2, slots.size)
        assertEquals(404, slots[0].id)
        assertEquals(1, slots[0].compatibleCategories.size)
        assertEquals(776, slots[0].compatibleCategories.first().id)
        assertEquals(417, slots[1].id)
    }

    @Test
    fun `getAllCategories tolerates category responses without name`() {
        val client = ModifiedCraftingApiClient(createSupport(buildWebClient { handleRequest(it) }))

        val category = client.getCategoryById(502, Region.Europe)

        assertEquals(502, category.id)
        assertEquals("", category.name.en_US)
        assertEquals("", category.name.en_GB)
    }

    private fun handleRequest(request: ClientRequest): Mono<ClientResponse> {
        val path = request.url().path
        return when {
            path.endsWith("/modified-crafting/index") -> okJson(rootIndexBody())
            path.endsWith("/modified-crafting/category/index") -> okJson(categoryIndexBody())
            path.endsWith("/modified-crafting/reagent-slot-type/index") -> okJson(slotTypeIndexBody())
            path.matches(Regex(".*/modified-crafting/category/\\d+$")) -> {
                okJson(categoryById(path.substringAfterLast('/').toInt()))
            }
            path.matches(Regex(".*/modified-crafting/reagent-slot-type/\\d+$")) -> {
                okJson(slotTypeById(path.substringAfterLast('/').toInt()))
            }
            else -> error("Unexpected request: ${request.method()} ${request.url()}")
        }
    }

    private fun rootIndexBody(): String = """
        {
          "_links": {
            "self": {
              "href": "https://us.api.blizzard.com/data/wow/modified-crafting/index?namespace=static-us"
            }
          }
        }
    """.trimIndent()

    private fun categoryIndexBody(): String = """
        {
          "_links": {
            "self": {
              "href": "https://us.api.blizzard.com/data/wow/modified-crafting/category/index?namespace=static-us"
            }
          },
          "categories": [
            {
              "id": 827,
              "name": { "en_US": "Global Finishing Reagent 02", "en_GB": "Global Finishing Reagent 02" },
              "key": { "href": "https://us.api.blizzard.com/data/wow/modified-crafting/category/827?namespace=static-us" }
            },
            {
              "id": 828,
              "name": { "en_US": "Global Finishing Reagent 03", "en_GB": "Global Finishing Reagent 03" },
              "key": { "href": "https://us.api.blizzard.com/data/wow/modified-crafting/category/828?namespace=static-us" }
            }
          ]
        }
    """.trimIndent()

    private fun slotTypeIndexBody(): String = """
        {
          "_links": {
            "self": {
              "href": "https://us.api.blizzard.com/data/wow/modified-crafting/reagent-slot-type/index?namespace=static-us"
            }
          },
          "slot_types": [
            {
              "id": 404,
              "name": { "en_US": "Global Finishing Reagent 03", "en_GB": "Global Finishing Reagent 03" },
              "key": { "href": "https://us.api.blizzard.com/data/wow/modified-crafting/reagent-slot-type/404?namespace=static-us" }
            },
            {
              "id": 417,
              "name": { "en_US": "Optional", "en_GB": "Optional" },
              "key": { "href": "https://us.api.blizzard.com/data/wow/modified-crafting/reagent-slot-type/417?namespace=static-us" }
            }
          ]
        }
    """.trimIndent()

    private fun categoryById(id: Int): String =
        when (id) {
            502 ->
                """
                {
                  "_links": {
                    "self": {
                      "href": "https://us.api.blizzard.com/data/wow/modified-crafting/category/502?namespace=static-us"
                    }
                  },
                  "id": 502
                }
                """.trimIndent()
            else -> loadFixture(this, "/blizzard/modified-crafting/category/$id-response.json")
        }

    private fun slotTypeById(id: Int): String = loadFixture(this, "/blizzard/modified-crafting/reagent-slot-type/$id-response.json")
}
