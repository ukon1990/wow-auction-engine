package net.jonasmf.auctionengine.service.admin

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.validation.Validation
import io.mockk.mockk
import io.mockk.verify
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperProfessionData
import net.jonasmf.auctionengine.repository.rds.NormalizedProfessionImportRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal

class NormalizedAuctionHelperProfessionInspectionServiceTest {
    private val objectMapper = jacksonObjectMapper()
    private val validator = Validation.buildDefaultValidatorFactory().validator
    private val repository = mockk<NormalizedProfessionImportRepository>(relaxed = true)
    private val service = NormalizedAuctionHelperProfessionInspectionService(validator, repository)

    @Test
    fun `accepts normalized recipe item skill and reagent associations`() {
        val result = service.inspect(normalizedPayload())

        assertThat(result.imported).isTrue()
        assertThat(result.charactersFound).isEqualTo(1)
        assertThat(result.professionsFound).isEqualTo(1)
        assertThat(result.recipesFound).isEqualTo(1)
        assertThat(result.recipesWithOutputItemFound).isEqualTo(1)
        assertThat(result.missingOutputItemAssociations).isZero()
        assertThat(result.missingReagentItemAssociations).isZero()
        assertThat(result.diagnostics).isEmpty()
        verify(exactly = 1) { repository.save(any(), 1, 1) }
    }

    @Test
    fun `rejects an invalid item id`() {
        val payload = normalizedPayload()
        val profession = payload.characters.single().professions.single()
        val recipe = profession.recipes.single().copy(outputItemId = -1)
        val invalid = payload.withProfession(profession.copy(recipes = listOf(recipe)))

        assertBadRequest(invalid, "outputItemId")
    }

    @Test
    fun `rejects duplicate recipe ids within a profession`() {
        val payload = normalizedPayload()
        val profession = payload.characters.single().professions.single()
        val duplicate = payload.withProfession(profession.copy(recipes = listOf(profession.recipes.single(), profession.recipes.single())))

        assertBadRequest(duplicate, "Recipe IDs must be unique")
    }

    @Test
    fun `rejects collection limits`() {
        val payload = normalizedPayload()
        val oversized = payload.copy(characters = List(101) { payload.characters.single() })

        assertBadRequest(oversized, "characters")
    }

    @Test
    fun `rejects non monotonic quality thresholds`() {
        val payload = normalizedPayload()
        val profession = payload.characters.single().professions.single()
        val recipe = profession.recipes.single().copy(qualityThresholds = listOf(100.toBigDecimal(), 90.toBigDecimal()))

        assertBadRequest(payload.withProfession(profession.copy(recipes = listOf(recipe))), "monotonically increasing")
    }

    @Test
    fun `accepts an incomplete max quality reagent association as a diagnostic`() {
        val payload = normalizedPayload()
        val profession = payload.characters.single().professions.single()
        val recipe = profession.recipes.single()
        val association = recipe.maxQualityRequiredReagents.single().copy(itemId = 999999)

        val result =
            service.inspect(
                payload.withProfession(profession.copy(recipes = listOf(recipe.copy(maxQualityRequiredReagents = listOf(association))))),
            )

        assertThat(result.imported).isTrue()
        assertThat(result.diagnostics.single().code.value).isEqualTo("MAX_QUALITY_REAGENT_ASSOCIATION_INCOMPLETE")
        assertThat(result.diagnostics.single().exampleRecipeIds).containsExactly(recipe.recipeId)
    }

    @Test
    fun `accepts zero quantities used for unavailable addon metadata`() {
        val payload = normalizedPayload()
        val profession = payload.characters.single().professions.single()
        val recipe = profession.recipes.single()
        val slot = recipe.reagentSlots.single()
        val zeroQuantityRecipe =
            recipe.copy(
                reagentSlots = listOf(slot.copy(quantity = BigDecimal.ZERO, reagents = listOf(slot.reagents.single().copy(quantity = BigDecimal.ZERO)))),
                maxQualityRequiredReagents = listOf(recipe.maxQualityRequiredReagents.single().copy(quantity = BigDecimal.ZERO)),
            )

        val result = service.inspect(payload.withProfession(profession.copy(recipes = listOf(zeroQuantityRecipe))))

        assertThat(result.imported).isTrue()
    }

    @Test
    fun `reports missing output and reagent item associations`() {
        val payload = normalizedPayload()
        val profession = payload.characters.single().professions.single()
        val recipe =
            profession.recipes.single().copy(
                craftedItemId = null,
                outputItemId = null,
                qualityOutputItemIds = emptyList(),
                reagentSlots = listOf(profession.recipes.single().reagentSlots.single().copy(reagents = emptyList())),
                maxQualityRequiredReagents = emptyList(),
            )

        val result = service.inspect(payload.withProfession(profession.copy(recipes = listOf(recipe))))

        assertThat(result.missingOutputItemAssociations).isEqualTo(1)
        assertThat(result.missingReagentItemAssociations).isEqualTo(1)
        assertThat(result.diagnostics.map { it.code.value }).containsExactly("CRAFTED_ITEM_MISSING", "REAGENT_ITEM_MISSING")
        assertThat(result.diagnostics.map { it.count }).containsExactly(1, 1)
        assertThat(result.diagnostics.flatMap { it.exampleRecipeIds }).containsOnly(recipe.recipeId)
    }

    @Test
    fun `does not report crafted item or skill data for gathering recipes`() {
        val payload = normalizedPayload()
        val profession = payload.characters.single().professions.single()
        val recipe =
            profession.recipes.single().copy(
                craftedItemId = null,
                outputItemId = null,
                qualityOutputItemIds = emptyList(),
                recipeType = 4,
                supportsQualities = false,
                isGatheringRecipe = true,
                hasCraftingOperationInfo = false,
                baseDifficulty = null,
                baseSkill = null,
                bonusSkill = null,
                requiredReagentSkillDelta = null,
                lowerSkillThreshold = null,
                upperSkillThreshold = null,
                qualityThresholds = emptyList(),
                maxQualityRequiredReagents = emptyList(),
            )

        val result = service.inspect(payload.withProfession(profession.copy(recipes = listOf(recipe))))

        assertThat(result.missingOutputItemAssociations).isZero()
        assertThat(result.missingCraftingSkillData).isZero()
    }

    @Test
    fun `reports missing crafted item for numeric item recipe type`() {
        val payload = normalizedPayload()
        val profession = payload.characters.single().professions.single()
        val recipe =
            profession.recipes.single().copy(
                recipeType = 1,
                supportsQualities = false,
                craftedItemId = null,
                outputItemId = null,
                qualityOutputItemIds = emptyList(),
            )

        val result = service.inspect(payload.withProfession(profession.copy(recipes = listOf(recipe))))

        assertThat(result.missingOutputItemAssociations).isEqualTo(1)
        assertThat(result.diagnostics.single().code.value).isEqualTo("CRAFTED_ITEM_MISSING")
    }

    @Test
    fun `associates maximum quality reagent by data slot index`() {
        val payload = normalizedPayload()
        val profession = payload.characters.single().professions.single()
        val recipe = profession.recipes.single()
        val association = recipe.maxQualityRequiredReagents.single().copy(slotIndex = null, dataSlotIndex = 1)

        val result =
            service.inspect(
                payload.withProfession(profession.copy(recipes = listOf(recipe.copy(maxQualityRequiredReagents = listOf(association))))),
            )

        assertThat(result.recipesFound).isEqualTo(1)
    }

    @Test
    fun `preserves zero as present crafting skill data`() {
        val payload = normalizedPayload()
        val profession = payload.characters.single().professions.single()
        val recipe =
            profession.recipes.single().copy(
                baseDifficulty = BigDecimal.ZERO,
                baseSkill = null,
                bonusSkill = null,
                requiredReagentSkillDelta = null,
                lowerSkillThreshold = null,
                upperSkillThreshold = null,
                qualityThresholds = emptyList(),
            )

        val result = service.inspect(payload.withProfession(profession.copy(recipes = listOf(recipe))))

        assertThat(result.missingCraftingSkillData).isZero()
    }

    private fun normalizedPayload(): NormalizedAuctionHelperProfessionData = objectMapper.readValue(normalizedPayloadJson)

    private fun NormalizedAuctionHelperProfessionData.withProfession(
        profession: net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperProfession,
    ): NormalizedAuctionHelperProfessionData =
        copy(characters = listOf(characters.single().copy(professions = listOf(profession))))

    private fun assertBadRequest(
        payload: NormalizedAuctionHelperProfessionData,
        detail: String,
    ) {
        assertThatThrownBy { service.inspect(payload) }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining(detail)
    }
}

private val normalizedPayloadJson =
    """
    {
      "contractVersion": 1,
      "source": {
        "addon": "AuctionHelper",
        "addonVersion": "1.2.3",
        "processorVersion": "1.0.0",
        "files": [{"fileName": "AuctionHelper_Professions.lua", "sha256": "${"a".repeat(64)}"}]
      },
      "characters": [{
        "characterKey": "eu-realm-character",
        "name": "Character",
        "realm": "realm",
        "region": "eu",
        "professions": [{
          "professionId": 164,
          "skillLineId": 2872,
          "name": "Blacksmithing",
          "skillLevel": 85,
          "maxSkillLevel": 100,
          "recipes": [{
            "recipeId": 450216,
            "name": "Charged Claymore",
            "learned": true,
            "recipeType": 1,
            "supportsQualities": true,
            "isGatheringRecipe": false,
            "isEnchantingRecipe": false,
            "isSalvageRecipe": false,
            "isRecraft": false,
            "hasCraftingOperationInfo": true,
            "craftedItemId": 222443,
            "outputItemId": 222443,
            "qualityOutputItemIds": [{"quality": 1, "itemId": 222443}, {"quality": 2, "itemId": 222444}],
            "baseDifficulty": 300,
            "baseSkill": 85,
            "bonusSkill": 10,
            "requiredReagentSkillDelta": 25,
            "lowerSkillThreshold": 100,
            "upperSkillThreshold": 300,
            "qualityThresholds": [100, 200, 300],
            "reagentSlots": [{
              "slotIndex": 1,
              "dataSlotIndex": 1,
              "slotType": "BASIC",
              "quantity": 2,
              "reagents": [{"itemId": 210221, "quality": 1, "quantity": 2}]
            }],
            "maxQualityRequiredReagents": [{"slotIndex": 1, "itemId": 210221, "quantity": 2}]
          }]
        }]
      }]
    }
    """.trimIndent()
