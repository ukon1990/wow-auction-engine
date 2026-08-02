import { CraftingMarketSearchRow } from '@api/generated';
import { CraftingTableRow } from '@features/crafting/crafting-browser.models';
import { toOptionalFiniteNumber, toQuality } from '@core/utils/filter';

export function toCraftingRow(row: CraftingMarketSearchRow): CraftingTableRow {
  const listingKey = row.listingKey;
  const outputPrice = toOptionalFiniteNumber(row.outputPriceCopper) ?? null;
  return {
    ...craftingItemIdentity(row),
    ...craftingRecipeIdentity(row),
    ...craftingPrices(row, outputPrice),
    ...craftingMarketStats(row),
    rowId: row.rowId,
    recipeId: row.recipeId,
    variantSummary: variantSummary(listingKey),
    listingKey: {
      bonusKey: listingKey.bonusKey,
      modifierKey: listingKey.modifierKey,
      petSpeciesId: listingKey.petSpeciesId,
    },
  };
}

function craftingItemIdentity(row: CraftingMarketSearchRow) {
  return {
    craftedItemId: row.item?.id ?? 0,
    craftedItemName: row.item?.name ?? '',
    quality: toQuality(row.item?.quality?.type ?? row.item?.quality?.name),
    iconUrl: row.item?.mediaUrl ?? undefined,
  };
}

function craftingRecipeIdentity(row: CraftingMarketSearchRow) {
  return {
    recipeName: row.recipe?.name ?? '',
    recipeRank: row.recipe?.rank ?? null,
    professionName: row.professionName ?? '—',
  };
}

function craftingPrices(row: CraftingMarketSearchRow, outputPrice: number | null) {
  return {
    outputPriceCopper: outputPrice,
    outputP25PriceCopper: toOptionalFiniteNumber(row.outputP25PriceCopper) ?? null,
    outputP75PriceCopper: toOptionalFiniteNumber(row.outputP75PriceCopper) ?? null,
    reagentCostCopper: toOptionalFiniteNumber(row.reagentCostCopper) ?? null,
    profitCopper: toOptionalFiniteNumber(row.profitCopper) ?? null,
    minBuyoutCopper: outputPrice,
  };
}

function craftingMarketStats(row: CraftingMarketSearchRow) {
  return {
    roiPercent: row.roiPercent ?? null,
    outputPriceChangePercent: row.outputPriceChangePercent ?? null,
    listingQuantity: row.listingQuantity != null ? Number(row.listingQuantity) : null,
    profileFit: row.profileFit ?? null,
    saleRate: toOptionalFiniteNumber(row.saleRate) ?? null,
    soldPerDay: toOptionalFiniteNumber(row.soldPerDay) ?? null,
  };
}

function variantSummary(listingKey: {
  bonusKey: string;
  modifierKey: string;
  petSpeciesId: number;
}): string {
  const parts: string[] = [];
  if (listingKey.bonusKey?.trim()) parts.push(`B:${truncate(listingKey.bonusKey, 24)}`);
  if (listingKey.modifierKey?.trim()) parts.push(`M:${truncate(listingKey.modifierKey, 12)}`);
  if (listingKey.petSpeciesId > 0) parts.push(`Pet ${listingKey.petSpeciesId}`);
  return parts.join(' · ');
}

function truncate(value: string, max: number): string {
  return value.length <= max ? value : `${value.slice(0, max - 1)}…`;
}
