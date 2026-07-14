package net.jonasmf.auctionengine.service.admin

import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperQualityOutput
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperRecipe
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperReagent
import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperReagentSlot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class NormalizedRecipeOverrideMapperTest {
    @Test
    fun `maps enchanting recipe output item id to override outputs`() {
        val request =
            NormalizedRecipeOverrideMapper.toOverrideRequest(
                enchantingRecipe(outputItemId = 224_001),
                importId = 42,
            )

        assertNotNull(request)
        assertEquals(224_001, request!!.craftedItemId)
        assertEquals(224_001, request.outputs?.single()?.craftedItemId)
        assertEquals("Auction Helper import #42", request.overrideNote)
    }

    @Test
    fun `maps quality outputs and reagent rank variants`() {
        val request =
            NormalizedRecipeOverrideMapper.toOverrideRequest(
                NormalizedAuctionHelperRecipe(
                    recipeId = 450_216,
                    name = "Charged Claymore",
                    learned = true,
                    supportsQualities = true,
                    craftedItemId = 222_443,
                    outputItemId = 222_443,
                    qualityOutputItemIds =
                        listOf(
                            NormalizedAuctionHelperQualityOutput(quality = 1, itemId = 222_443),
                            NormalizedAuctionHelperQualityOutput(quality = 2, itemId = 222_444),
                        ),
                    qualityThresholds = emptyList(),
                    reagentSlots =
                        listOf(
                            NormalizedAuctionHelperReagentSlot(
                                slotIndex = 1,
                                quantity = BigDecimal.TWO,
                                reagents =
                                    listOf(
                                        NormalizedAuctionHelperReagent(210_221, BigDecimal.TWO, quality = 1),
                                        NormalizedAuctionHelperReagent(210_222, BigDecimal.TWO, quality = 2),
                                    ),
                            ),
                        ),
                    maxQualityRequiredReagents = emptyList(),
                ),
                importId = 7,
            )

        assertNotNull(request)
        assertEquals(listOf(222_443, 222_444), request!!.outputs?.map { it.craftedItemId })
        assertNull(request.rank)
        val reagent = request.reagents!!.single()
        assertEquals(210_221, reagent.itemId)
        assertEquals(2, reagent.quantity)
        assertEquals(
            listOf(1 to 210_221, 2 to 210_222),
            reagent.ranks.orEmpty().map { it.rank to it.itemId },
        )
    }

    @Test
    fun `maps quality thresholds to output required skill levels`() {
        val request =
            NormalizedRecipeOverrideMapper.toOverrideRequest(
                NormalizedAuctionHelperRecipe(
                    recipeId = 450_216,
                    name = "Charged Claymore",
                    learned = true,
                    supportsQualities = true,
                    craftedItemId = 222_443,
                    outputItemId = 222_443,
                    baseSkill = BigDecimal("50"),
                    lowerSkillThreshold = BigDecimal("45"),
                    qualityOutputItemIds =
                        listOf(
                            NormalizedAuctionHelperQualityOutput(quality = 1, itemId = 222_443),
                            NormalizedAuctionHelperQualityOutput(quality = 2, itemId = 222_444),
                            NormalizedAuctionHelperQualityOutput(quality = 3, itemId = 222_445),
                        ),
                    qualityThresholds =
                        listOf(
                            BigDecimal("100"),
                            BigDecimal("200"),
                            BigDecimal("300"),
                        ),
                    reagentSlots = emptyList(),
                    maxQualityRequiredReagents = emptyList(),
                ),
                importId = 9,
            )

        assertNotNull(request)
        assertEquals(45, request!!.requiredSkillLevel)
        assertEquals(listOf(100, 200, 300), request.outputs?.map { it.requiredSkillLevel })
    }

    @Test
    fun `maps skill-only addon recipe to override with required skill level`() {
        val request =
            NormalizedRecipeOverrideMapper.toOverrideRequest(
                NormalizedAuctionHelperRecipe(
                    recipeId = 99,
                    name = "Skill only",
                    learned = true,
                    baseSkill = BigDecimal("75.5"),
                    lowerSkillThreshold = BigDecimal("70"),
                    qualityOutputItemIds = emptyList(),
                    qualityThresholds = emptyList(),
                    reagentSlots = emptyList(),
                    maxQualityRequiredReagents = emptyList(),
                ),
                importId = 3,
            )

        assertNotNull(request)
        assertEquals(70, request!!.requiredSkillLevel)
        assertNull(request.outputs)
        assertNull(request.reagents)
    }

    @Test
    fun `returns null when addon recipe has no outputs reagents or skill data`() {
        assertNull(
            NormalizedRecipeOverrideMapper.toOverrideRequest(
                NormalizedAuctionHelperRecipe(
                    recipeId = 1,
                    name = "Empty",
                    learned = true,
                    qualityOutputItemIds = emptyList(),
                    qualityThresholds = emptyList(),
                    reagentSlots = emptyList(),
                    maxQualityRequiredReagents = emptyList(),
                ),
                importId = 1,
            ),
        )
    }

    private fun enchantingRecipe(outputItemId: Int) =
        NormalizedAuctionHelperRecipe(
            recipeId = 123_456,
            name = "Enchant Chest - Test",
            learned = true,
            isEnchantingRecipe = true,
            outputItemId = outputItemId,
            qualityOutputItemIds = emptyList(),
            qualityThresholds = emptyList(),
            reagentSlots =
                listOf(
                    NormalizedAuctionHelperReagentSlot(
                        slotIndex = 1,
                        quantity = BigDecimal.ONE,
                        reagents = listOf(NormalizedAuctionHelperReagent(190_000, BigDecimal.ONE)),
                    ),
                ),
            maxQualityRequiredReagents = emptyList(),
        )
}
