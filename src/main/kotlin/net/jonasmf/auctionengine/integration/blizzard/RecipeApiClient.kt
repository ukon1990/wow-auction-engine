package net.jonasmf.auctionengine.integration.blizzard

import net.jonasmf.auctionengine.domain.profession.Recipe
import net.jonasmf.auctionengine.dto.recipe.RecipeDTO
import net.jonasmf.auctionengine.mapper.toDomain
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.bodyToMono

const val RECIPE_BASE_PATH = "/data/wow/recipe"

@Component
class RecipeApiClient(
    private val blizzardApiSupport: BlizzardApiSupport,
) {
    fun getById(id: Int): Recipe {
        val uri =
            blizzardApiSupport.buildRegionalUri(
                path = "$RECIPE_BASE_PATH/$id",
                namespace = blizzardApiSupport.dynamicNamespaceForRegion().value,
            )
        val recipe =
            blizzardApiSupport
                .webClient()
                .get()
                .uri(uri)
                .retrieve()
                .bodyToMono<RecipeDTO>()
                .block()!!
        return recipe.toDomain()
    }
}
