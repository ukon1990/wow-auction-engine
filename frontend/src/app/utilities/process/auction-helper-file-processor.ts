import {
  NormalizedAuctionHelperProfessionData,
  NormalizedAuctionHelperRecipe,
  NormalizedAuctionHelperReagentSlot,
  NormalizedAuctionHelperTalents,
} from '@api/generated';

import { ProcessingDiagnostic } from './addon-lua-processor';
import {
  NormalizedProfessionSnapshot,
  NormalizedRecipe,
  NormalizedTalentExport,
  auctionHelperLuaProcessor,
} from './auction-helper-lua-adapters';
import { decodeAuctionHelperTalentExport } from './auction-helper-talent-decoder';

export type AuctionHelperLocalPreview = Readonly<{
  payload: NormalizedAuctionHelperProfessionData;
  diagnostics: readonly ProcessingDiagnostic[];
  charactersFound: number;
  professionsFound: number;
  recipesFound: number;
}>;

export async function processAuctionHelperFiles(
  files: readonly File[],
  region: string,
): Promise<AuctionHelperLocalPreview> {
  if (!files.some((file) => file.name.toLowerCase() === 'auctionhelper_professions.lua')) {
    throw new Error('AuctionHelper_Professions.lua is required to create a profession import.');
  }
  if (!region.trim()) throw new Error('Region is required.');
  const processed: Array<{ file: File; source: string; sha256: string }> = [];
  for (const file of files) {
    const bytes = await file.arrayBuffer();
    processed.push({
      file,
      source: new TextDecoder('utf-8', { fatal: true }).decode(bytes),
      sha256: await sha256(bytes),
    });
  }
  const results = processed.map(({ file, source }) =>
    auctionHelperLuaProcessor.process(file.name, source),
  );
  const professionResult = results.find(
    (result) => result.adapterId === 'auction-helper-professions-v1',
  );
  const snapshot = professionResult?.data as NormalizedProfessionSnapshot | undefined;
  const characters = snapshot?.characters ?? [];
  const hasEmbeddedTalents = characters.some((character) =>
    character.professions.some((profession) => profession.talents !== null),
  );
  const diagnostics = results.flatMap((result) =>
    hasEmbeddedTalents
      ? result.diagnostics.filter((diagnostic) => diagnostic.code !== 'TALENT_EXPORT_MISSING')
      : result.diagnostics,
  );
  const talentResult = results.find((result) => result.adapterId === 'auction-helper-export-v1');
  let decodedTalents: ReturnType<typeof decodeAuctionHelperTalentExport> | null = null;
  if (talentResult) {
    const talent = talentResult.data as NormalizedTalentExport;
    if (talent.encodedPayload) {
      try {
        decodedTalents = decodeAuctionHelperTalentExport(talent.encodedPayload, talent.scope);
      } catch (cause) {
        diagnostics.push({
          code:
            cause instanceof Error && cause.message.startsWith('Unsupported')
              ? 'TALENT_SCOPE_UNSUPPORTED'
              : 'TALENT_EXPORT_INVALID',
          detail: cause instanceof Error ? cause.message : 'Unable to decode talent export.',
          fileName: talentResult.fileName,
        });
      }
    }
  }
  const payload: NormalizedAuctionHelperProfessionData = {
    contractVersion: 1,
    source: {
      addon: 'AuctionHelper',
      ...(snapshot?.addonVersion ? { addonVersion: snapshot.addonVersion } : {}),
      processorVersion: '1',
      files: processed.map(({ file, sha256: hash }) => ({ fileName: file.name, sha256: hash })),
    },
    characters: characters.map((character) => ({
      characterKey: `${region.toLowerCase()}:${character.guid ?? `${character.name}-${character.realm}`}`,
      name: character.name,
      realm: character.realm,
      region: region.toLowerCase(),
      professions: character.professions.map((profession) => ({
        professionId: profession.skillLineId,
        skillLineId: profession.skillLineId,
        name: profession.name ?? String(profession.skillLineId),
        ...(profession.skillLevel !== null ? { skillLevel: profession.skillLevel } : {}),
        recipes: profession.recipes.map(toApiRecipe),
        ...(profession.talents
          ? { talents: profession.talents }
          : talentsForProfession(decodedTalents, profession.skillLineId)),
      })),
    })),
  };
  return {
    payload,
    diagnostics: aggregateDiagnostics(diagnostics),
    charactersFound: characters.length,
    professionsFound: characters.reduce(
      (total, character) => total + character.professions.length,
      0,
    ),
    recipesFound: characters.reduce(
      (total, character) =>
        total +
        character.professions.reduce(
          (professionTotal, profession) => professionTotal + profession.recipes.length,
          0,
        ),
      0,
    ),
  };
}

function aggregateDiagnostics(
  diagnostics: readonly ProcessingDiagnostic[],
): ProcessingDiagnostic[] {
  const groups = new Map<string, { first: ProcessingDiagnostic; count: number }>();
  diagnostics.forEach((diagnostic) => {
    const key = `${diagnostic.fileName}\u0000${diagnostic.code}\u0000${diagnostic.detail}`;
    const current = groups.get(key);
    if (current) current.count += 1;
    else groups.set(key, { first: diagnostic, count: 1 });
  });
  return [...groups.values()].slice(0, 100).map(({ first, count }) => ({
    ...first,
    ...(count > 1 ? { detail: `${first.detail} (${count} occurrences)` } : {}),
  }));
}

function toApiRecipe(recipe: NormalizedRecipe): NormalizedAuctionHelperRecipe {
  const slots = new Map<string, NormalizedAuctionHelperReagentSlot>();
  for (const reagent of recipe.reagents) {
    if (reagent.quantity === null || reagent.quantity <= 0) continue;
    const slotIndex = reagent.slotIndex ?? 0;
    const quantity = reagent.quantity;
    const key = `${slotIndex}:${reagent.dataSlotIndex ?? ''}:${reagent.slotType ?? ''}:${quantity}`;
    const slot = slots.get(key) ?? {
      slotIndex,
      ...(reagent.dataSlotIndex !== null ? { dataSlotIndex: reagent.dataSlotIndex } : {}),
      ...(reagent.slotType ? { slotType: reagent.slotType } : {}),
      quantity,
      reagents: [],
    };
    slot.reagents.push({
      itemId: reagent.itemId,
      ...(reagent.quality !== null ? { quality: reagent.quality } : {}),
      quantity,
    });
    slots.set(key, slot);
  }
  const reagentSlots = [...slots.values()];
  return {
    recipeId: recipe.recipeId,
    name: recipe.name ?? String(recipe.recipeId),
    learned: recipe.learned ?? false,
    ...(recipe.recipeType !== null ? { recipeType: recipe.recipeType } : {}),
    ...optionalBoolean('supportsQualities', recipe.supportsQualities),
    ...optionalBoolean('isGatheringRecipe', recipe.isGatheringRecipe),
    ...optionalBoolean('isEnchantingRecipe', recipe.isEnchantingRecipe),
    ...optionalBoolean('isSalvageRecipe', recipe.isSalvageRecipe),
    ...optionalBoolean('isRecraft', recipe.isRecraft),
    ...optionalBoolean('hasCraftingOperationInfo', recipe.hasCraftingOperationInfo),
    ...(recipe.craftedItemId !== null ? { craftedItemId: recipe.craftedItemId } : {}),
    qualityOutputItemIds: recipe.outputQualityItemIds.flatMap((output) =>
      output.quality === null ? [] : [{ quality: output.quality, itemId: output.itemId }],
    ),
    ...(recipe.crafting.baseDifficulty !== null
      ? { baseDifficulty: recipe.crafting.baseDifficulty }
      : {}),
    ...(recipe.crafting.baseSkill !== null ? { baseSkill: recipe.crafting.baseSkill } : {}),
    ...(recipe.crafting.bonusSkill !== null ? { bonusSkill: recipe.crafting.bonusSkill } : {}),
    ...(recipe.crafting.requiredReagentSkillDelta !== null
      ? { requiredReagentSkillDelta: recipe.crafting.requiredReagentSkillDelta }
      : {}),
    ...(recipe.crafting.lowerSkillThreshold !== null
      ? { lowerSkillThreshold: recipe.crafting.lowerSkillThreshold }
      : {}),
    ...(recipe.crafting.upperSkillThreshold !== null
      ? { upperSkillThreshold: recipe.crafting.upperSkillThreshold }
      : {}),
    qualityThresholds: [...recipe.crafting.qualityThresholds],
    reagentSlots,
    maxQualityRequiredReagents: recipe.maxQualityRequiredReagents
      .filter(
        (choice) =>
          choice.quantity > 0 &&
          reagentSlots.some(
            (slot) =>
              (choice.slotIndex === null || slot.slotIndex === choice.slotIndex) &&
              (choice.dataSlotIndex === null || slot.dataSlotIndex === choice.dataSlotIndex) &&
              slot.reagents.some((reagent) => reagent.itemId === choice.itemId),
          ),
      )
      .map((choice) => ({
        ...(choice.slotIndex !== null ? { slotIndex: choice.slotIndex } : {}),
        ...(choice.dataSlotIndex !== null ? { dataSlotIndex: choice.dataSlotIndex } : {}),
        itemId: choice.itemId,
        quantity: choice.quantity,
      })),
  };
}

function optionalBoolean<K extends string>(
  key: K,
  value: boolean | null,
): Partial<Record<K, boolean>> {
  return value === null ? {} : ({ [key]: value } as Record<K, boolean>);
}

function talentsForProfession(
  decoded: ReturnType<typeof decodeAuctionHelperTalentExport> | null,
  skillLineId: number,
): { talents?: NormalizedAuctionHelperTalents } {
  const profession = decoded?.professions.find(
    (candidate) => candidate.skillLineId === skillLineId,
  );
  if (!profession) return {};
  return {
    talents: {
      trees: profession.trees.map((tree) => ({
        treeId: tree.treeId,
        ...(tree.name ? { name: tree.name } : {}),
        nodes: tree.nodes.map((node) => ({
          nodeId: node.nodeId,
          ...(node.maxRanks !== null ? { maxRanks: node.maxRanks } : {}),
          entries: node.entries.map((entry) => ({
            entryId: entry.entryId,
            ...(entry.rankLimit !== null ? { rankLimit: entry.rankLimit } : {}),
          })),
        })),
      })),
      allocations: profession.allocations.map((allocation) => ({ ...allocation })),
    },
  };
}

async function sha256(bytes: ArrayBuffer): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, '0')).join('');
}
