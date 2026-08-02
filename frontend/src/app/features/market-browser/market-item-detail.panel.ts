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
  CurrencyAmountComponent,
  formatCopperCurrency,
  HeatmapGridComponent,
  type HeatmapCell,
  ItemStatCardComponent,
  PaginationComponent,
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
  shouldFallbackToCommodityFetch,
  shouldUseCommodityScopeByDefault,
  showChartScopeToggleFn,
  sortHourlyPoints,
  type RegionCode,
} from './market-item-detail.helpers';
import {
  activeScopePrice,
  activeScopePriceCaption,
  activeScopeQuantity,
  currentListingsPagination,
  dailyTooltipRows,
  hourlyTooltipRows,
  itemInfoRows,
  marketDetailChartOptions,
  marketDetailPanelContextsEqual,
  pagedCurrentListings,
} from './market-item-detail-panel.helpers';

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
  protected readonly heatmapColumnLabels = Array.from({ length: 24 }, (_, hour) =>
    String(hour).padStart(2, '0'),
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
    const points = this.hourlyPointsForActiveScope();
    return hourlyPointsToChartSeries(points);
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
    return (
      craftings.find((crafting) => crafting.recipeId === this.selectedRecipeId()) ?? craftings[0]
    );
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

  protected readonly currentListingsPagination = computed(() =>
    currentListingsPagination(
      this.currentListingsForActiveScope().length,
      this.currentListingsPage(),
      this.currentListingsPageSize,
    ),
  );

  protected readonly pagedCurrentListings = computed(() => {
    return pagedCurrentListings(
      this.currentListingsForActiveScope(),
      this.currentListingsPagination(),
    );
  });

  constructor() {
    toObservable(this.panelCtx)
      .pipe(
        distinctUntilChanged(marketDetailPanelContextsEqual),
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
      .subscribe((response) => {
        if (response) {
          this.detail.set(response);
          this.titleChange.emit(response.item.name);
          this.selectedRecipeId.set(response.craftings[0]?.recipeId ?? null);
          this.loadSelectedRecipeAnalytics();
          if (shouldFallbackToCommodityFetch(response)) {
            this.storeCurrentListings(response.currentListings, this.chartScope());
            this.onScopeSelected('commodity');
            return;
          }
          const scope: ItemDetailScope = shouldUseCommodityScopeByDefault(response)
            ? 'commodity'
            : 'realm';
          this.chartScope.set(scope);
          this.storeCurrentListings(response.currentListings, scope);
          if (scope === 'commodity' && response.regionalMetricsRedundant) {
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
      craftings.findIndex((crafting) => crafting.recipeId === current),
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

  protected quantityLabel(quantity: number | null | undefined): string {
    if (quantity == null || !Number.isFinite(quantity)) return '—';
    return this.formatDecimal(Math.round(quantity), '1.0-0');
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

  protected dailyTooltipTitle(
    detail: AuctionMarketItemDetailResponse,
    domainValue: number,
  ): string {
    const point = this.dailyTooltipPoint(detail, domainValue);
    return point?.statDate
      ? point.statDate
      : $localize`:@@itemDetail.dayLabel:Day ${Math.round(domainValue) + 1}`;
  }

  protected dailyTooltipRows(
    detail: AuctionMarketItemDetailResponse,
    domainValue: number,
  ): TooltipRow[] {
    return dailyTooltipRows(this.dailyTooltipPoint(detail, domainValue), (value) =>
      this.numberDisplay(value),
    );
  }

  protected hourlyTooltipTitle(
    detail: AuctionMarketItemDetailResponse,
    domainValue: number,
  ): string {
    const point = this.hourlyTooltipPoint(detail, domainValue);
    const hour = point?.hourOfDay ?? 0;
    const prefix = `${String(hour).padStart(2, '0')}:00`;
    return point?.timestamp ? `${point.timestamp} · ${prefix}` : prefix;
  }

  protected hourlyTooltipRows(
    detail: AuctionMarketItemDetailResponse,
    domainValue: number,
  ): TooltipRow[] {
    return hourlyTooltipRows(this.hourlyTooltipPoint(detail, domainValue), (value) =>
      this.numberDisplay(value),
    );
  }

  protected showChartScopeToggle(detail: AuctionMarketItemDetailResponse): boolean {
    return showChartScopeToggleFn(detail);
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

  protected activeScopePrice(summary: AuctionMarketItemDetailSummary): number | null | undefined {
    return activeScopePrice(summary, this.chartScope());
  }

  protected activeScopeQuantity(
    summary: AuctionMarketItemDetailSummary,
  ): number | null | undefined {
    return activeScopeQuantity(summary, this.chartScope());
  }

  protected activeScopePriceCaption(summary: AuctionMarketItemDetailSummary): string {
    return activeScopePriceCaption(summary, this.chartScope());
  }

  protected readonly activeScopeMidPrice = computed(() => {
    const detail = this.detail();
    if (!detail) return null;
    const price = this.activeScopePrice(detail.summary);
    return price != null && Number.isFinite(price) ? price : null;
  });

  protected readonly activeScopeP25 = computed(() => {
    const metrics = this.activeScopeMetrics();
    const price = metrics?.p25Price;
    return price != null && Number.isFinite(price) ? price : null;
  });

  protected readonly activeScopeP75 = computed(() => {
    const metrics = this.activeScopeMetrics();
    const price = metrics?.p75Price;
    return price != null && Number.isFinite(price) ? price : null;
  });

  protected readonly itemInfoRows = computed(() => {
    return itemInfoRows(
      this.detail(),
      {
        quality: this.qualityLabel,
        itemClass: this.itemClassLabel,
        itemSubclass: this.itemSubclassLabel,
        expansion: this.expansionLabel,
        craftedBy: this.craftedByLabel,
        reagentIn: this.reagentInLabel,
      },
      (value) => this.numberDisplay(value),
    );
  });

  protected readonly qualityLabel = $localize`:@@itemDetail.quality:Quality`;
  protected readonly itemClassLabel = $localize`:@@itemDetail.itemClass:Item class`;
  protected readonly itemSubclassLabel = $localize`:@@itemDetail.itemSubclass:Item subclass`;
  protected readonly expansionLabel = $localize`:@@itemDetail.expansion:Expansion`;
  protected readonly craftedByLabel = $localize`:@@itemDetail.craftedByRecipes:Crafted by recipes`;
  protected readonly reagentInLabel = $localize`:@@itemDetail.reagentInRecipes:Used as reagent`;

  private activeScopeMetrics() {
    const detail = this.detail();
    if (!detail) return null;
    return detail.regionalMetricsRedundant || this.chartScope() === 'realm'
      ? detail.selectedRealm
      : detail.commodity;
  }

  private chartOptionsForSeries(
    series: Parameters<typeof marketDetailChartOptions>[0],
    labels: { readonly xLabelAt?: (index: number) => string } = {},
  ): Highcharts.Options {
    return marketDetailChartOptions(series, this.locale.formatLocale(), labels);
  }

  private dailyTooltipPoint(
    detail: AuctionMarketItemDetailResponse,
    domainValue: number,
  ): AuctionMarketItemDetailPoint | undefined {
    return this.dailyPointsForActiveScope(detail)[Math.round(domainValue)];
  }

  private hourlyTooltipPoint(
    _detail: AuctionMarketItemDetailResponse,
    domainValue: number,
  ): AuctionMarketItemHourlyPoint | undefined {
    const points = this.hourlyPointsForActiveScope();
    if (points.length === 0) return undefined;
    const index = Math.max(0, Math.min(points.length - 1, Math.round(domainValue)));
    return points[index];
  }

  private hourlyPointsForActiveScope(): AuctionMarketItemHourlyPoint[] {
    const detail = this.detail();
    if (!detail) return [];
    const points =
      detail.regionalMetricsRedundant || this.chartScope() === 'realm'
        ? detail.hourlySeriesRealm
        : detail.hourlySeriesCommodity;
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
