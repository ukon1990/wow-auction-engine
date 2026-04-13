package net.jonasmf.auctionengine.integration.blizzard

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.domain.profession.ModifiedCraftingCategory
import net.jonasmf.auctionengine.domain.profession.ModifiedCraftingSlot
import net.jonasmf.auctionengine.dto.modifiedcrafting.ModifiedCraftingCategoryDTO
import net.jonasmf.auctionengine.dto.modifiedcrafting.ModifiedCraftingCategoryIndexDTO
import net.jonasmf.auctionengine.dto.modifiedcrafting.ModifiedCraftingIndexDTO
import net.jonasmf.auctionengine.dto.modifiedcrafting.ReagentSlotTypeDTO
import net.jonasmf.auctionengine.dto.modifiedcrafting.ReagentSlotTypeIndexDTO
import net.jonasmf.auctionengine.mapper.toDomain
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.Duration

private const val MODIFIED_CRAFTING_API_RETRY_ATTEMPTS = 3L
private val MODIFIED_CRAFTING_API_RETRY_BACKOFF: Duration = Duration.ofSeconds(2)

const val MODIFIED_CRAFTING_BASE_PATH = "/data/wow/modified-crafting"
private const val MODIFIED_CRAFTING_INDEX_PATH = "$MODIFIED_CRAFTING_BASE_PATH/index"
private const val MODIFIED_CRAFTING_CATEGORY_INDEX_PATH = "$MODIFIED_CRAFTING_BASE_PATH/category/index"
private const val MODIFIED_CRAFTING_SLOT_TYPE_INDEX_PATH = "$MODIFIED_CRAFTING_BASE_PATH/reagent-slot-type/index"

@Component
class ModifiedCraftingApiClient(
    private val blizzardApiSupport: BlizzardApiSupport,
) {
    private val logger: Logger = LoggerFactory.getLogger(ModifiedCraftingApiClient::class.java)

    fun getAllCategories(region: Region): List<ModifiedCraftingCategory> {
        getRootIndex(region)
        val uri = buildUri(region, MODIFIED_CRAFTING_CATEGORY_INDEX_PATH)
        val index = fetch<ModifiedCraftingCategoryIndexDTO>(uri, "fetch modified crafting category index")
        return index.categories.mapNotNull { category ->
            runCatching { getCategoryById(category.id, region) }
                .onFailure { error ->
                    logger.warn(
                        "Skipping modified crafting category {} for region {} after fetch failure: {}",
                        category.id,
                        region,
                        error.message ?: error::class.simpleName ?: "unknown error",
                    )
                }.getOrNull()
        }
    }

    fun getCategoryById(
        id: Int,
        region: Region,
    ): ModifiedCraftingCategory {
        val uri = buildUri(region, "$MODIFIED_CRAFTING_BASE_PATH/category/$id")
        return fetch<ModifiedCraftingCategoryDTO>(uri, "fetch modified crafting category").toDomain()
    }

    fun getAllSlotTypes(region: Region): List<ModifiedCraftingSlot> {
        getRootIndex(region)
        val uri = buildUri(region, MODIFIED_CRAFTING_SLOT_TYPE_INDEX_PATH)
        val index = fetch<ReagentSlotTypeIndexDTO>(uri, "fetch modified crafting reagent slot type index")
        return index.slotTypes.mapNotNull { slotType ->
            runCatching { getSlotTypeById(slotType.id, region) }
                .onFailure { error ->
                    logger.warn(
                        "Skipping modified crafting slot type {} for region {} after fetch failure: {}",
                        slotType.id,
                        region,
                        error.message ?: error::class.simpleName ?: "unknown error",
                    )
                }.getOrNull()
        }
    }

    fun getSlotTypeById(
        id: Int,
        region: Region,
    ): ModifiedCraftingSlot {
        val uri = buildUri(region, "$MODIFIED_CRAFTING_BASE_PATH/reagent-slot-type/$id")
        return fetch<ReagentSlotTypeDTO>(uri, "fetch modified crafting reagent slot type").toDomain()
    }

    private fun getRootIndex(region: Region): ModifiedCraftingIndexDTO =
        fetch(
            buildUri(region, MODIFIED_CRAFTING_INDEX_PATH),
            "fetch modified crafting index",
        )

    private fun buildUri(
        region: Region,
        path: String,
    ): String =
        blizzardApiSupport.buildRegionalUri(
            region = region,
            path = path,
            namespace = blizzardApiSupport.staticNamespaceForRegion(region).value,
        )

    private inline fun <reified T : Any> fetch(
        uri: String,
        operation: String,
    ): T =
        blizzardApiSupport
            .webClient()
            .get()
            .uri(uri)
            .retrieve()
            .bodyToMono<T>()
            .onErrorMap { error ->
                BlizzardApiClientException.from(
                    error = error,
                    operation = operation,
                    url = uri,
                    timeout = MODIFIED_CRAFTING_API_RETRY_BACKOFF,
                )
            }.retryTransientBlizzardFailures(
                maxRetries = MODIFIED_CRAFTING_API_RETRY_ATTEMPTS,
                backoff = MODIFIED_CRAFTING_API_RETRY_BACKOFF,
            ).doOnError { error ->
                logger.logBlizzardHttpFailure(error)
            }.block()!!
}
