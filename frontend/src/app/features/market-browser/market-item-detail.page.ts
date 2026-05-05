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
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import {
  ChartPanelComponent,
  CopperToCurrencyPipe,
  CurrencyAmountComponent,
  formatCopperCurrency,
  HeatmapGridComponent,
  type HeatmapCell,
  ItemStatCardComponent,
  PageFrameComponent,
  SymbolIconComponent,
  type ChartPoint,
  type ChartSeries,
} from '@ui';
import {
  AuctionMarketItemCraftingAnalyticsResponse,
  AuctionMarketItemCraftingDetail,
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

import {
  ItemDetailScope,
  ItemDetailVariantParams,
  MarketItemDetailService,
} from '@core/services/market-item-detail.service';
import { RealmSelectionService } from '@core/services/realm-selection.service';

type RegionCode = 'us' | 'eu' | 'kr' | 'tw';

interface ItemDetailBackState {
  readonly returnUrl?: string;
  readonly returnLabel?: string;
}

interface TooltipRow {
  readonly label: string;
  readonly value: string;
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
    SymbolIconComponent,
  ],
  template: `
    <ee-page-frame
      [title]="pageTitle()"
      [eyebrow]="'Item Codex'"
      [loading]="loading()"
      titleId="item-codex-title"
    >
      @if (loading()) {
        <div class="flex flex-wrap items-center gap-2" aria-hidden="true">
          <div class="h-3 w-12 rounded bg-white/10 animate-pulse"></div>
          <div class="h-3 w-20 rounded bg-white/10 animate-pulse"></div>
          <div class="h-3 w-16 rounded bg-white/10 animate-pulse"></div>
          <div class="h-3 w-28 rounded bg-white/10 animate-pulse"></div>
        </div>
      } @else {
        <nav
          class="ee-label flex flex-wrap items-center gap-x-1 gap-y-1 text-outline select-text"
          aria-label="Breadcrumb"
        >
          <a
            [routerLink]="['/', regionRealm().region, regionRealm().realm]"
            class="rounded-sm hover:text-primary focus:outline-none focus-visible:ring-2 focus-visible:ring-primary/60"
            >{{ regionRealm().regionLabel }}</a
          >
          <span aria-hidden="true">/</span>
          <a
            [routerLink]="['/', regionRealm().region, regionRealm().realm]"
            class="rounded-sm hover:text-primary focus:outline-none focus-visible:ring-2 focus-visible:ring-primary/60"
            >{{ regionRealm().realmLabel }}</a
          >
          <span aria-hidden="true">/</span>
          <a
            [routerLink]="['/', regionRealm().region, regionRealm().realm, regionRealm().marketListSegment]"
            class="rounded-sm hover:text-primary focus:outline-none focus-visible:ring-2 focus-visible:ring-primary/60"
            >{{ regionRealm().marketListLabel }}</a
          >
          <span aria-hidden="true">/</span>
          <span class="text-on-surface" aria-current="page">{{ itemTitle() }}</span>
        </nav>
      }

      <div class="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center">
        @if (loading()) {
          <div class="h-9 w-36 rounded bg-white/10 animate-pulse" aria-hidden="true"></div>
        } @else {
          <button
            type="button"
            class="inline-flex w-fit items-center gap-2 rounded border border-white/10 bg-surface-container-high px-3 py-2 ee-label text-on-surface transition hover:bg-surface-container-highest focus:outline-none focus-visible:ring-2 focus-visible:ring-primary/60"
            (click)="goBack()"
          >
            <ee-symbol-icon class="text-base" name="arrow_back" aria-hidden="true" />
            {{ backLabel() }}
          </button>
        }
        @if (detail(); as d0) {
          @if (showChartScopeToggle(d0)) {
            <div
              class="inline-flex w-fit max-w-full rounded border border-white/10 bg-surface-container-high p-0.5 ee-label"
              role="group"
              aria-label="Chart data scope"
            >
              <button
                type="button"
                class="rounded px-3 py-1.5 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-primary/60 focus-visible:ring-offset-2 focus-visible:ring-offset-surface"
                [class.bg-primary]="chartScope() === 'realm'"
                [class.text-on-primary]="chartScope() === 'realm'"
                [attr.aria-pressed]="chartScope() === 'realm'"
                [disabled]="commodityLoading()"
                (click)="onScopeSelected('realm')"
              >
                Realm
              </button>
              <button
                type="button"
                class="rounded px-3 py-1.5 transition focus:outline-none focus-visible:ring-2 focus-visible:ring-primary/60 focus-visible:ring-offset-2 focus-visible:ring-offset-surface"
                [class.bg-primary]="chartScope() === 'commodity'"
                [class.text-on-primary]="chartScope() === 'commodity'"
                [attr.aria-pressed]="chartScope() === 'commodity'"
                [disabled]="commodityLoading()"
                (click)="onScopeSelected('commodity')"
              >
                Region
              </button>
            </div>
          }
        }
      </div>

      @if (loading()) {
        <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-hidden="true">
          @for (i of skeletonCards; track i) {
            <div class="ee-glass rounded-lg p-inner-padding">
              <div class="mb-5 flex items-start justify-between gap-4">
                <div class="h-3 w-24 rounded bg-white/10 animate-pulse"></div>
                <div class="h-8 w-8 rounded-full bg-white/10 animate-pulse"></div>
              </div>
              <div class="h-7 w-32 rounded bg-white/10 animate-pulse"></div>
              <div class="mt-3 h-3 w-28 rounded bg-white/10 animate-pulse"></div>
            </div>
          }
        </div>
        <div class="space-y-4" aria-hidden="true">
          @for (i of skeletonCharts; track i) {
            <section class="ee-glass rounded-lg p-inner-padding">
              <div class="mb-6 flex items-center justify-between gap-4">
                <div class="h-5 w-36 rounded bg-white/10 animate-pulse"></div>
                <div class="h-3 w-16 rounded bg-white/10 animate-pulse"></div>
              </div>
              <div class="relative h-64 overflow-hidden border-b border-l border-white/10">
                <div class="absolute inset-x-5 top-1/4 h-px bg-white/10"></div>
                <div class="absolute inset-x-5 top-1/2 h-px bg-white/10"></div>
                <div class="absolute inset-x-5 top-3/4 h-px bg-white/10"></div>
                <div class="absolute inset-x-6 bottom-4 flex h-36 items-end gap-2">
                  @for (bar of skeletonBars; track bar.index) {
                    <div
                      class="w-full rounded-t bg-white/10 animate-pulse"
                      [style.height.%]="bar.height"
                    ></div>
                  }
                </div>
              </div>
            </section>
          }
        </div>
      } @else if (error()) {
        <div class="ee-glass rounded-lg border border-error/40 p-inner-padding text-error">
          Could not load this item. Try again from the market browser.
        </div>
      } @else if (detail(); as d) {
        <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <ee-item-stat-card
            [label]="activeScopeLabel() + ' price'"
            icon="payments"
            [currency]="activeScopePrice(d.summary) | copperToCurrency"
            [caption]="activeScopePriceCaption(d.summary)"
            tone="primary"
          />
          <ee-item-stat-card
            [label]="activeScopeLabel() + ' quantity'"
            icon="inventory_2"
            [value]="quantityLabel(activeScopeQuantity(d.summary))"
            [caption]="''"
          />
        </div>

        <p class="ee-data text-on-surface-variant select-text">
          Snapshot: realm {{ d.selectedRealm.date ?? '—' }}, hour
          {{ d.selectedRealm.hourOfDay ?? '—' }}
          @if (showCommoditySnapshotLine(d) && chartScope() === 'commodity') {
            <span>
              · commodity {{ d.commodity.date ?? '—' }}, hour
              {{ d.commodity.hourOfDay ?? '—' }}</span
            >
          }
        </p>

        @if (d.marketDataSources.length) {
          <p class="ee-label text-outline select-text">
            Data version:
            @for (s of d.marketDataSources; track s.connectedRealmId) {
              <span class="ml-2 font-mono text-[0.7rem]">{{
                s.auctionHouseLastModified ?? '—'
              }}</span>
            }
          </p>
        }

        <ng-template #dailyChartTip let-ctx>
          <div
            class="ee-glass min-w-64 rounded-md border border-white/15 bg-surface-container/95 px-3 py-2 text-left text-xs text-on-surface shadow-lg backdrop-blur-md"
          >
            <div class="ee-label text-outline mb-1.5">{{ dailyTooltipTitle(d, ctx.x) }}</div>
            <div class="space-y-1 font-space-mono text-[11px]">
              @for (row of dailyTooltipRows(d, ctx.x); track row.label) {
                <div class="flex justify-between gap-4">
                  <span class="text-outline">{{ row.label }}</span>
                  <span class="text-on-surface">{{ row.value }}</span>
                </div>
              }
              @if (!dailyTooltipRows(d, ctx.x).length) {
                <div class="text-outline">No values for this day</div>
              }
            </div>
          </div>
        </ng-template>

        <ng-template #hourlyChartTip let-ctx>
          <div
            class="ee-glass min-w-64 rounded-md border border-white/15 bg-surface-container/95 px-3 py-2 text-left text-xs text-on-surface shadow-lg backdrop-blur-md"
          >
            <div class="ee-label text-outline mb-1.5">{{ hourlyTooltipTitle(d, ctx.x) }}</div>
            <div class="space-y-1 font-space-mono text-[11px]">
              @for (row of hourlyTooltipRows(d, ctx.x); track row.label) {
                <div class="flex justify-between gap-4">
                  <span class="text-outline">{{ row.label }}</span>
                  <span class="text-on-surface">{{ row.value }}</span>
                </div>
              }
              @if (!hourlyTooltipRows(d, ctx.x).length) {
                <div class="text-outline">No values for this hour</div>
              }
            </div>
          </div>
        </ng-template>

        <ee-chart-panel
          title="Daily market"
          rangeLabel="14 days"
          [series]="dailyChartSeries()"
          [tooltipTemplate]="dailyChartTip"
          description="Average quantity per hour as bars; buyout spread and average as lines."
        />

        <ee-chart-panel
          title="Hourly market"
          rangeLabel="14 days"
          [series]="hourlyChartSeries()"
          [tooltipTemplate]="hourlyChartTip"
          description="Listed quantity per hour over 14 days as bars; buyout spread and average as lines."
        />

        @if (d.craftings.length) {
          <section class="ee-glass rounded-lg p-inner-padding">
            <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
              <h2 class="ee-section-heading flex items-center gap-2 text-on-surface">
                <ee-symbol-icon class="text-outline" name="handyman" />
                Crafting
              </h2>
              @if (analyticsLoading()) {
                <span class="ee-label text-outline">Loading recipe analytics…</span>
              }
            </div>

            @if (d.craftings.length > 1) {
              <div class="mb-4 flex flex-wrap gap-2" role="tablist" aria-label="Recipes">
                @for (recipe of d.craftings; track recipe.recipeId) {
                  <button
                    type="button"
                    class="rounded border border-white/10 px-3 py-1.5 ee-label transition focus:outline-none focus-visible:ring-2 focus-visible:ring-primary/60"
                    [class.bg-primary]="recipe.recipeId === selectedRecipeId()"
                    [class.text-on-primary]="recipe.recipeId === selectedRecipeId()"
                    [class.bg-surface-container-high]="recipe.recipeId !== selectedRecipeId()"
                    [attr.aria-selected]="recipe.recipeId === selectedRecipeId()"
                    role="tab"
                    (click)="selectRecipe(recipe.recipeId)"
                  >
                    {{ recipe.recipeName }}
                  </button>
                }
              </div>
            }

            @if (selectedCrafting(); as crafting) {
              <div class="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(18rem,24rem)]">
                <div class="overflow-x-auto">
                  <table class="w-full border-collapse ee-data text-left text-on-surface">
                    <thead>
                      <tr class="border-b border-white/10 ee-label text-outline">
                        <th class="py-2 pr-4">Reagent</th>
                        <th class="py-2 pr-4 text-right">Qty</th>
                        <th class="py-2 pr-4 text-right">Unit price</th>
                        <th class="py-2 text-right">Line total</th>
                      </tr>
                    </thead>
                    <tbody>
                      @for (reagent of crafting.reagents; track reagent.itemId) {
                        <tr class="border-b border-white/5">
                          <td class="py-3 pr-4 select-text">
                            <div class="flex items-center gap-2">
                              @if (reagent.mediaUrl) {
                                <img class="h-6 w-6 rounded" [src]="reagent.mediaUrl" alt="" />
                              }
                              <span>{{ reagent.name }}</span>
                              @if (!reagent.priced) {
                                <span class="rounded bg-error/15 px-2 py-0.5 ee-label text-error">missing price</span>
                              }
                            </div>
                          </td>
                          <td class="py-3 pr-4 text-right tabular-nums select-text">{{ reagent.quantity }}</td>
                          <td class="py-3 pr-4 text-right tabular-nums select-text">
                            <ee-currency-amount class="inline-flex justify-end" [amount]="reagent.unitPrice | copperToCurrency" />
                          </td>
                          <td class="py-3 text-right tabular-nums select-text">
                            <ee-currency-amount class="inline-flex justify-end" [amount]="reagent.lineTotal | copperToCurrency" />
                          </td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>

                <div class="rounded border border-white/10 bg-surface-container-high/60 p-4">
                  <div class="ee-label mb-3 text-outline">{{ crafting.recipeName }}</div>
                  <div class="space-y-2 ee-data text-on-surface">
                    <div class="flex justify-between gap-4"><span>Crafted qty</span><span>{{ crafting.craftedQuantity }}</span></div>
                    <div class="flex justify-between gap-4"><span>Reagent cost</span><ee-currency-amount [amount]="crafting.reagentCost | copperToCurrency" /></div>
                    <div class="flex justify-between gap-4"><span>Output unit</span><ee-currency-amount [amount]="crafting.outputUnitPrice | copperToCurrency" /></div>
                    <div class="flex justify-between gap-4"><span>Profit</span><ee-currency-amount [amount]="crafting.profit | copperToCurrency" /></div>
                    <div class="flex justify-between gap-4"><span>ROI</span><span>{{ formatRoi(crafting.roiPercent) }}</span></div>
                  </div>
                  @if (!crafting.reagentsFullyPriced) {
                    <p class="ee-label mt-3 text-error">Profit hidden until all reagents have prices.</p>
                  }
                </div>
              </div>
            }
          </section>

          @if (craftingAnalytics(); as analytics) {
            <ee-chart-panel
              title="Crafting profit / ROI"
              rangeLabel="14 days"
              [series]="craftingAnalyticsSeries()"
              description="Daily profit and ROI for selected recipe. Missing points mean incomplete pricing."
            />
            <ng-template #heatmapTip let-cell="cell" let-rowLabel="rowLabel" let-columnLabel="columnLabel">
              <div class="ee-label text-outline">{{ rowLabel }} · {{ columnLabel }}:00</div>
              <div class="font-space-mono">{{ cell.label }}</div>
            </ng-template>
            <ee-heatmap-grid
              title="Crafting profit heatmap"
              rangeLabel="14 days"
              description="Average profit by day and hour for selected recipe."
              [rowLabels]="heatmapRowLabels"
              [columnLabels]="heatmapColumnLabels"
              [cells]="heatmapCells()"
              [tooltipTemplate]="heatmapTip"
            />
          } @else if (analyticsError()) {
            <div class="ee-glass rounded-lg border border-error/40 p-inner-padding text-error">
              Could not load crafting analytics.
            </div>
          }
        }
      }
    </ee-page-frame>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarketItemDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly detailService = inject(MarketItemDetailService);
  private readonly realmSelection = inject(RealmSelectionService);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly decimalPipe = new DecimalPipe('en-US');

  protected readonly loading = signal(true);
  protected readonly commodityLoading = signal(false);
  protected readonly error = signal(false);
  protected readonly detail = signal<AuctionMarketItemDetailResponse | null>(null);
  protected readonly commodityLoaded = signal(false);
  protected readonly chartScope = signal<'realm' | 'commodity'>('realm');
  protected readonly selectedRecipeId = signal<number | null>(null);
  protected readonly craftingAnalytics = signal<AuctionMarketItemCraftingAnalyticsResponse | null>(null);
  protected readonly analyticsLoading = signal(false);
  protected readonly analyticsError = signal(false);
  protected readonly heatmapRowLabels = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'] as const;
  protected readonly heatmapColumnLabels = Array.from({ length: 24 }, (_, h) => String(h).padStart(2, '0'));
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
        marketListLabel: 'Auctions',
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
    const d = this.detail();
    if (!d) return [];
    const pts =
      d.regionalMetricsRedundant || this.chartScope() === 'realm'
        ? d.dailySeriesRealm
        : d.dailySeriesCommodity;
    return dailyPointsToChartSeries(pts);
  });

  protected readonly hourlyChartSeries = computed(() => {
    const d = this.detail();
    if (!d) return [];
    const pts =
      d.regionalMetricsRedundant || this.chartScope() === 'realm'
        ? d.hourlySeriesRealm
        : d.hourlySeriesCommodity;
    return hourlyPointsToChartSeries(pts);
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

  protected readonly heatmapCells = computed<HeatmapCell[]>(() =>
    (this.craftingAnalytics()?.heatmap ?? []).map((cell) => ({
      row: cell.dayOfWeek,
      col: cell.hourOfDay,
      value: cell.profit,
      label: `${formatCopperCurrency(cell.profit)} · ROI ${this.formatRoi(cell.roiPercent)} · n=${cell.sampleCount}`,
    })),
  );

  constructor() {
    if (isPlatformBrowser(this.platformId)) {
      const raw = window.history.state as Record<string, unknown> | null;
      this.backState.set({
        returnUrl: typeof raw?.['returnUrl'] === 'string' ? raw['returnUrl'] : undefined,
        returnLabel: typeof raw?.['returnLabel'] === 'string' ? raw['returnLabel'] : undefined,
      });
    }

    const realmRoute = realmAncestorRoute(this.route);

    combineLatest([realmRoute.paramMap, this.route.paramMap, this.route.data, this.route.queryParamMap])
      .pipe(
        map(([realmPm, itemPm, data, q]) => ({
          region: realmPm.get('region'),
          realmSlug: realmPm.get('realm'),
          itemId: Number(itemPm.get('itemId')),
          recipeId: itemPm.get('recipeId'),
          listSegment: (data['marketListSegment'] as string | undefined) ?? 'auctions',
          listLabel: (data['marketListLabel'] as string | undefined) ?? 'Auctions',
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
          this.craftingAnalytics.set(null);
          this.analyticsError.set(false);
          this.selectedRecipeId.set(null);
          this.chartScope.set(ctx.initialScope);
          const preferredRecipeId =
            ctx.listSegment === 'crafting' && ctx.recipeId ? Number(ctx.recipeId) : undefined;
          return this.detailService
            .loadItemDetail(ctx.region, ctx.realmSlug, ctx.itemId, ctx.variant, ctx.initialScope, undefined, preferredRecipeId)
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
    return this.backState().returnLabel ?? 'Back to market';
  }

  protected selectRecipe(recipeId: number): void {
    if (this.selectedRecipeId() === recipeId) return;
    this.selectedRecipeId.set(recipeId);
    this.loadSelectedRecipeAnalytics();
  }

  private loadSelectedRecipeAnalytics(): void {
    const ctx = this.routeCtx();
    const recipeId = this.selectedRecipeId();
    if (!ctx || recipeId == null) return;
    this.analyticsLoading.set(true);
    this.analyticsError.set(false);
    this.craftingAnalytics.set(null);
    this.detailService
      .loadCraftingAnalytics(ctx.region, ctx.realmSlug, ctx.itemId, recipeId, ctx.variant)
      .pipe(
        finalize(() => this.analyticsLoading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (analytics) => this.craftingAnalytics.set(analytics),
        error: () => this.analyticsError.set(true),
      });
  }

  protected onScopeSelected(scope: ItemDetailScope): void {
    if (scope === 'realm') {
      this.chartScope.set('realm');
      return;
    }
    if (this.commodityLoaded()) {
      this.chartScope.set('commodity');
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
          this.commodityLoaded.set(true);
          this.chartScope.set('commodity');
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

  protected dailyTooltipTitle(d: AuctionMarketItemDetailResponse, x: number): string {
    const point = this.dailyTooltipPoint(d, x);
    return point?.statDate ? point.statDate : `Day ${Math.round(x) + 1}`;
  }

  protected dailyTooltipRows(d: AuctionMarketItemDetailResponse, x: number): TooltipRow[] {
    const point = this.dailyTooltipPoint(d, x);
    if (!point) return [];
    return [
      { label: 'date', value: point.statDate ?? '—' },
      { label: 'avg quantity', value: this.numberDisplay(point.avgQuantity) },
      { label: 'min quantity', value: this.numberDisplay(point.minQuantity) },
      { label: 'max quantity', value: this.numberDisplay(point.maxQuantity) },
      { label: 'min price', value: formatCopperCurrency(point.minPrice) },
      { label: 'p25 price', value: formatCopperCurrency(point.p25Price) },
      { label: 'avg price', value: formatCopperCurrency(point.avgPrice) },
      { label: 'p75 price', value: formatCopperCurrency(point.p75Price) },
      { label: 'max price', value: formatCopperCurrency(point.maxPrice) },
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
      { label: 'hour', value: `${String(point.hourOfDay).padStart(2, '0')}:00` },
      { label: 'timestamp', value: point.timestamp ?? '—' },
      { label: 'quantity / hour', value: this.numberDisplay(point.totalQuantity) },
      { label: 'min price', value: formatCopperCurrency(point.minPrice) },
      { label: 'p25 price', value: formatCopperCurrency(point.p25Price) },
      { label: 'avg price', value: formatCopperCurrency(point.avgPrice) },
      { label: 'p75 price', value: formatCopperCurrency(point.p75Price) },
      { label: 'max price', value: formatCopperCurrency(point.maxPrice) },
    ];
  }

  protected priceChangeCaption(pct: number | null | undefined): string {
    if (pct == null || !Number.isFinite(pct)) return '';
    const sign = pct > 0 ? '+' : '';
    return `${sign}${this.formatDecimal(pct, '1.1-1')}% vs prior day`;
  }

  protected realmVsCommodityCaption(pct: number | null | undefined): string {
    if (pct == null || !Number.isFinite(pct)) return '';
    const sign = pct > 0 ? '+' : '';
    return `${sign}${this.formatDecimal(pct, '1.1-1')}% vs commodity`;
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
      ? 'Region'
      : 'Realm';
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

  private dailyTooltipPoint(
    d: AuctionMarketItemDetailResponse,
    x: number,
  ): AuctionMarketItemDetailPoint | undefined {
    const points =
      d.regionalMetricsRedundant || this.chartScope() === 'realm'
        ? d.dailySeriesRealm
        : d.dailySeriesCommodity;
    return points[Math.round(x)];
  }

  private hourlyTooltipPoint(
    d: AuctionMarketItemDetailResponse,
    x: number,
  ): AuctionMarketItemHourlyPoint | undefined {
    const points =
      d.regionalMetricsRedundant || this.chartScope() === 'realm'
        ? d.hourlySeriesRealm
        : d.hourlySeriesCommodity;
    if (points.length === 0) return undefined;
    const i = Math.max(0, Math.min(points.length - 1, Math.round(x)));
    return points[i];
  }

  private numberDisplay(value: number | null | undefined): string {
    if (value == null || !Number.isFinite(value)) return '—';
    return this.formatDecimal(Math.round(value), '1.0-0');
  }

  private formatDecimal(value: number, digitsInfo: string): string {
    return (
      this.decimalPipe.transform(value, digitsInfo, this.selectedLocaleForNumberPipe()) ??
      String(value)
    );
  }

  private selectedLocaleForNumberPipe(): string | undefined {
    return this.realmSelection.selected()?.locale?.replace('_', '-');
  }
}

function realmAncestorRoute(route: ActivatedRoute): ActivatedRoute {
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

function variantFromQuery(q: ParamMap): ItemDetailVariantParams {
  return {
    bonusKey: q.get('bonusKey') ?? '',
    modifierKey: q.get('modifierKey') ?? '',
    petSpeciesId: Number(q.get('petSpeciesId') ?? 0) || 0,
  };
}

function variantEqual(a: ItemDetailVariantParams, b: ItemDetailVariantParams): boolean {
  return (
    a.bonusKey === b.bonusKey &&
    a.modifierKey === b.modifierKey &&
    a.petSpeciesId === b.petSpeciesId
  );
}

function scopeFromQuery(q: ParamMap): ItemDetailScope {
  return q.get('scope') === 'commodity' ? 'commodity' : 'realm';
}

function isRegion(value: string | null | undefined): value is RegionCode {
  return value === 'us' || value === 'eu' || value === 'kr' || value === 'tw';
}

function formatRealmLabel(slug: string): string {
  const t = slug.replace(/-/g, ' ');
  return t.length ? t.charAt(0).toUpperCase() + t.slice(1) : slug;
}

function showChartScopeToggleFn(d: AuctionMarketItemDetailResponse): boolean {
  return !d.regionalMetricsRedundant && hasRealmScopeMetrics(d.summary);
}

function shouldUseCommodityScopeByDefault(d: AuctionMarketItemDetailResponse): boolean {
  return d.regionalMetricsRedundant || !hasRealmScopeMetrics(d.summary);
}

function shouldFallbackToCommodityFetch(d: AuctionMarketItemDetailResponse): boolean {
  return (
    !d.regionalMetricsRedundant &&
    !hasRealmScopeMetrics(d.summary) &&
    !hasCommodityScopeMetrics(d.summary)
  );
}

function hasRealmScopeMetrics(summary: AuctionMarketItemDetailSummary): boolean {
  const realmPrice = summary.selectedRealmPrice;
  const realmQty = summary.selectedRealmQuantity;
  return (
    (realmPrice != null && Number.isFinite(realmPrice)) ||
    (realmQty != null && Number.isFinite(realmQty))
  );
}

function hasCommodityScopeMetrics(summary: AuctionMarketItemDetailSummary): boolean {
  const commodityPrice = summary.commodityPrice;
  const commodityQty = summary.commodityQuantity;
  return (
    (commodityPrice != null && Number.isFinite(commodityPrice)) ||
    (commodityQty != null && Number.isFinite(commodityQty))
  );
}

function priceChangeCaptionStatic(pct: number | null | undefined): string {
  if (pct == null || !Number.isFinite(pct)) return '';
  const sign = pct > 0 ? '+' : '';
  return `${sign}${pct.toFixed(1)}% vs prior day`;
}

function mergeCommodityScope(
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

/**
 * One pass per `dailySeries*` row: bars use `avgQuantity`, lines use price fields from the same object.
 * `ee-chart-panel` uses separate `yScaleKey`s (`price` vs `quantity`) from `chart.ts`.
 */
function dailyPointsToChartSeries(rows: readonly AuctionMarketItemDetailPoint[]): ChartSeries[] {
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
  if (lowerPts.length > 0) {
    series.push({
      id: 'low',
      kind: 'line',
      yScaleKey: 'price',
      color: 'secondary',
      points: lowerPts,
    });
  }
  if (midPts.length > 0) {
    series.push({
      id: 'mid',
      kind: 'line',
      yScaleKey: 'price',
      color: 'primary-container',
      points: midPts,
    });
  }
  if (upperPts.length > 0) {
    series.push({ id: 'high', kind: 'line', yScaleKey: 'price', color: 'error', points: upperPts });
  }
  return series;
}

function craftingAnalyticsToChartSeries(analytics: AuctionMarketItemCraftingAnalyticsResponse): ChartSeries[] {
  const profitPts: ChartPoint[] = [];
  const roiPts: ChartPoint[] = [];
  analytics.dailySeries.forEach((point, index) => {
    if (point.profit != null && Number.isFinite(point.profit)) profitPts.push({ x: index, y: point.profit });
    if (point.roiPercent != null && Number.isFinite(point.roiPercent)) roiPts.push({ x: index, y: point.roiPercent });
  });
  const series: ChartSeries[] = [];
  if (profitPts.length) {
    series.push({ id: 'profit', kind: 'column', yScaleKey: 'profit', color: 'primary-container', points: profitPts });
  }
  if (roiPts.length) {
    series.push({ id: 'roi', kind: 'line', yScaleKey: 'roi', color: 'secondary', points: roiPts });
  }
  return series;
}

function hourlyPointsToChartSeries(rows: readonly AuctionMarketItemHourlyPoint[]): ChartSeries[] {
  if (rows.length === 0) return [];

  const sorted = [...rows].sort((a, b) => {
    const ta = Date.parse(a.timestamp ?? '');
    const tb = Date.parse(b.timestamp ?? '');
    if (Number.isFinite(ta) && Number.isFinite(tb)) return ta - tb;
    if (Number.isFinite(ta)) return -1;
    if (Number.isFinite(tb)) return 1;
    return a.hourOfDay - b.hourOfDay;
  });
  const qtyPts: ChartPoint[] = [];
  const lowerPts: ChartPoint[] = [];
  const midPts: ChartPoint[] = [];
  const upperPts: ChartPoint[] = [];

  for (let i = 0; i < sorted.length; i++) {
    const p = sorted[i]!;
    const x = i;

    const q = p.totalQuantity;
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
  if (lowerPts.length > 0) {
    series.push({
      id: 'low',
      kind: 'line',
      yScaleKey: 'price',
      color: 'secondary',
      points: lowerPts,
    });
  }
  if (midPts.length > 0) {
    series.push({
      id: 'mid',
      kind: 'line',
      yScaleKey: 'price',
      color: 'primary-container',
      points: midPts,
    });
  }
  if (upperPts.length > 0) {
    series.push({ id: 'high', kind: 'line', yScaleKey: 'price', color: 'error', points: upperPts });
  }
  return series;
}
