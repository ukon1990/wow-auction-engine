package net.jonasmf.auctionengine.service.admin

import net.jonasmf.auctionengine.generated.model.AdminItemCompareField
import net.jonasmf.auctionengine.generated.model.AdminRecipe1
import net.jonasmf.auctionengine.generated.model.AdminRecipeBulkOverrideRequest
import net.jonasmf.auctionengine.generated.model.AdminRecipeCompareResponse
import net.jonasmf.auctionengine.generated.model.AdminRecipeFields
import net.jonasmf.auctionengine.generated.model.AdminRecipeOverrideRequest
import net.jonasmf.auctionengine.generated.model.AdminRecipePage
import net.jonasmf.auctionengine.integration.blizzard.RecipeApiClient
import net.jonasmf.auctionengine.repository.rds.AdminExpansionRepository
import net.jonasmf.auctionengine.repository.rds.AdminRecipeRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

private const val MAX_ADMIN_RECIPE_PAGE_SIZE = 100

@Service
class AdminRecipeService(
    private val adminRecipeRepository: AdminRecipeRepository,
    private val recipeApiClient: RecipeApiClient,
) {
    fun searchRecipes(
        query: String?,
        locale: String?,
        professionId: Int?,
        hasOverride: Boolean?,
        craftedItemId: Int?,
        page: Int,
        pageSize: Int,
    ): AdminRecipePage {
        validatePagination(page, pageSize)
        val result =
            adminRecipeRepository.searchRecipes(
                query = query,
                hasOverride = hasOverride,
                professionId = professionId,
                craftedItemId = craftedItemId,
                page = page,
                pageSize = pageSize,
                localeColumnSuffix = AdminExpansionRepository.resolveLocaleColumnSuffix(locale),
            )
        return AdminRecipePage(
            recipes = result.recipes,
            page = adminRecipeRepository.pageMetadata(page, pageSize, result.totalItems),
        )
    }

    fun getRecipe(
        id: Int,
        locale: String?,
        includeBase: Boolean,
        includeOverride: Boolean,
    ): AdminRecipe1 =
        findRecipe(id, locale)
            .toAdminRecipe(includeBase = includeBase, includeOverride = includeOverride)

    @Transactional
    fun upsertOverride(
        id: Int,
        request: AdminRecipeOverrideRequest,
    ): AdminRecipe1 {
        if (!adminRecipeRepository.hasBaseRecipe(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Base recipe not found: $id")
        }
        validateOverride(request)
        adminRecipeRepository.upsertOverride(id, request)
        return getRecipe(id, locale = null, includeBase = true, includeOverride = true)
    }

    @Transactional
    fun bulkUpsertOverrides(request: AdminRecipeBulkOverrideRequest): List<AdminRecipe1> {
        if (request.overrides.size > MAX_ADMIN_RECIPE_PAGE_SIZE) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Bulk override request cannot exceed 100 recipes")
        }
        val seenIds = mutableSetOf<Int>()
        request.overrides.forEach { override ->
            if (!seenIds.add(override.id)) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate recipe override id: ${override.id}")
            }
            if (!adminRecipeRepository.hasBaseRecipe(override.id)) {
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "Base recipe not found: ${override.id}")
            }
            validateOverride(override.`override`)
        }
        request.overrides.forEach { override ->
            adminRecipeRepository.upsertOverride(override.id, override.`override`)
        }
        return request.overrides.map { getRecipe(it.id, locale = null, includeBase = true, includeOverride = true) }
    }

    @Transactional
    fun deleteOverride(id: Int) {
        if (!adminRecipeRepository.deleteOverride(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe override not found: $id")
        }
    }

    fun compareWithApi(id: Int): AdminRecipeCompareResponse {
        val local = findRecipe(id, locale = null)
        val apiRecipe =
            runCatching { recipeApiClient.getById(id) }
                .getOrElse { error ->
                    throw ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Blizzard API recipe not found or unavailable: $id",
                        error,
                    )
                }
        val baseValues = local.base?.toCompareValues().orEmpty()
        val overrideValues = local.override?.toCompareValues().orEmpty()
        val effectiveValues = local.effective.toCompareValues()
        val apiValues =
            mapOf(
                "craftedItemId" to apiRecipe.craftedItemId?.toString(),
                "craftedQuantity" to apiRecipe.craftedQuantity?.toString(),
                "rank" to apiRecipe.rank?.toString(),
                "mediaUrl" to apiRecipe.mediaUrl,
                "mediaSourceUrl" to apiRecipe.mediaSourceUrl,
                "reagents" to apiRecipe.reagents.joinToString { "${it.itemId} x${it.quantity}" },
            )
        val fieldNames = baseValues.keys + overrideValues.keys + effectiveValues.keys + apiValues.keys
        return AdminRecipeCompareResponse(
            recipeId = id,
            fields =
                fieldNames.associateWith { field ->
                    AdminItemCompareField(
                        base = baseValues[field],
                        `override` = overrideValues[field],
                        api = apiValues[field],
                        effective = effectiveValues[field],
                    )
                },
        )
    }

    private fun findRecipe(
        id: Int,
        locale: String?,
    ) = adminRecipeRepository.findRecipeRows(
        id = id,
        localeColumnSuffix = AdminExpansionRepository.resolveLocaleColumnSuffix(locale),
    ) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found: $id")

    private fun validatePagination(
        page: Int,
        pageSize: Int,
    ) {
        if (page < 1) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be at least 1")
        }
        if (pageSize !in 1..MAX_ADMIN_RECIPE_PAGE_SIZE) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "pageSize must be between 1 and 100")
        }
    }

    private fun validateOverride(request: AdminRecipeOverrideRequest) {
        validateOptionalPositive("craftedQuantity", request.craftedQuantity)
        validateOptionalNonNegative("rank", request.rank)
        validateOptionalNonNegative("requiredSkillLevel", request.requiredSkillLevel)
        request.outputs?.let { outputs ->
            val sortOrders = mutableSetOf<Int>()
            outputs.forEach { output ->
                if (!sortOrders.add(output.sortOrder)) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate output sortOrder: ${output.sortOrder}")
                }
                validatePositive("output.craftedQuantity", output.craftedQuantity)
                validateNonNegative("output.sortOrder", output.sortOrder)
                output.requiredSkillLevel?.let { validateNonNegative("output.requiredSkillLevel", it) }
            }
        }
        request.reagents?.let { reagents ->
            val sortOrders = mutableSetOf<Int>()
            reagents.forEach { reagent ->
                if (!sortOrders.add(reagent.sortOrder)) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate reagent sortOrder: ${reagent.sortOrder}")
                }
                validatePositive("reagent.quantity", reagent.quantity)
                validateNonNegative("reagent.sortOrder", reagent.sortOrder)
                val ranks = mutableSetOf<Int>()
                reagent.ranks.orEmpty().forEach { rank ->
                    if (!ranks.add(rank.rank)) {
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate reagent rank: ${rank.rank}")
                    }
                    if (rank.rank !in 1..3) {
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reagent.rank must be between 1 and 3")
                    }
                    rank.skillPoints?.let { validateNonNegative("reagent.skillPoints", it) }
                }
            }
        }
    }

    private fun validateOptionalPositive(
        field: String,
        value: Int?,
    ) {
        value?.let { validatePositive(field, it) }
    }

    private fun validatePositive(
        field: String,
        value: Int,
    ) {
        if (value < 1) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$field must be at least 1")
        }
    }

    private fun validateOptionalNonNegative(
        field: String,
        value: Int?,
    ) {
        value?.let { validateNonNegative(field, it) }
    }

    private fun validateNonNegative(
        field: String,
        value: Int,
    ) {
        if (value < 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$field must be non-negative")
        }
    }
}

private fun AdminRecipeFields.toCompareValues(): Map<String, String?> =
    mapOf(
        "craftedItemId" to craftedItemId?.toString(),
        "craftedQuantity" to craftedQuantity?.toString(),
        "rank" to rank?.toString(),
        "requiredSkillLevel" to requiredSkillLevel?.toString(),
        "mediaUrl" to mediaUrl,
        "mediaSourceUrl" to mediaSourceUrl,
        "outputs" to outputs.orEmpty().joinToString { "${it.craftedItemId} x${it.craftedQuantity}" },
        "reagents" to reagents.orEmpty().joinToString { "${it.itemId} x${it.quantity}" },
        "overrideNote" to overrideNote,
    )
