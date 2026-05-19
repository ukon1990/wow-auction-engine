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
  let r: ActivatedRoute | null = route;
  while (r) {
    const m = r.snapshot.paramMap;
    if (m.has('region') && m.has('realm')) {
      return r;
    }
    r = r.parent;
  }
  return route;
}

export function variantFromQuery(q: ParamMap): ItemDetailVariantParams {
  return {
    bonusKey: q.get('bonusKey') ?? '',
    modifierKey: q.get('modifierKey') ?? '',
    petSpeciesId: Number(q.get('petSpeciesId') ?? 0) || 0,
  };
}

export function variantEqual(a: ItemDetailVariantParams, b: ItemDetailVariantParams): boolean {
  return (
    a.bonusKey === b.bonusKey &&
    a.modifierKey === b.modifierKey &&
    a.petSpeciesId === b.petSpeciesId
  );
}

export function scopeFromQuery(q: ParamMap): ItemDetailScope {
  return q.get('scope') === 'commodity' ? 'commodity' : 'realm';
}

export function isRegion(value: string | null | undefined): value is RegionCode {
  return value === 'us' || value === 'eu' || value === 'kr' || value === 'tw';
}

export function formatRealmLabel(slug: string): string {
  const t = slug.replace(/-/g, ' ');
  return t.length ? t.charAt(0).toUpperCase() + t.slice(1) : slug;
}

export function showChartScopeToggleFn(d: AuctionMarketItemDetailResponse): boolean {
  return !d.regionalMetricsRedundant && hasRealmScopeMetrics(d.summary);
}

export function shouldUseCommodityScopeByDefault(d: AuctionMarketItemDetailResponse): boolean {
  return d.regionalMetricsRedundant || !hasRealmScopeMetrics(d.summary);
}

export function shouldFallbackToCommodityFetch(d: AuctionMarketItemDetailResponse): boolean {
  return (
    !d.regionalMetricsRedundant &&
    !hasRealmScopeMetrics(d.summary) &&
    !hasCommodityScopeMetrics(d.summary)
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

  const qtyPts: ChartPoint[] = [];
  const lowerPts: ChartPoint[] = [];
  const midPts: ChartPoint[] = [];
  const upperPts: ChartPoint[] = [];

  for (let i = 0; i < rows.length; i++) {
    const p = rows[i]!;
    const x = i;
    const q = p.avgQuantity;
    qtyPts.push({
      x,
      y: q != null && Number.isFinite(q) && q >= 0 ? q : 0,
    });

    const hasPctiles =
      p.p25Price != null &&
      p.p75Price != null &&
      Number.isFinite(p.p25Price) &&
      Number.isFinite(p.p75Price);
    const lo = hasPctiles ? p.p25Price! : p.minPrice;
    const hi = hasPctiles ? p.p75Price! : p.maxPrice;
    const mid = p.avgPrice;

    if (lo != null && Number.isFinite(lo)) lowerPts.push({ x, y: lo });
    if (mid != null && Number.isFinite(mid)) midPts.push({ x, y: mid });
    if (hi != null && Number.isFinite(hi)) upperPts.push({ x, y: hi });
  }

  if (lowerPts.length === 0 && midPts.length === 0 && upperPts.length === 0) {
    return qtyPts.length
      ? [
          {
            id: 'quantity',
            kind: 'column',
            yScaleKey: 'quantity',
            color: 'tertiary-container',
            points: qtyPts,
          },
        ]
      : [];
  }

  const series: ChartSeries[] = [];
  if (qtyPts.length > 0) {
    series.push({
      id: 'quantity',
      kind: 'column',
      yScaleKey: 'quantity',
      color: 'tertiary-container',
      points: qtyPts,
    });
  }
  const sameLine = (a: readonly ChartPoint[], b: readonly ChartPoint[]) =>
    a.length === b.length && a.every((p, i) => p.x === b[i]?.x && p.y === b[i]?.y);

  if (lowerPts.length > 0) {
    series.push({
      id: 'low',
      kind: 'line',
      yScaleKey: 'price',
      color: 'secondary',
      points: lowerPts,
    });
  }
  if (midPts.length > 0 && !sameLine(midPts, lowerPts)) {
    series.push({
      id: 'mid',
      kind: 'line',
      yScaleKey: 'price',
      color: 'primary-container',
      points: midPts,
    });
  }
  if (upperPts.length > 0 && !sameLine(upperPts, lowerPts) && !sameLine(upperPts, midPts)) {
    series.push({ id: 'high', kind: 'line', yScaleKey: 'price', color: 'error', points: upperPts });
  }
  return series;
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
  return [...rows].sort((a, b) => {
    const ta = Date.parse(a.timestamp ?? '');
    const tb = Date.parse(b.timestamp ?? '');
    if (Number.isFinite(ta) && Number.isFinite(tb)) return ta - tb;
    if (Number.isFinite(ta)) return -1;
    if (Number.isFinite(tb)) return 1;
    return a.hourOfDay - b.hourOfDay;
  });
}

export function hourlyPointsToChartSeries(
  rows: readonly AuctionMarketItemHourlyPoint[],
): ChartSeries[] {
  if (rows.length === 0) return [];

  const sorted = sortHourlyPoints(rows);
  const qtyPts: ChartPoint[] = [];
  const midPts: ChartPoint[] = [];

  for (let i = 0; i < sorted.length; i++) {
    const p = sorted[i]!;
    const x = i;

    const q = p.totalQuantity;
    qtyPts.push({
      x,
      y: q != null && Number.isFinite(q) && q >= 0 ? q : 0,
    });

    const mid = p.avgPrice;
    if (mid != null && Number.isFinite(mid)) midPts.push({ x, y: mid });
  }

  if (midPts.length === 0) {
    return qtyPts.length
      ? [
          {
            id: 'quantity',
            kind: 'column',
            yScaleKey: 'quantity',
            color: 'tertiary-container',
            points: qtyPts,
          },
        ]
      : [];
  }

  const series: ChartSeries[] = [];
  if (qtyPts.length > 0) {
    series.push({
      id: 'quantity',
      kind: 'column',
      yScaleKey: 'quantity',
      color: 'tertiary-container',
      points: qtyPts,
    });
  }
  series.push({
    id: 'mid',
    kind: 'line',
    yScaleKey: 'price',
    color: 'primary-container',
    points: midPts,
  });
  return series;
}

export function hourlyPriceHeatmapCellsFromPoints(
  points: readonly AuctionMarketItemHourlyPoint[],
): HeatmapCell[] {
  type Bucket = { sum: number; count: number };
  const buckets = new Map<string, Bucket>();
  for (const p of points) {
    if (p.avgPrice == null || !Number.isFinite(p.avgPrice)) continue;
    const dayOfWeek = dayOfWeekFromTimestamp(p.timestamp);
    if (dayOfWeek == null) continue;
    const hour = p.hourOfDay;
    if (!Number.isFinite(hour) || hour < 0 || hour > 23) continue;
    const key = `${dayOfWeek}-${hour}`;
    const current = buckets.get(key) ?? { sum: 0, count: 0 };
    current.sum += p.avgPrice;
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
