import { DecimalPipe, PercentPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
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
  SkeletonDirective,
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
import { catchError, distinctUntilChanged, finalize, of, switchMap } from 'rxjs';
import type Highcharts from 'highcharts/esm/highcharts';
import { ItemDetailModalService } from '@core/services/item-detail-modal.service';
import {
  ItemDetailScope,
  ItemDetailVariantParams,
  MarketItemDetailService,
} from '@core/services/market-item-detail.service';
import { LocaleService } from '@core/services/locale.service';
import { ItemLinkComponent, type ItemLinkMode } from '@shared/item-link/item-link.component';
import {
  craftingAnalyticsToChartSeries,
  dailyPointsToChartSeries,
  dayOfMonthLabel,
  hourOfDayLabel,
  hourlyPointsToChartSeries,
  hourlyPriceHeatmapCellsFromPoints,
  mergeCommodityScope,
  priceChangeCaptionStatic,
  quantityAxisLabel,
  shouldFallbackToCommodityFetch,
  shouldUseCommodityScopeByDefault,
  showChartScopeToggleFn,
  sortHourlyPoints,
  type RegionCode,
  variantEqual,
} from './market-item-detail.helpers';

@Component({
  selector: 'app-market-item-detail-panel',
  imports: [
    ChartPanelComponent,
    CopperToCurrencyPipe,
    HeatmapGridComponent,
    CurrencyAmountComponent,
    ItemStatCardComponent,
    ItemLinkComponent,
    PaginationComponent,
    SkeletonDirective,
    SymbolIconComponent,
    TooltipCardComponent,
  ],
  templateUrl: './market-item-detail.panel.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarketItemDetailPanelComponent {
  readonly region = input.required<RegionCode>();
  readonly realmSlug = input.required<string>();
  readonly itemId = input.required<number>();
  readonly variant = input<ItemDetailVariantParams>({
    bonusKey: '',
    modifierKey: '',
    petSpeciesId: 0,
  });
  readonly initialScope = input<ItemDetailScope>('realm');
  readonly recipeId = input<number | null>(null);
  readonly linkMode = input<ItemLinkMode>('modal');

  readonly titleChange = output<string>();
  readonly scopeChange = output<ItemDetailScope>();

  private readonly destroyRef = inject(DestroyRef);
  private readonly detailService = inject(MarketItemDetailService);
  private readonly modal = inject(ItemDetailModalService);
  private readonly locale = inject(LocaleService);
  private readonly decimalPipe = new DecimalPipe(this.locale.formatLocale());
  private readonly percentPipe = new PercentPipe(this.locale.formatLocale());

  protected readonly saleRateStatLabel = $localize`:@@market.column.saleRate:Sale rate`;
  protected readonly soldPerDayStatLabel = $localize`:@@market.column.soldPerDay:Avg sold/day`;
  protected readonly reagentLinkLayoutClass =
    'inline rounded no-underline text-inherit outline-none transition hover:text-primary focus-visible:ring-2 focus-visible:ring-primary/60';

  protected readonly loading = signal(true);
  protected readonly commodityLoading = signal(false);
  protected readonly error = signal(false);
  protected readonly detail = signal<AuctionMarketItemDetailResponse | null>(null);
  protected readonly contentLoading = computed(() => this.loading() || !this.detail());
  protected readonly realmCurrentListings = signal<readonly AuctionMarketItemCurrentListing[]>([]);
  protected readonly commodityCurrentListings = signal<readonly AuctionMarketItemCurrentListing[]>(
    [],
  );
  protected readonly currentListingsPage = signal(0);
  protected readonly currentListingsPageSize = 10;
  protected readonly commodityLoaded = signal(false);
  protected readonly chartScope = signal<ItemDetailScope>('realm');
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
  protected readonly currentListingsRowLabel = $localize`:@@itemDetail.listingsCount:listings`;
  protected readonly currentListingsEmptySummary = $localize`:@@itemDetail.noCurrentListings:No current listings for this item.`;

  private readonly panelCtx = computed(() => ({
    region: this.region(),
    realmSlug: this.realmSlug(),
    itemId: this.itemId(),
    variant: this.variant(),
    initialScope: this.initialScope(),
    recipeId: this.recipeId(),
  }));

  protected readonly reagentLinkMode = computed<ItemLinkMode>(() =>
    this.linkMode() === 'page' ? 'page' : 'modal',
  );

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
    toObservable(this.panelCtx)
      .pipe(
        distinctUntilChanged(
          (a, b) =>
            a.region === b.region &&
            a.realmSlug === b.realmSlug &&
            a.itemId === b.itemId &&
            a.initialScope === b.initialScope &&
            a.recipeId === b.recipeId &&
            variantEqual(a.variant, b.variant),
        ),
        switchMap((ctx) => {
          if (!Number.isFinite(ctx.itemId)) {
            this.loading.set(false);
            this.error.set(true);
            this.detail.set(null);
            return of(null);
          }
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
            ctx.recipeId != null && Number.isFinite(ctx.recipeId) ? ctx.recipeId : undefined;
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
          this.titleChange.emit(res.item.name);
          this.selectedRecipeId.set(res.craftings[0]?.recipeId ?? null);
          this.loadSelectedRecipeAnalytics();
          if (shouldFallbackToCommodityFetch(res)) {
            this.storeCurrentListings(res.currentListings, this.chartScope());
            this.onScopeSelected('commodity');
            return;
          }
          const scope: ItemDetailScope = shouldUseCommodityScopeByDefault(res)
            ? 'commodity'
            : 'realm';
          this.chartScope.set(scope);
          this.storeCurrentListings(res.currentListings, scope);
          if (scope === 'commodity' && res.regionalMetricsRedundant) {
            this.commodityLoaded.set(true);
          }
        }
      });
  }

  private storeCurrentListings(
    listings: readonly AuctionMarketItemCurrentListing[],
    scope: ItemDetailScope,
  ): void {
    if (scope === 'commodity') {
      this.commodityCurrentListings.set(listings);
    } else {
      this.realmCurrentListings.set(listings);
    }
  }

  protected selectRecipe(recipeId: number): void {
    if (this.selectedRecipeId() === recipeId) return;
    this.selectedRecipeId.set(recipeId);
    this.loadSelectedRecipeAnalytics();
  }

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
    const recipeId = this.selectedRecipeId();
    if (recipeId == null) return;
    const reqId = ++this.analyticsRequestId;
    this.analyticsLoading.set(true);
    this.analyticsError.set(false);
    this.craftingAnalytics.set(null);
    this.detailService
      .loadCraftingAnalytics(
        this.region(),
        this.realmSlug(),
        this.itemId(),
        recipeId,
        this.variant(),
      )
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
      this.emitScopeChange('realm');
      return;
    }
    if (this.commodityLoaded()) {
      this.chartScope.set('commodity');
      this.currentListingsPage.set(0);
      this.emitScopeChange('commodity');
      return;
    }
    this.commodityLoading.set(true);
    const preferredRecipeId = this.recipeId();
    this.detailService
      .loadItemDetail(
        this.region(),
        this.realmSlug(),
        this.itemId(),
        this.variant(),
        'commodity',
        undefined,
        preferredRecipeId ?? undefined,
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
          this.emitScopeChange('commodity');
        },
        error: () => {
          this.error.set(true);
        },
      });
  }

  private emitScopeChange(scope: ItemDetailScope): void {
    if (this.linkMode() === 'page') {
      this.scopeChange.emit(scope);
      return;
    }
    this.modal.updateScope(scope);
  }

  protected formatRoi(pct: number | null | undefined): string {
    if (pct == null || !Number.isFinite(pct)) return '—';
    return `${this.formatDecimal(pct, '1.1-1')}%`;
  }

  protected quantityLabel(q: number | null | undefined): string {
    if (q == null || !Number.isFinite(q)) return '—';
    return this.formatDecimal(Math.round(q), '1.0-0');
  }

  protected saleRateLabel(rate: number | null | undefined): string {
    if (rate == null || !Number.isFinite(rate)) return '—';
    return this.percentPipe.transform(rate, '1.0-1', this.locale.formatLocale()) ?? '—';
  }

  protected soldPerDayLabel(value: number | null | undefined): string {
    if (value == null || !Number.isFinite(value)) return '—';
    return this.formatDecimal(value, '1.0-2');
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

  protected showChartScopeToggle(d: AuctionMarketItemDetailResponse): boolean {
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

  protected readonly activeScopeMidPrice = computed(() => {
    const d = this.detail();
    if (!d) return null;
    const price = this.activeScopePrice(d.summary);
    return price != null && Number.isFinite(price) ? price : null;
  });

  protected readonly activeScopeP25 = computed(() => {
    const metrics = this.activeScopeMetrics();
    const p = metrics?.p25Price;
    return p != null && Number.isFinite(p) ? p : null;
  });

  protected readonly activeScopeP75 = computed(() => {
    const metrics = this.activeScopeMetrics();
    const p = metrics?.p75Price;
    return p != null && Number.isFinite(p) ? p : null;
  });

  protected readonly itemInfoRows = computed(() => {
    const d = this.detail();
    if (!d) {
      return [
        { label: this.qualityLabel, value: '—' },
        { label: this.itemClassLabel, value: '—' },
        { label: this.itemSubclassLabel, value: '—' },
        { label: this.expansionLabel, value: '—' },
        { label: this.craftedByLabel, value: '—' },
        { label: this.reagentInLabel, value: '—' },
      ];
    }
    const item = d.item;
    return [
      { label: this.qualityLabel, value: item.quality?.name?.trim() || '—' },
      { label: this.itemClassLabel, value: item.itemClass?.name?.trim() || '—' },
      { label: this.itemSubclassLabel, value: item.itemSubclass?.name?.trim() || '—' },
      { label: this.expansionLabel, value: item.expansion?.name?.trim() || '—' },
      {
        label: this.craftedByLabel,
        value: this.numberDisplay(d.craftedByRecipeCount),
      },
      {
        label: this.reagentInLabel,
        value: this.numberDisplay(d.reagentInRecipeCount),
      },
    ];
  });

  protected readonly qualityLabel = $localize`:@@itemDetail.quality:Quality`;
  protected readonly itemClassLabel = $localize`:@@itemDetail.itemClass:Item class`;
  protected readonly itemSubclassLabel = $localize`:@@itemDetail.itemSubclass:Item subclass`;
  protected readonly expansionLabel = $localize`:@@itemDetail.expansion:Expansion`;
  protected readonly craftedByLabel = $localize`:@@itemDetail.craftedByRecipes:Crafted by recipes`;
  protected readonly reagentInLabel = $localize`:@@itemDetail.reagentInRecipes:Used as reagent`;

  private activeScopeMetrics() {
    const d = this.detail();
    if (!d) return null;
    return d.regionalMetricsRedundant || this.chartScope() === 'realm'
      ? d.selectedRealm
      : d.commodity;
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
