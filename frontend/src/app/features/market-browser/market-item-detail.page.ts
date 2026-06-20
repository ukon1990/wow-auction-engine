import { DecimalPipe, isPlatformBrowser } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  PLATFORM_ID,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  ChartPanelComponent,
  CopperToCurrencyPipe,
  copperToCurrencyAmount,
  CurrencyAmountComponent,
  formatCopperCurrency,
  HeatmapGridComponent,
  type ChartSeries,
  type HeatmapCell,
  ItemStatCardComponent,
  PaginationComponent,
  type PaginationState,
  PageFrameComponent,
  SymbolIconComponent,
  TooltipCardComponent,
  type TooltipRow,
} from '@ui';
import {
  AuctionMarketItemCraftingAnalyticsResponse,
  AuctionMarketItemCraftingDetail,
  AuctionMarketItemCurrentListing,
  AuctionMarketItemDetailPoint,
  AuctionMarketItemDetailResponse,
  AuctionMarketItemDetailSummary,
  AuctionMarketItemHourlyPoint,
} from '@api/generated';
import {
  catchError,
  combineLatest,
  distinctUntilChanged,
  finalize,
  map,
  of,
  switchMap,
} from 'rxjs';
import type Highcharts from 'highcharts/esm/highcharts';

import {
  ItemDetailScope,
  ItemDetailVariantParams,
  MarketItemDetailService,
} from '@core/services/market-item-detail.service';
import { LocaleService } from '@core/services/locale.service';
import {
  craftingAnalyticsToChartSeries,
  dailyPointsToChartSeries,
  dayOfMonthLabel,
  formatRealmLabel,
  hourOfDayLabel,
  hourlyPointsToChartSeries,
  hourlyPriceHeatmapCellsFromPoints,
  isRegion,
  mergeCommodityScope,
  priceChangeCaptionStatic,
  quantityAxisLabel,
  realmAncestorRoute,
  scopeFromQuery,
  shouldFallbackToCommodityFetch,
  shouldUseCommodityScopeByDefault,
  showChartScopeToggleFn,
  sortHourlyPoints,
  type RegionCode,
  variantEqual,
  variantFromQuery,
} from './market-item-detail.helpers';

interface ItemDetailBackState {
  readonly returnUrl?: string;
  readonly returnLabel?: string;
}

@Component({
  selector: 'app-market-item-detail-page',
  host: {
    class: 'flex min-h-0 min-w-0 flex-1 flex-col',
  },
  imports: [
    RouterLink,
    PageFrameComponent,
    ChartPanelComponent,
    CopperToCurrencyPipe,
    HeatmapGridComponent,
    CurrencyAmountComponent,
    ItemStatCardComponent,
    PaginationComponent,
    SymbolIconComponent,
    TooltipCardComponent,
  ],
  templateUrl: './market-item-detail.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarketItemDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly detailService = inject(MarketItemDetailService);
  private readonly locale = inject(LocaleService);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly decimalPipe = new DecimalPipe(this.locale.formatLocale());

  protected readonly loading = signal(true);
  protected readonly commodityLoading = signal(false);
  protected readonly error = signal(false);
  protected readonly detail = signal<AuctionMarketItemDetailResponse | null>(null);
  protected readonly realmCurrentListings = signal<readonly AuctionMarketItemCurrentListing[]>([]);
  protected readonly commodityCurrentListings = signal<readonly AuctionMarketItemCurrentListing[]>(
    [],
  );
  protected readonly currentListingsPage = signal(0);
  protected readonly currentListingsPageSize = 10;
  protected readonly commodityLoaded = signal(false);
  protected readonly chartScope = signal<'realm' | 'commodity'>('realm');
  protected readonly selectedRecipeId = signal<number | null>(null);
  protected readonly craftingAnalytics = signal<AuctionMarketItemCraftingAnalyticsResponse | null>(
    null,
  );
  protected readonly analyticsLoading = signal(false);
  protected readonly analyticsError = signal(false);
  private analyticsRequestId = 0;
  protected readonly heatmapRowLabels = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'] as const;
  protected readonly heatmapColumnLabels = Array.from({ length: 24 }, (_, h) =>
    String(h).padStart(2, '0'),
  );
  protected readonly skeletonCards = [0, 1, 2, 3] as const;
  protected readonly skeletonCharts = [0, 1] as const;
  protected readonly skeletonBars = [
    { index: 0, height: 42 },
    { index: 1, height: 64 },
    { index: 2, height: 36 },
    { index: 3, height: 78 },
    { index: 4, height: 55 },
    { index: 5, height: 88 },
    { index: 6, height: 48 },
    { index: 7, height: 70 },
    { index: 8, height: 58 },
    { index: 9, height: 82 },
    { index: 10, height: 46 },
    { index: 11, height: 66 },
  ] as const;
  protected readonly heatmapSkeletonRows = Array.from({ length: 7 }, (_, i) => i);
  protected readonly currentListingsRowLabel = $localize`:@@itemDetail.listingsCount:listings`;
  protected readonly currentListingsEmptySummary = $localize`:@@itemDetail.noCurrentListings:No current listings for this item.`;

  protected pageEyebrow(): string {
    return $localize`:@@itemDetail.eyebrow:Item Codex`;
  }
  protected readonly heatmapSkeletonCols = Array.from({ length: 24 }, (_, i) => i);

  private readonly backState = signal<ItemDetailBackState>({});

  private readonly routeCtx = signal<{
    region: RegionCode;
    realmSlug: string;
    itemId: number;
    recipeId: string | null;
    variant: ItemDetailVariantParams;
    listSegment: string;
    listLabel: string;
  } | null>(null);

  protected readonly regionRealm = computed(() => {
    const ctx = this.routeCtx();
    if (!ctx) {
      return {
        region: '',
        realm: '',
        realmLabel: '',
        regionLabel: '',
        marketListSegment: 'auctions',
        marketListLabel: $localize`:@@route.auctions:Auctions`,
      };
    }
    return {
      region: ctx.region,
      realm: ctx.realmSlug,
      realmLabel: formatRealmLabel(ctx.realmSlug),
      regionLabel: ctx.region.toUpperCase(),
      marketListSegment: ctx.listSegment,
      marketListLabel: ctx.listLabel,
    };
  });

  protected readonly itemTitle = computed(() => this.detail()?.item.name ?? 'Item');

  protected readonly pageTitle = computed(() => this.detail()?.item.name ?? 'Item');

  protected readonly dailyChartSeries = computed(() => {
    return dailyPointsToChartSeries(this.dailyPointsForActiveScope());
  });

  protected readonly hourlyChartSeries = computed(() => {
    const pts = this.hourlyPointsForActiveScope();
    return hourlyPointsToChartSeries(pts);
  });

  protected readonly dailyChartOptions = computed<Highcharts.Options>(() => {
    const points = this.dailyPointsForActiveScope();
    return this.chartOptionsForSeries(this.dailyChartSeries(), {
      xLabelAt: (index) => dayOfMonthLabel(points[index]?.statDate),
    });
  });

  protected readonly hourlyChartOptions = computed<Highcharts.Options>(() => {
    const points = this.hourlyPointsForActiveScope();
    return this.chartOptionsForSeries(this.hourlyChartSeries(), {
      xLabelAt: (index) => hourOfDayLabel(points[index]?.hourOfDay),
    });
  });

  protected readonly selectedCrafting = computed<AuctionMarketItemCraftingDetail | null>(() => {
    const craftings = this.detail()?.craftings ?? [];
    if (!craftings.length) return null;
    return craftings.find((c) => c.recipeId === this.selectedRecipeId()) ?? craftings[0];
  });

  protected readonly craftingAnalyticsSeries = computed(() => {
    const analytics = this.craftingAnalytics();
    if (!analytics) return [];
    return craftingAnalyticsToChartSeries(analytics);
  });

  protected readonly craftingAnalyticsChartOptions = computed<Highcharts.Options>(() =>
    this.chartOptionsForSeries(this.craftingAnalyticsSeries()),
  );

  protected readonly craftingHeatmapCells = computed<HeatmapCell[]>(() =>
    (this.craftingAnalytics()?.heatmap ?? []).map((cell) => ({
      row: cell.dayOfWeek,
      col: cell.hourOfDay,
      value: cell.profit,
      label: [
        $localize`:@@itemDetail.heatmapProfit:profit ${formatCopperCurrency(cell.profit)}`,
        $localize`:@@itemDetail.heatmapPrice:price ${formatCopperCurrency(cell.outputUnitPrice)}`,
        $localize`:@@itemDetail.heatmapRoi:ROI ${this.formatRoi(cell.roiPercent)}`,
        `n=${cell.sampleCount}`,
      ].join(' · '),
    })),
  );

  protected readonly hourlyPriceHeatmapCells = computed<HeatmapCell[]>(() => {
    const points = this.hourlyPointsForActiveScope();
    return hourlyPriceHeatmapCellsFromPoints(points);
  });

  protected readonly currentListingsForActiveScope = computed(() => {
    if (this.chartScope() === 'commodity') {
      return this.commodityCurrentListings();
    }
    return this.realmCurrentListings();
  });

  protected readonly currentListingsPagination = computed<PaginationState>(() => {
    const totalItems = this.currentListingsForActiveScope().length;
    const totalPages = Math.ceil(totalItems / this.currentListingsPageSize);
    const page = totalPages > 0 ? Math.min(this.currentListingsPage(), totalPages - 1) : 0;
    return {
      page,
      pageSize: this.currentListingsPageSize,
      totalItems,
      totalPages,
    };
  });

  protected readonly pagedCurrentListings = computed(() => {
    const listings = this.currentListingsForActiveScope();
    const page = this.currentListingsPagination().page;
    const start = page * this.currentListingsPageSize;
    return listings.slice(start, start + this.currentListingsPageSize);
  });

  constructor() {
    if (isPlatformBrowser(this.platformId)) {
      const raw = window.history.state as Record<string, unknown> | null;
      this.backState.set({
        returnUrl: typeof raw?.['returnUrl'] === 'string' ? raw['returnUrl'] : undefined,
        returnLabel: typeof raw?.['returnLabel'] === 'string' ? raw['returnLabel'] : undefined,
      });
    }

    const realmRoute = realmAncestorRoute(this.route);

    combineLatest([
      realmRoute.paramMap,
      this.route.paramMap,
      this.route.data,
      this.route.queryParamMap,
    ])
      .pipe(
        map(([realmPm, itemPm, data, q]) => ({
          region: realmPm.get('region'),
          realmSlug: realmPm.get('realm'),
          itemId: Number(itemPm.get('itemId')),
          recipeId: itemPm.get('recipeId'),
          listSegment: (data['marketListSegment'] as string | undefined) ?? 'auctions',
          listLabel:
            (data['marketListLabel'] as string | undefined) ??
            $localize`:@@route.auctions:Auctions`,
          variant: variantFromQuery(q),
          initialScope: scopeFromQuery(q),
        })),
        distinctUntilChanged(
          (a, b) =>
            a.region === b.region &&
            a.realmSlug === b.realmSlug &&
            a.itemId === b.itemId &&
            a.recipeId === b.recipeId &&
            a.listSegment === b.listSegment &&
            a.initialScope === b.initialScope &&
            variantEqual(a.variant, b.variant),
        ),
        switchMap((ctx) => {
          if (!isRegion(ctx.region) || !ctx.realmSlug || !Number.isFinite(ctx.itemId)) {
            this.loading.set(false);
            this.error.set(true);
            this.detail.set(null);
            this.routeCtx.set(null);
            return of(null);
          }
          this.routeCtx.set({
            region: ctx.region,
            realmSlug: ctx.realmSlug,
            itemId: ctx.itemId,
            recipeId: ctx.recipeId,
            variant: ctx.variant,
            listSegment: ctx.listSegment,
            listLabel: ctx.listLabel,
          });
          this.loading.set(true);
          this.error.set(false);
          this.commodityLoaded.set(false);
          this.realmCurrentListings.set([]);
          this.commodityCurrentListings.set([]);
          this.currentListingsPage.set(0);
          this.craftingAnalytics.set(null);
          this.analyticsError.set(false);
          this.selectedRecipeId.set(null);
          this.chartScope.set(ctx.initialScope);
          const preferredRecipeId =
            ctx.listSegment === 'crafting' && ctx.recipeId ? Number(ctx.recipeId) : undefined;
          return this.detailService
            .loadItemDetail(
              ctx.region,
              ctx.realmSlug,
              ctx.itemId,
              ctx.variant,
              ctx.initialScope,
              undefined,
              preferredRecipeId,
            )
            .pipe(
              finalize(() => this.loading.set(false)),
              catchError(() => {
                this.error.set(true);
                this.detail.set(null);
                return of(null);
              }),
            );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((res) => {
        if (res) {
          this.detail.set(res);
          if (this.chartScope() === 'commodity') {
            this.commodityCurrentListings.set(res.currentListings);
          } else {
            this.realmCurrentListings.set(res.currentListings);
          }
          this.selectedRecipeId.set(res.craftings[0]?.recipeId ?? null);
          this.loadSelectedRecipeAnalytics();
          if (shouldFallbackToCommodityFetch(res)) {
            this.onScopeSelected('commodity');
            return;
          }
          if (shouldUseCommodityScopeByDefault(res)) {
            this.chartScope.set('commodity');
          } else {
            this.chartScope.set('realm');
          }
        }
      });
  }

  protected backLabel(): string {
    return this.backState().returnLabel ?? $localize`:@@itemDetail.backToMarket:Back to market`;
  }

  protected selectRecipe(recipeId: number): void {
    if (this.selectedRecipeId() === recipeId) return;
    this.selectedRecipeId.set(recipeId);
    this.loadSelectedRecipeAnalytics();
  }

  /**
   * Implements the keyboard navigation contract of the WAI-ARIA radiogroup pattern for the recipe
   * selector: Arrow keys move focus and selection between recipes, Home/End jump to first/last,
   * Space/Enter activate the focused recipe (browsers already do this for buttons but we keep the
   * branch consistent). The radiogroup uses roving tabindex via [attr.tabindex] on the buttons.
   */
  protected onRecipeRadioKeydown(
    event: KeyboardEvent,
    craftings: readonly { recipeId: number }[],
  ): void {
    const ARROW_KEYS = ['ArrowRight', 'ArrowDown', 'ArrowLeft', 'ArrowUp', 'Home', 'End'] as const;
    if (!ARROW_KEYS.includes(event.key as (typeof ARROW_KEYS)[number])) return;
    if (craftings.length === 0) return;

    const current = this.selectedRecipeId();
    const currentIndex = Math.max(
      0,
      craftings.findIndex((r) => r.recipeId === current),
    );
    let nextIndex = currentIndex;
    switch (event.key) {
      case 'ArrowRight':
      case 'ArrowDown':
        nextIndex = (currentIndex + 1) % craftings.length;
        break;
      case 'ArrowLeft':
      case 'ArrowUp':
        nextIndex = (currentIndex - 1 + craftings.length) % craftings.length;
        break;
      case 'Home':
        nextIndex = 0;
        break;
      case 'End':
        nextIndex = craftings.length - 1;
        break;
    }
    if (nextIndex === currentIndex) return;
    event.preventDefault();
    const nextRecipe = craftings[nextIndex];
    this.selectRecipe(nextRecipe.recipeId);

    queueMicrotask(() => {
      const target = event.currentTarget as HTMLElement | null;
      const next = target?.querySelector<HTMLElement>(
        `[role="radio"][data-recipe-index="${nextIndex}"]`,
      );
      next?.focus();
    });
  }

  private loadSelectedRecipeAnalytics(): void {
    const ctx = this.routeCtx();
    const recipeId = this.selectedRecipeId();
    if (!ctx || recipeId == null) return;
    const reqId = ++this.analyticsRequestId;
    this.analyticsLoading.set(true);
    this.analyticsError.set(false);
    this.craftingAnalytics.set(null);
    this.detailService
      .loadCraftingAnalytics(ctx.region, ctx.realmSlug, ctx.itemId, recipeId, ctx.variant)
      .pipe(
        finalize(() => {
          if (reqId === this.analyticsRequestId) {
            this.analyticsLoading.set(false);
          }
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (analytics) => {
          if (reqId !== this.analyticsRequestId) return;
          this.craftingAnalytics.set(analytics);
        },
        error: () => {
          if (reqId !== this.analyticsRequestId) return;
          this.analyticsError.set(true);
        },
      });
  }

  protected onScopeSelected(scope: ItemDetailScope): void {
    if (scope === 'realm') {
      this.chartScope.set('realm');
      this.currentListingsPage.set(0);
      return;
    }
    if (this.commodityLoaded()) {
      this.chartScope.set('commodity');
      this.currentListingsPage.set(0);
      return;
    }
    const ctx = this.routeCtx();
    if (!ctx) return;
    this.commodityLoading.set(true);
    this.detailService
      .loadItemDetail(
        ctx.region,
        ctx.realmSlug,
        ctx.itemId,
        ctx.variant,
        'commodity',
        undefined,
        ctx.recipeId ? Number(ctx.recipeId) : undefined,
      )
      .pipe(
        finalize(() => this.commodityLoading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (commodityRes) => {
          this.detail.update((existing) => mergeCommodityScope(existing, commodityRes));
          this.commodityCurrentListings.set(commodityRes.currentListings);
          this.commodityLoaded.set(true);
          this.chartScope.set('commodity');
          this.currentListingsPage.set(0);
        },
        error: () => {
          this.error.set(true);
        },
      });
  }

  protected goBack(): void {
    const url = this.backState().returnUrl;
    if (url) {
      void this.router.navigateByUrl(url);
      return;
    }
    void this.router.navigate(['..'], { relativeTo: this.route });
  }

  protected formatRoi(pct: number | null | undefined): string {
    if (pct == null || !Number.isFinite(pct)) return '—';
    return `${this.formatDecimal(pct, '1.1-1')}%`;
  }

  protected quantityLabel(q: number | null | undefined): string {
    if (q == null || !Number.isFinite(q)) return '—';
    return this.formatDecimal(Math.round(q), '1.0-0');
  }

  protected onCurrentListingsPageChange(page: number): void {
    this.currentListingsPage.set(page);
  }

  protected dailyTooltipTitle(d: AuctionMarketItemDetailResponse, x: number): string {
    const point = this.dailyTooltipPoint(d, x);
    return point?.statDate
      ? point.statDate
      : $localize`:@@itemDetail.dayLabel:Day ${Math.round(x) + 1}`;
  }

  protected dailyTooltipRows(d: AuctionMarketItemDetailResponse, x: number): TooltipRow[] {
    const point = this.dailyTooltipPoint(d, x);
    if (!point) return [];
    return [
      { label: $localize`:@@itemDetail.tooltip.date:date`, value: point.statDate ?? '—' },
      {
        label: $localize`:@@itemDetail.tooltip.avgQuantity:avg quantity`,
        value: this.numberDisplay(point.avgQuantity),
      },
      {
        label: $localize`:@@itemDetail.tooltip.minQuantity:min quantity`,
        value: this.numberDisplay(point.minQuantity),
      },
      {
        label: $localize`:@@itemDetail.tooltip.maxQuantity:max quantity`,
        value: this.numberDisplay(point.maxQuantity),
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

  protected hourlyTooltipTitle(d: AuctionMarketItemDetailResponse, x: number): string {
    const point = this.hourlyTooltipPoint(d, x);
    const hour = point?.hourOfDay ?? 0;
    const prefix = `${String(hour).padStart(2, '0')}:00`;
    return point?.timestamp ? `${point.timestamp} · ${prefix}` : prefix;
  }

  protected hourlyTooltipRows(d: AuctionMarketItemDetailResponse, x: number): TooltipRow[] {
    const point = this.hourlyTooltipPoint(d, x);
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
        value: this.numberDisplay(point.totalQuantity),
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

  protected priceChangeCaption(pct: number | null | undefined): string {
    if (pct == null || !Number.isFinite(pct)) return '';
    const sign = pct > 0 ? '+' : '';
    return $localize`:@@itemDetail.vsPriorDay:${sign}${this.formatDecimal(pct, '1.1-1')}% vs prior day`;
  }

  protected realmVsCommodityCaption(pct: number | null | undefined): string {
    if (pct == null || !Number.isFinite(pct)) return '';
    const sign = pct > 0 ? '+' : '';
    return $localize`:@@itemDetail.vsCommodity:${sign}${this.formatDecimal(pct, '1.1-1')}% vs commodity`;
  }

  protected summaryHasCommodityPrice(s: AuctionMarketItemDetailSummary): boolean {
    const p = s.commodityPrice;
    return p != null && Number.isFinite(p);
  }

  protected summaryHasCommodityQuantity(s: AuctionMarketItemDetailSummary): boolean {
    const q = s.commodityQuantity;
    return q != null && Number.isFinite(q);
  }

  protected showChartScopeToggle(d: AuctionMarketItemDetailResponse): boolean {
    return showChartScopeToggleFn(d);
  }

  protected showCommoditySnapshotLine(d: AuctionMarketItemDetailResponse): boolean {
    return showChartScopeToggleFn(d);
  }

  protected activeScopeLabel(): string {
    return this.chartScope() === 'commodity' || this.detail()?.regionalMetricsRedundant
      ? $localize`:@@itemDetail.regionScope:Region`
      : $localize`:@@itemDetail.realmScope:Realm`;
  }

  protected activeScopePriceLabel(): string {
    const scope = this.activeScopeLabel();
    return $localize`:@@itemDetail.scopePrice:${scope} price`;
  }

  protected activeScopeQuantityLabel(): string {
    const scope = this.activeScopeLabel();
    return $localize`:@@itemDetail.scopeQuantity:${scope} quantity`;
  }

  protected activeScopePrice(s: AuctionMarketItemDetailSummary): number | null | undefined {
    return this.chartScope() === 'commodity' ? s.commodityPrice : s.selectedRealmPrice;
  }

  protected activeScopeQuantity(s: AuctionMarketItemDetailSummary): number | null | undefined {
    return this.chartScope() === 'commodity' ? s.commodityQuantity : s.selectedRealmQuantity;
  }

  protected activeScopePriceCaption(s: AuctionMarketItemDetailSummary): string {
    return this.chartScope() === 'commodity'
      ? priceChangeCaptionStatic(s.commodityPriceChangePercent)
      : priceChangeCaptionStatic(s.selectedRealmPriceChangePercent);
  }

  private chartOptionsForSeries(
    series: readonly ChartSeries[],
    labels: { readonly xLabelAt?: (index: number) => string } = {},
  ): Highcharts.Options {
    const yScaleKeys = [...new Set(series.map((s) => s.yScaleKey))];
    return {
      xAxis: labels.xLabelAt
        ? {
            labels: {
              formatter: (ctx) => labels.xLabelAt?.(this.axisIndex(ctx.value)) || ctx.text || '',
            },
          }
        : undefined,
      yAxis: yScaleKeys.map((key) => ({
        min: key === 'quantity' ? 0 : undefined,
        labels: {
          formatter: (ctx) => this.yAxisLabel(key, ctx.value, ctx.text),
        },
      })),
    };
  }

  private yAxisLabel(key: string, value: number | string, fallback: string | undefined): string {
    if (key === 'price' || key === 'profit') {
      return this.copperAxisLabel(value);
    }
    if (key === 'quantity') {
      return this.quantityAxisLabel(value);
    }
    if (key === 'roi') {
      return `${this.axisNumber(value).toLocaleString(this.locale.formatLocale())}%`;
    }
    return fallback ?? String(value);
  }

  private copperAxisLabel(value: number | string): string {
    return formatCopperCurrency(this.axisNumber(value));
  }

  private quantityAxisLabel(value: number | string): string {
    return quantityAxisLabel(this.axisNumber(value), this.locale.formatLocale());
  }

  private axisNumber(value: number | string): number {
    return typeof value === 'number' ? value : Number(value);
  }

  private axisIndex(value: number | string): number {
    return Math.round(this.axisNumber(value));
  }

  private dailyTooltipPoint(
    d: AuctionMarketItemDetailResponse,
    x: number,
  ): AuctionMarketItemDetailPoint | undefined {
    return this.dailyPointsForActiveScope(d)[Math.round(x)];
  }

  private hourlyTooltipPoint(
    _d: AuctionMarketItemDetailResponse,
    x: number,
  ): AuctionMarketItemHourlyPoint | undefined {
    const points = this.hourlyPointsForActiveScope();
    if (points.length === 0) return undefined;
    const i = Math.max(0, Math.min(points.length - 1, Math.round(x)));
    return points[i];
  }

  private hourlyPointsForActiveScope(): AuctionMarketItemHourlyPoint[] {
    const d = this.detail();
    if (!d) return [];
    const points =
      d.regionalMetricsRedundant || this.chartScope() === 'realm'
        ? d.hourlySeriesRealm
        : d.hourlySeriesCommodity;
    return sortHourlyPoints(points);
  }

  private dailyPointsForActiveScope(
    detail: AuctionMarketItemDetailResponse | null = this.detail(),
  ): readonly AuctionMarketItemDetailPoint[] {
    if (!detail) return [];
    return detail.regionalMetricsRedundant || this.chartScope() === 'realm'
      ? detail.dailySeriesRealm
      : detail.dailySeriesCommodity;
  }

  private numberDisplay(value: number | null | undefined): string {
    if (value == null || !Number.isFinite(value)) return '—';
    return this.formatDecimal(Math.round(value), '1.0-0');
  }

  private formatDecimal(value: number, digitsInfo: string): string {
    return (
      this.decimalPipe.transform(value, digitsInfo, this.locale.formatLocale()) ?? String(value)
    );
  }
}
