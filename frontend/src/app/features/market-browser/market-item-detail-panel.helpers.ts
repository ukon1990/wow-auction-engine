import {
  copperToCurrencyAmount,
  formatCopperCurrency,
  type ChartSeries,
  type PaginationState,
  type TooltipRow,
} from '@ui';
import type {
  AuctionMarketItemCurrentListing,
  AuctionMarketItemDetailPoint,
  AuctionMarketItemDetailResponse,
  AuctionMarketItemDetailSummary,
  AuctionMarketItemHourlyPoint,
} from '@api/generated';
import type Highcharts from 'highcharts/esm/highcharts';
import type {
  ItemDetailScope,
  ItemDetailVariantParams,
} from '@core/services/market-item-detail.service';

import {
  priceChangeCaptionStatic,
  quantityAxisLabel,
  type RegionCode,
  variantEqual,
} from './market-item-detail.helpers';

export interface MarketDetailPanelContext {
  readonly region: RegionCode;
  readonly realmSlug: string;
  readonly itemId: number;
  readonly variant: ItemDetailVariantParams;
  readonly initialScope: ItemDetailScope;
  readonly recipeId: number | null;
}

export function marketDetailPanelContextsEqual(
  previousContext: MarketDetailPanelContext,
  nextContext: MarketDetailPanelContext,
): boolean {
  return (
    previousContext.region === nextContext.region &&
    previousContext.realmSlug === nextContext.realmSlug &&
    previousContext.itemId === nextContext.itemId &&
    previousContext.initialScope === nextContext.initialScope &&
    previousContext.recipeId === nextContext.recipeId &&
    variantEqual(previousContext.variant, nextContext.variant)
  );
}

export function activeScopePrice(
  summary: AuctionMarketItemDetailSummary,
  scope: ItemDetailScope,
): number | null | undefined {
  return scope === 'commodity' ? summary.commodityPrice : summary.selectedRealmPrice;
}

export function activeScopeQuantity(
  summary: AuctionMarketItemDetailSummary,
  scope: ItemDetailScope,
): number | null | undefined {
  return scope === 'commodity' ? summary.commodityQuantity : summary.selectedRealmQuantity;
}

export function activeScopePriceCaption(
  summary: AuctionMarketItemDetailSummary,
  scope: ItemDetailScope,
): string {
  const changePercent =
    scope === 'commodity'
      ? summary.commodityPriceChangePercent
      : summary.selectedRealmPriceChangePercent;
  return priceChangeCaptionStatic(changePercent);
}

export function currentListingsPagination(
  totalItems: number,
  requestedPage: number,
  pageSize: number,
): PaginationState {
  const totalPages = Math.ceil(totalItems / pageSize);
  return {
    page: totalPages > 0 ? Math.min(requestedPage, totalPages - 1) : 0,
    pageSize,
    totalItems,
    totalPages,
  };
}

export function pagedCurrentListings(
  listings: readonly AuctionMarketItemCurrentListing[],
  pagination: PaginationState,
): readonly AuctionMarketItemCurrentListing[] {
  const start = pagination.page * pagination.pageSize;
  return listings.slice(start, start + pagination.pageSize);
}

export function marketDetailChartOptions(
  series: readonly ChartSeries[],
  locale: string,
  labels: { readonly xLabelAt?: (index: number) => string } = {},
): Highcharts.Options {
  const yScaleKeys = [...new Set(series.map((chartSeries) => chartSeries.yScaleKey))];
  return {
    xAxis: labels.xLabelAt
      ? {
          labels: {
            formatter: (context) =>
              labels.xLabelAt?.(axisIndex(context.value)) || context.text || '',
          },
        }
      : undefined,
    yAxis: yScaleKeys.map((key) => ({
      min: key === 'quantity' ? 0 : undefined,
      labels: {
        formatter: (context) => axisLabel(key, context.value, context.text, locale),
      },
    })),
  };
}

function axisLabel(
  key: string,
  value: number | string,
  fallback: string | undefined,
  locale: string,
): string {
  if (key === 'price' || key === 'profit') {
    return formatCopperCurrency(axisNumber(value));
  }
  if (key === 'quantity') {
    return quantityAxisLabel(axisNumber(value), locale);
  }
  if (key === 'roi') {
    return `${axisNumber(value).toLocaleString(locale)}%`;
  }
  return fallback ?? String(value);
}

function axisNumber(value: number | string): number {
  return typeof value === 'number' ? value : Number(value);
}

function axisIndex(value: number | string): number {
  return Math.round(axisNumber(value));
}

export function dailyTooltipRows(
  point: AuctionMarketItemDetailPoint | undefined,
  numberDisplay: (value: number | null | undefined) => string,
): TooltipRow[] {
  if (!point) return [];
  return [
    { label: $localize`:@@itemDetail.tooltip.date:date`, value: point.statDate ?? '—' },
    {
      label: $localize`:@@itemDetail.tooltip.avgQuantity:avg quantity`,
      value: numberDisplay(point.avgQuantity),
    },
    {
      label: $localize`:@@itemDetail.tooltip.minQuantity:min quantity`,
      value: numberDisplay(point.minQuantity),
    },
    {
      label: $localize`:@@itemDetail.tooltip.maxQuantity:max quantity`,
      value: numberDisplay(point.maxQuantity),
    },
    {
      label: $localize`:@@itemDetail.tooltip.minPrice:min price`,
      amount: copperToCurrencyAmount(point.minPrice),
    },
    {
      label: $localize`:@@itemDetail.tooltip.p25Price:p25 price`,
      amount: copperToCurrencyAmount(point.p25Price),
    },
    {
      label: $localize`:@@itemDetail.tooltip.avgPrice:avg price`,
      amount: copperToCurrencyAmount(point.avgPrice),
    },
    {
      label: $localize`:@@itemDetail.tooltip.p75Price:p75 price`,
      amount: copperToCurrencyAmount(point.p75Price),
    },
    {
      label: $localize`:@@itemDetail.tooltip.maxPrice:max price`,
      amount: copperToCurrencyAmount(point.maxPrice),
    },
  ];
}

export function hourlyTooltipRows(
  point: AuctionMarketItemHourlyPoint | undefined,
  numberDisplay: (value: number | null | undefined) => string,
): TooltipRow[] {
  if (!point) return [];
  return [
    {
      label: $localize`:@@itemDetail.tooltip.hour:hour`,
      value: `${String(point.hourOfDay).padStart(2, '0')}:00`,
    },
    {
      label: $localize`:@@itemDetail.tooltip.timestamp:timestamp`,
      value: point.timestamp ?? '—',
    },
    {
      label: $localize`:@@itemDetail.tooltip.quantityPerHour:quantity / hour`,
      value: numberDisplay(point.totalQuantity),
    },
    {
      label: $localize`:@@itemDetail.tooltip.minPrice:min price`,
      amount: copperToCurrencyAmount(point.minPrice),
    },
    {
      label: $localize`:@@itemDetail.tooltip.avgPrice:avg price`,
      amount: copperToCurrencyAmount(point.avgPrice),
    },
    {
      label: $localize`:@@itemDetail.tooltip.maxPrice:max price`,
      amount: copperToCurrencyAmount(point.maxPrice),
    },
  ];
}

export interface ItemInfoLabels {
  readonly quality: string;
  readonly itemClass: string;
  readonly itemSubclass: string;
  readonly expansion: string;
  readonly craftedBy: string;
  readonly reagentIn: string;
}

export function itemInfoRows(
  detail: AuctionMarketItemDetailResponse | null,
  labels: ItemInfoLabels,
  numberDisplay: (value: number | null | undefined) => string,
): ReadonlyArray<{ label: string; value: string }> {
  if (!detail) {
    return Object.values(labels).map((label) => ({ label, value: '—' }));
  }
  const item = detail.item;
  return [
    { label: labels.quality, value: item.quality?.name?.trim() || '—' },
    { label: labels.itemClass, value: item.itemClass?.name?.trim() || '—' },
    { label: labels.itemSubclass, value: item.itemSubclass?.name?.trim() || '—' },
    { label: labels.expansion, value: item.expansion?.name?.trim() || '—' },
    { label: labels.craftedBy, value: numberDisplay(detail.craftedByRecipeCount) },
    { label: labels.reagentIn, value: numberDisplay(detail.reagentInRecipeCount) },
  ];
}
