import { AuctionMarketSearchRow } from '@api/generated';
import { copperToCurrencyAmount, MarketItemRow } from '@ui';
import { nonemptyName, toOptionalFiniteNumber, toQuality } from '@core/utils/filter';

export const toMarketRow = (row: AuctionMarketSearchRow): MarketItemRow => {
  const listingPriceCopper =
    toOptionalFiniteNumber(row.listingPrice) ??
    toOptionalFiniteNumber(row.selectedRealm?.price) ??
    toOptionalFiniteNumber(row.commodity?.price);
  const listingQuantity =
    toOptionalFiniteNumber(row.listingQuantity) ??
    toOptionalFiniteNumber(row.selectedRealm?.quantity) ??
    toOptionalFiniteNumber(row.commodity?.quantity);
  const p25PriceCopper =
    toOptionalFiniteNumber(row.selectedRealm?.p25Price) ??
    toOptionalFiniteNumber(row.commodity?.p25Price);
  const p75PriceCopper =
    toOptionalFiniteNumber(row.selectedRealm?.p75Price) ??
    toOptionalFiniteNumber(row.commodity?.p75Price);
  const mergedCurrency = copperToCurrencyAmount(listingPriceCopper);
  const preferredScope = readPreferredScope(row);
  const isCommodity = row.isCommodity ?? preferredScope === 'commodity';
  return {
    id: String(row.item.id),
    name: row.item.name,
    preferredScope,
    isCommodity,
    listingPriceCopper:
      listingPriceCopper !== null && listingPriceCopper !== undefined
        ? Number(listingPriceCopper)
        : undefined,
    p25PriceCopper:
      p25PriceCopper !== null && p25PriceCopper !== undefined ? Number(p25PriceCopper) : undefined,
    p75PriceCopper:
      p75PriceCopper !== null && p75PriceCopper !== undefined ? Number(p75PriceCopper) : undefined,
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
    minBuyout: mergedCurrency,
    marketValue: {},
    regionalAverage: mergedCurrency,
    saleRate: 0,
    selectedQuantity: listingQuantity ?? undefined,
  };
};

const readPreferredScope = (row: AuctionMarketSearchRow): 'realm' | 'commodity' | undefined => {
  const raw = row.preferredScope;
  return raw === 'commodity' || raw === 'realm' ? raw : undefined;
};
