import { CraftingMarketSearchRow } from '@api/generated';
import { CraftingTableRow } from '@features/crafting/crafting-browser.models';
import { toOptionalFiniteNumber, toQuality } from '@core/utils/filter';

export function toCraftingRow(row: CraftingMarketSearchRow): CraftingTableRow {
  const lk = row.listingKey;
  const outPrice = toOptionalFiniteNumber(row.outputPriceCopper) ?? null;
  const minBuy = outPrice;
  return {
    rowId: row.rowId,
    recipeId: row.recipeId,
    craftedItemId: row.item?.id ?? 0,
    craftedItemName: row.item?.name ?? '',
    recipeName: row.recipe?.name ?? '',
    professionName: row.professionName ?? '—',
    variantSummary: variantSummary(lk),
    listingKey: {
      bonusKey: lk.bonusKey,
      modifierKey: lk.modifierKey,
      petSpeciesId: lk.petSpeciesId,
    },
    quality: toQuality(row.item?.quality?.type ?? row.item?.quality?.name),
    iconUrl: row.item?.mediaUrl ?? undefined,
    outputPriceCopper: outPrice,
    outputP25PriceCopper: toOptionalFiniteNumber(row.outputP25PriceCopper) ?? null,
    outputP75PriceCopper: toOptionalFiniteNumber(row.outputP75PriceCopper) ?? null,
    reagentCostCopper: toOptionalFiniteNumber(row.reagentCostCopper) ?? null,
    profitCopper: toOptionalFiniteNumber(row.profitCopper) ?? null,
    roiPercent: row.roiPercent ?? null,
    outputPriceChangePercent: row.outputPriceChangePercent ?? null,
    listingQuantity: row.listingQuantity != null ? Number(row.listingQuantity) : null,
    minBuyoutCopper: minBuy,
  };
}

function variantSummary(lk: {
  bonusKey: string;
  modifierKey: string;
  petSpeciesId: number;
}): string {
  const parts: string[] = [];
  if (lk.bonusKey?.trim()) parts.push(`B:${truncate(lk.bonusKey, 24)}`);
  if (lk.modifierKey?.trim()) parts.push(`M:${truncate(lk.modifierKey, 12)}`);
  if (lk.petSpeciesId) parts.push(`Pet ${lk.petSpeciesId}`);
  return parts.length ? parts.join(' · ') : 'Default';
}

function truncate(s: string, max: number): string {
  return s.length <= max ? s : `${s.slice(0, max - 1)}…`;
}
