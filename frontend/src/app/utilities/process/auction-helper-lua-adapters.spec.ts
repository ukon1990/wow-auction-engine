import {
  auctionHelperExportSource as exportSource,
  auctionHelperProfessionsSource as professionsSource,
} from './testing/fixture-sources';

import {
  NormalizedProfessionSnapshot,
  NormalizedTalentExport,
  auctionHelperLuaProcessor,
} from './auction-helper-lua-adapters';

describe('AuctionHelper Lua adapters', () => {
  it('normalizes recipe outputs, reagents, and distinct crafting skill fields', () => {
    const result = auctionHelperLuaProcessor.process<NormalizedProfessionSnapshot>(
      'AuctionHelper_Professions.lua',
      professionsSource,
    );
    const recipe = result.data.characters[0]?.professions[0]?.recipes.find(
      (candidate) => candidate.recipeId === 12345,
    );

    expect(recipe).toMatchObject({
      recipeId: 12345,
      craftedItemId: 222437,
      outputQualityItemIds: [
        { quality: 1, itemId: 222437 },
        { quality: 2, itemId: 222438 },
      ],
      reagents: [
        {
          itemId: 210221,
          quality: 1,
          quantity: 3,
          slotIndex: 1,
          dataSlotIndex: 1,
          slotType: 'BASIC',
        },
        {
          itemId: 210222,
          quality: 2,
          quantity: 3,
          slotIndex: 1,
          dataSlotIndex: 1,
          slotType: 'BASIC',
        },
      ],
      maxQualityRequiredReagents: [
        { slotIndex: null, dataSlotIndex: 1, itemId: 210222, quantity: 3 },
      ],
      crafting: {
        baseDifficulty: 300,
        baseSkill: 100,
        bonusSkill: 25,
        requiredReagentSkillDelta: 40,
        lowerSkillThreshold: 200,
        upperSkillThreshold: 300,
        qualityThresholds: [100, 200, 300],
      },
    });
  });

  it('diagnoses missing item and skill associations without guessing', () => {
    const result = auctionHelperLuaProcessor.process<NormalizedProfessionSnapshot>(
      'AuctionHelper_Professions.lua',
      professionsSource,
    );

    expect(
      result.diagnostics.filter((item) => item.recipeId === 999).map((item) => item.code),
    ).toEqual(['CRAFTED_ITEM_MISSING', 'CRAFTING_SKILL_DATA_MISSING']);
  });

  it('converts exported child paths into normalized parent relationships', () => {
    const result = auctionHelperLuaProcessor.process<NormalizedProfessionSnapshot>(
      'AuctionHelper_Professions.lua',
      professionsSource,
    );

    expect(result.data.characters[0]?.professions[0]?.talents?.trees[0]?.tabs[0]?.nodes).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ nodeId: 101, parentNodeIds: [] }),
        expect.objectContaining({ nodeId: 102, parentNodeIds: [101] }),
      ]),
    );
  });

  it('does not report missing output or difficulty for non-quality gathering recipes', () => {
    const result = auctionHelperLuaProcessor.process<NormalizedProfessionSnapshot>(
      'AuctionHelper_Professions.lua',
      professionsSource,
    );
    const gathering = result.data.characters[0]?.professions[0]?.recipes.find(
      (recipe) => recipe.recipeId === 1000,
    );

    expect(gathering).toMatchObject({
      recipeType: 4,
      supportsQualities: false,
      isGatheringRecipe: true,
      hasCraftingOperationInfo: false,
      craftedItemId: null,
    });
    expect(result.diagnostics.filter((item) => item.recipeId === 1000)).toEqual([]);
  });

  it('normalizes the separately exported talent payload wrapper', () => {
    const result = auctionHelperLuaProcessor.process<NormalizedTalentExport>(
      'AuctionHelper.lua',
      exportSource,
    );

    expect(result.data).toEqual({
      formatVersion: 1,
      exportFormat: 'AHCBOR1',
      scope: 'profession_talents',
      generatedAt: '2026-07-13T10:00:00Z',
      encodedPayload: 'AHCBOR1:eJwDAAAAAAE=',
    });
    expect(result.diagnostics).toEqual([]);
  });

  it('requires the exact registered filename and expected global', () => {
    expect(() => auctionHelperLuaProcessor.process('Other.lua', professionsSource)).toThrowError(
      /No registered addon adapter/,
    );
  });
});
