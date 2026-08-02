import { ActivatedRoute, ParamMap } from '@angular/router';
import { formatCopperCurrency, type ChartPoint, type ChartSeries, type HeatmapCell } from '@ui';
import type {
  AuctionMarketItemCraftingAnalyticsResponse,
  AuctionMarketItemDetailPoint,
  AuctionMarketItemDetailResponse,
  AuctionMarketItemDetailSummary,
  AuctionMarketItemHourlyPoint,
} from '@api/generated';
import type {
  ItemDetailScope,
  ItemDetailVariantParams,
} from '@core/services/market-item-detail.service';

export type RegionCode = 'us' | 'eu' | 'kr' | 'tw';

export function realmAncestorRoute(route: ActivatedRoute): ActivatedRoute {
  let ancestorRoute: ActivatedRoute | null = route;
  while (ancestorRoute) {
    const parameters = ancestorRoute.snapshot.paramMap;
    if (parameters.has('region') && parameters.has('realm')) {
      return ancestorRoute;
    }
    ancestorRoute = ancestorRoute.parent;
  }
  return route;
}

export function variantFromQuery(queryParameters: ParamMap): ItemDetailVariantParams {
  return {
    bonusKey: queryParameters.get('bonusKey') ?? '',
    modifierKey: queryParameters.get('modifierKey') ?? '',
    petSpeciesId: Number(queryParameters.get('petSpeciesId') ?? 0) || 0,
  };
}

export function variantEqual(
  firstVariant: ItemDetailVariantParams,
  secondVariant: ItemDetailVariantParams,
): boolean {
  return (
    firstVariant.bonusKey === secondVariant.bonusKey &&
    firstVariant.modifierKey === secondVariant.modifierKey &&
    firstVariant.petSpeciesId === secondVariant.petSpeciesId
  );
}

export function scopeFromQuery(queryParameters: ParamMap): ItemDetailScope {
  return queryParameters.get('scope') === 'commodity' ? 'commodity' : 'realm';
}

export function isRegion(value: string | null | undefined): value is RegionCode {
  return value === 'us' || value === 'eu' || value === 'kr' || value === 'tw';
}

export function formatRealmLabel(slug: string): string {
  const label = slug.replace(/-/g, ' ');
  return label.length ? label.charAt(0).toUpperCase() + label.slice(1) : slug;
}

export function showChartScopeToggleFn(detail: AuctionMarketItemDetailResponse): boolean {
  return !detail.regionalMetricsRedundant && hasRealmScopeMetrics(detail.summary);
}

export function shouldUseCommodityScopeByDefault(detail: AuctionMarketItemDetailResponse): boolean {
  return detail.regionalMetricsRedundant || !hasRealmScopeMetrics(detail.summary);
}

export function shouldFallbackToCommodityFetch(detail: AuctionMarketItemDetailResponse): boolean {
  return (
    !detail.regionalMetricsRedundant &&
    !hasRealmScopeMetrics(detail.summary) &&
    !hasCommodityScopeMetrics(detail.summary)
  );
}

export function hasRealmScopeMetrics(summary: AuctionMarketItemDetailSummary): boolean {
  const realmPrice = summary.selectedRealmPrice;
  const realmQty = summary.selectedRealmQuantity;
  return (
    (realmPrice != null && Number.isFinite(realmPrice)) ||
    (realmQty != null && Number.isFinite(realmQty))
  );
}

export function hasCommodityScopeMetrics(summary: AuctionMarketItemDetailSummary): boolean {
  const commodityPrice = summary.commodityPrice;
  const commodityQty = summary.commodityQuantity;
  return (
    (commodityPrice != null && Number.isFinite(commodityPrice)) ||
    (commodityQty != null && Number.isFinite(commodityQty))
  );
}

export function priceChangeCaptionStatic(pct: number | null | undefined): string {
  if (pct == null || !Number.isFinite(pct)) return '';
  const sign = pct > 0 ? '+' : '';
  return $localize`:@@itemDetail.vsPriorDay:${sign}${pct.toFixed(1)}% vs prior day`;
}

export function dayOfMonthLabel(value: string | null | undefined): string {
  if (!value) return '';
  const dateOnly = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);
  if (dateOnly?.[3]) {
    return String(Number(dateOnly[3]));
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '' : String(date.getUTCDate());
}

export function hourOfDayLabel(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) return '';
  return `${String(Math.max(0, Math.min(23, Math.round(value)))).padStart(2, '0')}:00`;
}

export function quantityAxisLabel(value: number | null | undefined, locale = 'en-US'): string {
  if (value == null || !Number.isFinite(value)) return '—';
  const notation = Math.abs(value) >= 10_000 ? 'compact' : 'standard';
  return new Intl.NumberFormat(locale, {
    maximumFractionDigits: notation === 'compact' || !Number.isInteger(value) ? 1 : 0,
    notation,
  }).format(value);
}

export function mergeCommodityScope(
  existing: AuctionMarketItemDetailResponse | null,
  commodity: AuctionMarketItemDetailResponse,
): AuctionMarketItemDetailResponse {
  if (!existing) return commodity;
  return {
    ...existing,
    regionalMetricsRedundant: commodity.regionalMetricsRedundant,
    commodity: commodity.commodity,
    summary: {
      ...existing.summary,
      commodityPrice: commodity.summary.commodityPrice,
      commodityQuantity: commodity.summary.commodityQuantity,
      commodityPriceChangePercent: commodity.summary.commodityPriceChangePercent,
      realmVsCommodityPricePercent: commodity.summary.realmVsCommodityPricePercent,
    },
    dailySeriesCommodity: commodity.dailySeriesCommodity,
    hourlySeriesCommodity: commodity.hourlySeriesCommodity,
    quantityPieCommodity: commodity.quantityPieCommodity,
    currentListings: commodity.currentListings,
    marketDataSources:
      commodity.marketDataSources.length > 0
        ? commodity.marketDataSources
        : existing.marketDataSources,
  };
}

export function dailyPointsToChartSeries(
  rows: readonly AuctionMarketItemDetailPoint[],
): ChartSeries[] {
  if (rows.length === 0) return [];

  const quantityPoints: ChartPoint[] = [];
  const lowerPoints: ChartPoint[] = [];
  const middlePoints: ChartPoint[] = [];
  const upperPoints: ChartPoint[] = [];

  rows.forEach((row, index) => {
    const points = dailyChartPoints(row, index);
    quantityPoints.push(points.quantity);
    if (points.lower) lowerPoints.push(points.lower);
    if (points.middle) middlePoints.push(points.middle);
    if (points.upper) upperPoints.push(points.upper);
  });

  if (lowerPoints.length === 0 && middlePoints.length === 0 && upperPoints.length === 0) {
    return quantitySeries(quantityPoints);
  }

  const series = quantitySeries(quantityPoints);

  if (lowerPoints.length > 0) {
    series.push({
      id: 'low',
      kind: 'line',
      yScaleKey: 'price',
      color: 'secondary',
      points: lowerPoints,
    });
  }
  if (middlePoints.length > 0 && !sameChartLine(middlePoints, lowerPoints)) {
    series.push({
      id: 'mid',
      kind: 'line',
      yScaleKey: 'price',
      color: 'primary-container',
      points: middlePoints,
    });
  }
  if (
    upperPoints.length > 0 &&
    !sameChartLine(upperPoints, lowerPoints) &&
    !sameChartLine(upperPoints, middlePoints)
  ) {
    series.push({
      id: 'high',
      kind: 'line',
      yScaleKey: 'price',
      color: 'error',
      points: upperPoints,
    });
  }
  return series;
}

function dailyChartPoints(
  row: AuctionMarketItemDetailPoint,
  index: number,
): {
  quantity: ChartPoint;
  lower?: ChartPoint;
  middle?: ChartPoint;
  upper?: ChartPoint;
} {
  const quantity = row.avgQuantity;
  const hasPercentiles =
    row.p25Price != null &&
    row.p75Price != null &&
    Number.isFinite(row.p25Price) &&
    Number.isFinite(row.p75Price);
  return {
    quantity: {
      x: index,
      y: quantity != null && Number.isFinite(quantity) && quantity >= 0 ? quantity : 0,
    },
    lower: finiteChartPoint(hasPercentiles ? row.p25Price : row.minPrice, index),
    middle: finiteChartPoint(row.avgPrice, index),
    upper: finiteChartPoint(hasPercentiles ? row.p75Price : row.maxPrice, index),
  };
}

function finiteChartPoint(value: number | null | undefined, index: number): ChartPoint | undefined {
  return value != null && Number.isFinite(value) ? { x: index, y: value } : undefined;
}

function quantitySeries(points: readonly ChartPoint[]): ChartSeries[] {
  return points.length
    ? [
        {
          id: 'quantity',
          kind: 'column',
          yScaleKey: 'quantity',
          color: 'tertiary-container',
          points,
        },
      ]
    : [];
}

function sameChartLine(
  firstLine: readonly ChartPoint[],
  secondLine: readonly ChartPoint[],
): boolean {
  return (
    firstLine.length === secondLine.length &&
    firstLine.every(
      (point, index) => point.x === secondLine[index]?.x && point.y === secondLine[index]?.y,
    )
  );
}

export function craftingAnalyticsToChartSeries(
  analytics: AuctionMarketItemCraftingAnalyticsResponse,
): ChartSeries[] {
  const profitPts: ChartPoint[] = [];
  const roiPts: ChartPoint[] = [];
  analytics.dailySeries.forEach((point, index) => {
    if (point.profit != null && Number.isFinite(point.profit))
      profitPts.push({ x: index, y: point.profit });
    if (point.roiPercent != null && Number.isFinite(point.roiPercent))
      roiPts.push({ x: index, y: point.roiPercent });
  });
  const series: ChartSeries[] = [];
  if (profitPts.length) {
    series.push({
      id: 'profit',
      kind: 'column',
      yScaleKey: 'profit',
      color: 'primary-container',
      points: profitPts,
    });
  }
  if (roiPts.length) {
    series.push({ id: 'roi', kind: 'line', yScaleKey: 'roi', color: 'secondary', points: roiPts });
  }
  return series;
}

export function sortHourlyPoints(
  rows: readonly AuctionMarketItemHourlyPoint[],
): AuctionMarketItemHourlyPoint[] {
  return [...rows].sort((firstPoint, secondPoint) => {
    const firstTimestamp = Date.parse(firstPoint.timestamp ?? '');
    const secondTimestamp = Date.parse(secondPoint.timestamp ?? '');
    if (Number.isFinite(firstTimestamp) && Number.isFinite(secondTimestamp)) {
      return firstTimestamp - secondTimestamp;
    }
    if (Number.isFinite(firstTimestamp)) return -1;
    if (Number.isFinite(secondTimestamp)) return 1;
    return firstPoint.hourOfDay - secondPoint.hourOfDay;
  });
}

export function hourlyPointsToChartSeries(
  rows: readonly AuctionMarketItemHourlyPoint[],
): ChartSeries[] {
  if (rows.length === 0) return [];

  const sorted = sortHourlyPoints(rows);
  const quantityPoints: ChartPoint[] = [];
  const middlePoints: ChartPoint[] = [];

  for (let index = 0; index < sorted.length; index++) {
    const point = sorted[index]!;

    const quantity = point.totalQuantity;
    quantityPoints.push({
      x: index,
      y: quantity != null && Number.isFinite(quantity) && quantity >= 0 ? quantity : 0,
    });

    const middle = point.avgPrice;
    if (middle != null && Number.isFinite(middle)) {
      middlePoints.push({ x: index, y: middle });
    }
  }

  if (middlePoints.length === 0) {
    return quantitySeries(quantityPoints);
  }

  const series = quantitySeries(quantityPoints);
  series.push({
    id: 'mid',
    kind: 'line',
    yScaleKey: 'price',
    color: 'primary-container',
    points: middlePoints,
  });
  return series;
}

export function hourlyPriceHeatmapCellsFromPoints(
  points: readonly AuctionMarketItemHourlyPoint[],
): HeatmapCell[] {
  type Bucket = { sum: number; count: number };
  const buckets = new Map<string, Bucket>();
  for (const point of points) {
    if (point.avgPrice == null || !Number.isFinite(point.avgPrice)) continue;
    const dayOfWeek = dayOfWeekFromTimestamp(point.timestamp);
    if (dayOfWeek == null) continue;
    const hour = point.hourOfDay;
    if (!Number.isFinite(hour) || hour < 0 || hour > 23) continue;
    const key = `${dayOfWeek}-${hour}`;
    const current = buckets.get(key) ?? { sum: 0, count: 0 };
    current.sum += point.avgPrice;
    current.count += 1;
    buckets.set(key, current);
  }
  const cells: HeatmapCell[] = [];
  for (const [key, bucket] of buckets.entries()) {
    const [rowRaw, colRaw] = key.split('-');
    const row = Number(rowRaw);
    const col = Number(colRaw);
    if (!Number.isFinite(row) || !Number.isFinite(col) || bucket.count <= 0) continue;
    const avgPrice = bucket.sum / bucket.count;
    cells.push({
      row,
      col,
      value: avgPrice,
      label: $localize`:@@itemDetail.heatmapAvgPrice:avg price ${formatCopperCurrency(avgPrice)} · n=${bucket.count}`,
    });
  }
  return cells;
}

export function dayOfWeekFromTimestamp(timestamp: string | null | undefined): number | null {
  if (!timestamp) return null;
  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) return null;
  return (date.getUTCDay() + 6) % 7;
}
