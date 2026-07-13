import {
  AddonLuaAdapter,
  AddonLuaProcessor,
  AddonLuaResult,
  ProcessingDiagnostic,
} from './addon-lua-processor';
import { LuaValue } from './lua-assignment-processor';

export type NormalizedReagent = Readonly<{
  itemId: number;
  quality: number | null;
  quantity: number | null;
  slotIndex: number | null;
  dataSlotIndex: number | null;
  slotType: string | null;
}>;

export type NormalizedRecipe = Readonly<{
  recipeId: number;
  name: string | null;
  learned: boolean | null;
  categoryId: number | null;
  recipeType: number | null;
  supportsQualities: boolean | null;
  isGatheringRecipe: boolean | null;
  isEnchantingRecipe: boolean | null;
  isSalvageRecipe: boolean | null;
  isRecraft: boolean | null;
  hasCraftingOperationInfo: boolean | null;
  craftedItemId: number | null;
  outputQualityItemIds: ReadonlyArray<{ quality: number | null; itemId: number }>;
  reagents: readonly NormalizedReagent[];
  maxQualityRequiredReagents: ReadonlyArray<{
    slotIndex: number | null;
    dataSlotIndex: number | null;
    itemId: number;
    quantity: number;
  }>;
  crafting: Readonly<{
    baseDifficulty: number | null;
    baseSkill: number | null;
    bonusSkill: number | null;
    requiredReagentSkillDelta: number | null;
    lowerSkillThreshold: number | null;
    upperSkillThreshold: number | null;
    qualityThresholds: readonly number[];
  }>;
}>;

export type NormalizedProfessionSnapshot = Readonly<{
  formatVersion: 1;
  addonVersion: string | null;
  characters: ReadonlyArray<{
    name: string;
    realm: string;
    guid: string | null;
    professions: ReadonlyArray<{
      skillLineId: number;
      name: string | null;
      skillLevel: number | null;
      talents: Readonly<{
        trees: Array<{
          treeId: number;
          name?: string;
          nodes: Array<{
            nodeId: number;
            maxRanks?: number;
            entries: Array<{ entryId: number; rankLimit?: number }>;
          }>;
        }>;
        allocations: Array<{ nodeId: number; entryId: number; rank: number }>;
      }> | null;
      recipes: readonly NormalizedRecipe[];
    }>;
  }>;
}>;

export type NormalizedTalentExport = Readonly<{
  formatVersion: 1;
  exportFormat: string | null;
  scope: string | null;
  generatedAt: string | null;
  encodedPayload: string | null;
}>;

const professionsAdapter: AddonLuaAdapter<NormalizedProfessionSnapshot> = {
  id: 'auction-helper-professions-v1',
  fileNames: ['AuctionHelper_Professions.lua'],
  assignmentNames: ['AuctionHelperProfessionsDB'],
  canProcess: (_fileName, assignments) => isRecord(assignments['AuctionHelperProfessionsDB']),
  normalize(fileName, assignments) {
    const root = record(assignments['AuctionHelperProfessionsDB']);
    const diagnostics: ProcessingDiagnostic[] = [];
    const characters = values(root['characters'])
      .map(record)
      .map((character) => normalizeCharacter(character, fileName, diagnostics))
      .filter((value): value is NonNullable<typeof value> => value !== null);
    return {
      adapterId: this.id,
      fileName,
      diagnostics,
      data: {
        formatVersion: 1,
        addonVersion: text(root['addonVersion']),
        characters,
      },
    };
  },
};

const exportAdapter: AddonLuaAdapter<NormalizedTalentExport> = {
  id: 'auction-helper-export-v1',
  fileNames: ['AuctionHelper.lua'],
  assignmentNames: ['AuctionHelperLastExport'],
  canProcess: (_fileName, assignments) => isRecord(assignments['AuctionHelperLastExport']),
  normalize(fileName, assignments) {
    const root = record(assignments['AuctionHelperLastExport']);
    const payload = text(root['payload']);
    return {
      adapterId: this.id,
      fileName,
      diagnostics: payload?.startsWith('AHCBOR1:')
        ? []
        : [
            {
              code: 'TALENT_EXPORT_MISSING',
              detail: 'AuctionHelperLastExport does not contain an AHCBOR1 payload.',
              fileName,
            },
          ],
      data: {
        formatVersion: 1,
        exportFormat: payload?.includes(':') ? payload.slice(0, payload.indexOf(':')) : null,
        scope: text(root['scope']),
        generatedAt: text(root['generatedAt']),
        encodedPayload: payload,
      },
    };
  },
};

export const auctionHelperLuaProcessor = new AddonLuaProcessor([professionsAdapter, exportAdapter]);

function normalizeCharacter(
  source: Record<string, LuaValue>,
  fileName: string,
  diagnostics: ProcessingDiagnostic[],
): NormalizedProfessionSnapshot['characters'][number] | null {
  const meta = record(source['meta']);
  const name = text(meta['name']);
  const realm = text(meta['realm']);
  if (!name || !realm) return null;
  const professions = values(source['professions'])
    .map(record)
    .map((profession) => normalizeProfession(profession, fileName, diagnostics))
    .filter((value): value is NonNullable<typeof value> => value !== null);
  return { name, realm, guid: text(meta['guid']), professions };
}

function normalizeProfession(
  source: Record<string, LuaValue>,
  fileName: string,
  diagnostics: ProcessingDiagnostic[],
): NormalizedProfessionSnapshot['characters'][number]['professions'][number] | null {
  const skillLineId = integer(source['skillLineID']);
  if (skillLineId === null) return null;
  return {
    skillLineId,
    name: text(source['professionName']) ?? text(source['currentLevelName']),
    skillLevel: integer(source['skillLevel']),
    talents: normalizeEmbeddedTalents(source),
    recipes: entries(source['recipes'])
      .map(([recipeKey, recipe]) =>
        normalizeRecipe(record(recipe), recipeKey, fileName, diagnostics),
      )
      .filter((value): value is NormalizedRecipe => value !== null),
  };
}

function normalizeRecipe(
  source: Record<string, LuaValue>,
  recipeKey: string,
  fileName: string,
  diagnostics: ProcessingDiagnostic[],
): NormalizedRecipe | null {
  const info = record(source['info']);
  const recipeId =
    integer(source['skillLineAbilityID']) ??
    integer(info['skillLineAbilityID']) ??
    integerValue(recipeKey) ??
    integer(info['recipeID']);
  if (recipeId === null) return null;
  const outputs = record(source['outputs']);
  const schematic = record(source['schematic']);
  const outputQualityItemIds = values(outputs['qualityVariants'])
    .map(record)
    .map((variant) => ({
      quality: craftingQuality(variant['qualityIndex']) ?? craftingQuality(variant['quality']),
      itemId: integer(variant['itemID']),
    }))
    .filter(
      (variant): variant is { quality: number | null; itemId: number } => variant.itemId !== null,
    );
  const craftedItemId =
    integer(schematic['outputItemID']) ??
    integer(outputs['itemID']) ??
    integer(record(outputs['raw'])['itemID']) ??
    outputQualityItemIds[0]?.itemId ??
    null;
  const supportsQualities = boolean(info['supportsQualities']);
  const hasCraftingOperationInfo = boolean(schematic['hasCraftingOperationInfo']);
  const isGatheringRecipe = boolean(info['isGatheringRecipe']);
  const isEnchantingRecipe = boolean(info['isEnchantingRecipe']);
  const isSalvageRecipe = boolean(info['isSalvageRecipe']);
  const expectsItemOutput =
    isGatheringRecipe !== true &&
    isEnchantingRecipe !== true &&
    isSalvageRecipe !== true &&
    (boolean(info['hasSingleItemOutput']) === true || supportsQualities === true);
  if (craftedItemId === null && expectsItemOutput) {
    diagnostics.push({
      code: 'CRAFTED_ITEM_MISSING',
      detail: 'No crafted item ID was exported for this recipe; no association was inferred.',
      fileName,
      recipeId,
    });
  }
  const crafting = record(source['crafting']);
  const base = record(crafting['base']);
  const qualityThresholds = values(crafting['qualityThresholds'])
    .map(number)
    .filter((value): value is number => value !== null);
  const skillValues = {
    baseDifficulty: integer(crafting['baseDifficulty']) ?? integer(base['baseDifficulty']),
    baseSkill: integer(crafting['baseSkill']) ?? integer(base['baseSkill']),
    bonusSkill: integer(crafting['bonusSkill']) ?? integer(base['bonusSkill']),
    requiredReagentSkillDelta: integer(crafting['requiredReagentSkillDelta']),
    lowerSkillThreshold: integer(base['lowerSkillThreshold']),
    upperSkillThreshold:
      integer(base['upperSkillThreshold']) ?? integer(base['upperSkillTreshold']),
    qualityThresholds,
  };
  if (
    (supportsQualities === true || hasCraftingOperationInfo === true) &&
    skillValues.baseDifficulty === null &&
    skillValues.baseSkill === null &&
    skillValues.qualityThresholds.length === 0
  ) {
    diagnostics.push({
      code: 'CRAFTING_SKILL_DATA_MISSING',
      detail: 'No crafting difficulty or skill thresholds were exported for this recipe.',
      fileName,
      recipeId,
    });
  }
  return {
    recipeId,
    name: text(info['name']),
    learned: boolean(info['learned']),
    categoryId: integer(info['categoryID']),
    recipeType: integer(schematic['recipeType']) ?? integer(outputs['recipeType']),
    supportsQualities,
    isGatheringRecipe,
    isEnchantingRecipe,
    isSalvageRecipe,
    isRecraft: boolean(info['isRecraft']) ?? boolean(schematic['isRecraft']),
    hasCraftingOperationInfo,
    craftedItemId,
    outputQualityItemIds,
    reagents: normalizeReagents(schematic, values(source['slots'])),
    maxQualityRequiredReagents: values(crafting['maxQualityRequiredReagents'])
      .map(record)
      .map((choice) => ({
        slotIndex: integer(choice['slotIndex']),
        dataSlotIndex: integer(choice['dataSlotIndex']),
        itemId: integer(record(choice['reagent'])['itemID']),
        quantity: integer(choice['quantity']),
      }))
      .filter(
        (
          choice,
        ): choice is {
          slotIndex: number | null;
          dataSlotIndex: number | null;
          itemId: number;
          quantity: number;
        } => choice.itemId !== null && choice.quantity !== null,
      ),
    crafting: skillValues,
  };
}

function normalizeEmbeddedTalents(
  profession: Record<string, LuaValue>,
): NormalizedProfessionSnapshot['characters'][number]['professions'][number]['talents'] {
  const specializationTrees = values(profession['specializationTrees']).map(record);
  const primaryTree = record(profession['specializationTree']);
  if (specializationTrees.length === 0 && Object.keys(primaryTree).length > 0) {
    specializationTrees.push(primaryTree);
  }
  const allocations: Array<{ nodeId: number; entryId: number; rank: number }> = [];
  const trees = specializationTrees.flatMap((specialization) =>
    values(specialization['tabs']).flatMap((tabValue) => {
      const tab = record(tabValue);
      const treeId = integer(tab['treeID']);
      if (treeId === null) return [];
      const nodes = values(tab['nodes']).flatMap((nodeValue) => {
        const node = record(nodeValue);
        const nodeInfo = record(node['nodeInfo']);
        const nodeId = integer(node['nodeID']);
        if (nodeId === null) return [];
        const activeEntry = record(nodeInfo['activeEntry']);
        const entryId = integer(activeEntry['entryID']) ?? integer(nodeInfo['activeEntryID']);
        const rank = integer(activeEntry['rank']) ?? integer(nodeInfo['currentRank']);
        if (entryId !== null && rank !== null) allocations.push({ nodeId, entryId, rank });
        const maxRanks = integer(nodeInfo['maxRanks']) ?? integer(nodeInfo['totalMaxRanks']);
        return [
          {
            nodeId,
            ...(maxRanks !== null ? { maxRanks } : {}),
            entries: values(node['entries']).flatMap((entryValue) => {
              const entry = record(entryValue);
              const normalizedEntryId = integer(entry['entryID']);
              if (normalizedEntryId === null) return [];
              const rankLimit =
                integer(record(entry['entryInfo'])['maxRanks']) ??
                integer(record(entry['definitionInfo'])['maxRanks']);
              return [
                {
                  entryId: normalizedEntryId,
                  ...(rankLimit !== null ? { rankLimit } : {}),
                },
              ];
            }),
          },
        ];
      });
      const name = text(record(tab['tabInfo'])['name']);
      return [{ treeId, ...(name ? { name } : {}), nodes }];
    }),
  );
  return trees.length > 0 ? { trees, allocations } : null;
}

function normalizeReagents(
  schematic: Record<string, LuaValue>,
  enrichedSlots: LuaValue[],
): NormalizedReagent[] {
  const result: NormalizedReagent[] = [];
  const qualityByItemId = new Map<number, number>();
  enrichedSlots
    .flatMap((slot) => values(record(slot)['reagents']))
    .forEach((reagentValue) => {
      const reagent = record(reagentValue);
      const itemId = integer(reagent['itemID']);
      const quality = craftingQuality(reagent['quality']);
      if (itemId !== null && quality !== null) qualityByItemId.set(itemId, quality);
    });
  values(schematic['reagentSlotSchematics']).forEach((slotValue, slotOffset) => {
    const slot = record(slotValue);
    values(slot['reagents']).forEach((reagentValue) => {
      const reagent = record(reagentValue);
      const itemId = integer(reagent['itemID']);
      if (itemId === null) return;
      result.push({
        itemId,
        quality: craftingQuality(reagent['quality']) ?? qualityByItemId.get(itemId) ?? null,
        quantity: integer(reagent['quantityRequired']) ?? integer(slot['quantityRequired']),
        slotIndex: integer(slot['slotIndex']) ?? slotOffset + 1,
        dataSlotIndex: integer(slot['dataSlotIndex']),
        slotType:
          text(slot['reagentType']) ??
          text(slot['slotType']) ??
          number(slot['reagentType'])?.toString() ??
          number(slot['dataSlotType'])?.toString() ??
          null,
      });
    });
  });
  return result;
}

function values(value: LuaValue | undefined): LuaValue[] {
  if (Array.isArray(value)) return value;
  return isRecord(value) ? Object.values(value) : [];
}

function entries(value: LuaValue | undefined): Array<[string, LuaValue]> {
  if (Array.isArray(value)) return value.map((item, index) => [String(index + 1), item]);
  return isRecord(value) ? Object.entries(value) : [];
}

function record(value: LuaValue | undefined): Record<string, LuaValue> {
  return isRecord(value) ? value : {};
}

function isRecord(value: LuaValue | undefined): value is Record<string, LuaValue> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function text(value: LuaValue | undefined): string | null {
  return typeof value === 'string' ? value : null;
}

function number(value: LuaValue | undefined): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function integer(value: LuaValue | undefined): number | null {
  const result = number(value);
  return result !== null && Number.isInteger(result) ? result : null;
}

function integerValue(value: string): number | null {
  const result = Number(value);
  return Number.isInteger(result) ? result : null;
}

function craftingQuality(value: LuaValue | undefined): number | null {
  const result = integer(value);
  return result !== null && result >= 1 && result <= 10 ? result : null;
}

function boolean(value: LuaValue | undefined): boolean | null {
  return typeof value === 'boolean' ? value : null;
}

export type AuctionHelperProcessingResult =
  AddonLuaResult<NormalizedProfessionSnapshot> | AddonLuaResult<NormalizedTalentExport>;
