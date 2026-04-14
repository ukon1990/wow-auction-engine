package net.jonasmf.auctionengine.integration.blizzard

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.domain.profession.Recipe
import net.jonasmf.auctionengine.dto.recipe.RecipeDTO
import net.jonasmf.auctionengine.mapper.toDomain
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

private const val RECIPE_API_RETRY_ATTEMPTS = 3L
private val RECIPE_API_RETRY_BACKOFF: Duration = Duration.ofSeconds(2)

const val RECIPE_BASE_PATH = "/data/wow/recipe"

@Component
class RecipeApiClient(
    private val blizzardApiSupport: BlizzardApiSupport,
) {
    private val logger: Logger = LoggerFactory.getLogger(RecipeApiClient::class.java)

    fun getById(id: Int): Recipe = getById(id, blizzardApiSupport.defaultRegion())

    fun getById(
        id: Int,
        region: Region,
    ): Recipe {
        val uri =
            blizzardApiSupport.buildRegionalUri(
                region = region,
                path = "$RECIPE_BASE_PATH/$id",
                namespace = blizzardApiSupport.staticNamespaceForRegion(region).value,
            )
        val response =
            blizzardApiSupport
                .webClient()
                .get()
                .uri(uri)
                .retrieve()
                .toEntity(RecipeDTO::class.java)
                .onErrorMap { error ->
                    BlizzardApiClientException.from(
                        error = error,
                        operation = "fetch recipe",
                        url = uri,
                        timeout = RECIPE_API_RETRY_BACKOFF,
                    )
                }.retryTransientBlizzardFailures(
                    maxRetries = RECIPE_API_RETRY_ATTEMPTS,
                    backoff = RECIPE_API_RETRY_BACKOFF,
                ).doOnError { error ->
                    logger.logBlizzardHttpFailure(error)
                }.block()!!
        val recipe = requireNotNull(response.body) { "Recipe body missing for $uri" }
        return recipe.toDomain(
            lastModified =
                response.headers.lastModified
                    .takeIf { it > 0 }
                    ?.let(Instant::ofEpochMilli),
        )
    }
}
