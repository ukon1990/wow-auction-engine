import { AuctionMarketSearchRow } from '@api/generated';
import { copperToCurrencyAmount, MarketItemRow } from '@ui';
import { nonemptyName, toOptionalFiniteNumber, toQuality } from '@core/utils/filter';

export const toMarketRow = (row: AuctionMarketSearchRow): MarketItemRow => {
  const listingPriceCopper = firstDefinedNumber(
    row.listingPrice,
    row.selectedRealm?.price,
    row.commodity?.price,
  );
  const listingQuantity = firstDefinedNumber(
    row.listingQuantity,
    row.selectedRealm?.quantity,
    row.commodity?.quantity,
  );
  const p25PriceCopper = firstDefinedNumber(row.selectedRealm?.p25Price, row.commodity?.p25Price);
  const p75PriceCopper = firstDefinedNumber(row.selectedRealm?.p75Price, row.commodity?.p75Price);
  const mergedCurrency = copperToCurrencyAmount(listingPriceCopper);
  const preferredScope = readPreferredScope(row);
  const isCommodity = row.isCommodity ?? preferredScope === 'commodity';
  return createMarketRow(row, {
    listingPriceCopper,
    listingQuantity,
    p25PriceCopper,
    p75PriceCopper,
    preferredScope,
    isCommodity,
    mergedCurrency,
  });
};

interface MarketRowValues {
  readonly listingPriceCopper: number | null | undefined;
  readonly listingQuantity: number | null | undefined;
  readonly p25PriceCopper: number | null | undefined;
  readonly p75PriceCopper: number | null | undefined;
  readonly preferredScope: 'realm' | 'commodity' | undefined;
  readonly isCommodity: boolean;
  readonly mergedCurrency: MarketItemRow['minBuyout'];
}

function createMarketRow(row: AuctionMarketSearchRow, values: MarketRowValues): MarketItemRow {
  return {
    ...createIdentityFields(row),
    ...createPriceFields(values),
    ...createMarketMetrics(row, values),
  };
}

function createIdentityFields(
  row: AuctionMarketSearchRow,
): Pick<
  MarketItemRow,
  | 'id'
  | 'name'
  | 'listingKey'
  | 'itemClassName'
  | 'itemSubclassName'
  | 'recipeRank'
  | 'quality'
  | 'iconUrl'
> {
  return {
    id: String(row.item.id),
    name: row.item.name,
    listingKey: row.listingKey
      ? {
          bonusKey: row.listingKey.bonusKey,
          modifierKey: row.listingKey.modifierKey,
          petSpeciesId: row.listingKey.petSpeciesId,
        }
      : undefined,
    itemClassName: nonemptyName(row.item.itemClass?.name),
    itemSubclassName: nonemptyName(row.item.itemSubclass?.name),
    recipeRank: row.item.recipe?.rank ?? undefined,
    quality: toQuality(row.item.quality?.type ?? row.item.quality?.name),
    iconUrl: row.item.mediaUrl ?? undefined,
  };
}

function createPriceFields(
  values: MarketRowValues,
): Pick<MarketItemRow, 'listingPriceCopper' | 'p25PriceCopper' | 'p75PriceCopper'> {
  return {
    listingPriceCopper: values.listingPriceCopper ?? undefined,
    p25PriceCopper: values.p25PriceCopper ?? undefined,
    p75PriceCopper: values.p75PriceCopper ?? undefined,
  };
}

function createMarketMetrics(
  row: AuctionMarketSearchRow,
  values: MarketRowValues,
): Omit<
  MarketItemRow,
  | 'id'
  | 'name'
  | 'listingKey'
  | 'itemClassName'
  | 'itemSubclassName'
  | 'recipeRank'
  | 'quality'
  | 'iconUrl'
  | 'listingPriceCopper'
  | 'p25PriceCopper'
  | 'p75PriceCopper'
> {
  return {
    preferredScope: values.preferredScope,
    isCommodity: values.isCommodity,
    minBuyout: values.mergedCurrency,
    marketValue: {},
    regionalAverage: values.mergedCurrency,
    saleRate: toOptionalFiniteNumber(row.saleRate) ?? null,
    soldPerDay: toOptionalFiniteNumber(row.soldPerDay) ?? null,
    selectedQuantity: values.listingQuantity ?? undefined,
  };
}

function firstDefinedNumber(...values: unknown[]): number | null | undefined {
  for (const value of values) {
    const number = toOptionalFiniteNumber(value);
    if (number !== null && number !== undefined) return number;
  }
  return undefined;
}

const readPreferredScope = (row: AuctionMarketSearchRow): 'realm' | 'commodity' | undefined => {
  const raw = row.preferredScope;
  return raw === 'commodity' || raw === 'realm' ? raw : undefined;
};
