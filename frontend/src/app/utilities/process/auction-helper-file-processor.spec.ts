import { webcrypto } from 'node:crypto';

import { auctionHelperProfessionsSource as professionsSource } from './testing/fixture-sources';

import { processAuctionHelperFiles } from './auction-helper-file-processor';

describe('processAuctionHelperFiles', () => {
  it('creates the bounded generated API contract without including Lua source', async () => {
    Object.defineProperty(globalThis, 'crypto', { configurable: true, value: webcrypto });
    const file = new File([professionsSource], 'AuctionHelper_Professions.lua', {
      type: 'text/plain',
    });

    const preview = await processAuctionHelperFiles([file], 'eu');
    const serialized = JSON.stringify(preview.payload);

    expect(preview.charactersFound).toBe(1);
    expect(preview.recipesFound).toBe(3);
    expect(preview.payload.source.files[0]).toMatchObject({
      fileName: 'AuctionHelper_Professions.lua',
    });
    expect(preview.payload.source.files[0]?.sha256).toMatch(/^[a-f0-9]{64}$/);
    const recipe = preview.payload.characters[0]?.professions[0]?.recipes.find(
      (candidate) => candidate.recipeId === 450216,
    );
    expect(recipe).toMatchObject({
      recipeType: 1,
      reagentSlots: [{ slotIndex: 1, dataSlotIndex: 1 }],
      maxQualityRequiredReagents: [{ dataSlotIndex: 1, itemId: 210222, quantity: 3 }],
    });
    expect(preview.payload.characters[0]).toMatchObject({
      region: 'eu',
      characterKey: 'eu:Player-1',
    });
    expect(serialized).not.toContain('AuctionHelperProfessionsDB');
    expect(serialized).not.toContain(professionsSource);
  });

  it('requires the professions source and keeps region identities distinct', async () => {
    const talentOnly = new File(['AuctionHelperLastExport = {}'], 'AuctionHelper.lua');
    await expect(processAuctionHelperFiles([talentOnly], 'eu')).rejects.toThrow(
      /AuctionHelper_Professions.lua is required/,
    );

    const file = new File([professionsSource], 'AuctionHelper_Professions.lua');
    const [eu, us] = await Promise.all([
      processAuctionHelperFiles([file], 'eu'),
      processAuctionHelperFiles([file], 'us'),
    ]);
    expect(eu.payload.characters[0]?.characterKey).not.toBe(us.payload.characters[0]?.characterKey);
  });

  it('omits reagents without a positive exported quantity and their dangling associations', async () => {
    const sourceWithoutQuantity = professionsSource.replace('["quantityRequired"] = 3,', '');
    const file = new File([sourceWithoutQuantity], 'AuctionHelper_Professions.lua');

    const preview = await processAuctionHelperFiles([file], 'eu');
    const recipe = preview.payload.characters[0]?.professions[0]?.recipes.find(
      (candidate) => candidate.recipeId === 450216,
    );

    expect(recipe?.reagentSlots).toEqual([]);
    expect(recipe?.maxQualityRequiredReagents).toEqual([]);
  });

  it('omits addon quality zero from unranked reagents', async () => {
    const sourceWithUnrankedReagent = professionsSource.replace(
      '{ ["itemID"] = 210221, ["quality"] = 1 }',
      '{ ["itemID"] = 210221, ["quality"] = 0 }',
    );
    const file = new File([sourceWithUnrankedReagent], 'AuctionHelper_Professions.lua');

    const preview = await processAuctionHelperFiles([file], 'eu');
    const recipe = preview.payload.characters[0]?.professions[0]?.recipes.find(
      (candidate) => candidate.recipeId === 450216,
    );
    const unrankedReagent = recipe?.reagentSlots[0]?.reagents.find(
      (reagent) => reagent.itemId === 210221,
    );

    expect(unrankedReagent).toEqual({ itemId: 210221, quantity: 3 });
  });
});
