import { isPlatformBrowser } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  PLATFORM_ID,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { debounceTime, distinctUntilChanged, fromEvent, map, startWith } from 'rxjs';

import {
  FilterOptionChanged,
  FilterPanelComponent,
  FilterRangeChanged,
  MarketItemRow,
  PageFrameComponent,
  SearchInputComponent,
  SortingState,
  SymbolIconComponent,
  TableComponent,
} from '@ui';

import {
  createMarketBrowserTableColumns,
  marketBrowserHeaderRowClass,
  marketBrowserRowClass,
  marketBrowserRowGridTemplateColumns,
  marketBrowserSkeletonRowClass,
} from './market-browser-table.columns';
import { AuctionItemService } from '@core/services/auction-item.service';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

const DEFAULT_VIEWPORT_WIDTH = 1280;
const CARD_VIEW_MAX_WIDTH = 1023;
const CLASS_MIN_WIDTH = 860;
const SUBCLASS_MIN_WIDTH = 1040;
const QUALITY_MIN_WIDTH = 1200;

@Component({
  selector: 'app-market-browser-page',
  host: {
    class: 'flex min-h-0 flex-1 overflow-hidden',
  },
  imports: [
    FilterPanelComponent,
    PageFrameComponent,
    SearchInputComponent,
    SymbolIconComponent,
    TableComponent,
    ReactiveFormsModule,
  ],
  templateUrl: 'market-browser.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarketBrowserPage {
  readonly searchField = new FormControl();
  protected readonly mobileNavOpen = signal(false);
  protected readonly mobileFiltersOpen = signal(false);
  protected readonly viewportWidth = signal(DEFAULT_VIEWPORT_WIDTH);
  protected readonly marketColumns = createMarketBrowserTableColumns();
  protected readonly cardView = computed(() => this.viewportWidth() <= CARD_VIEW_MAX_WIDTH);
  protected readonly activeMarketColumns = computed(() =>
    this.marketColumns.filter((column) =>
      activeColumnIdsForViewport(this.viewportWidth()).has(String(column.id ?? '')),
    ),
  );
  protected readonly marketTableColumns = computed(() =>
    this.cardView() ? this.marketColumns : this.activeMarketColumns(),
  );
  protected readonly marketRowGridTemplate = computed(() =>
    marketBrowserRowGridTemplateColumns(this.marketTableColumns()),
  );
  protected readonly marketRowClass = marketBrowserRowClass;
  protected readonly marketTableMinWidth = 'min-w-0 w-full';
  protected readonly marketTableHeaderRow = marketBrowserHeaderRowClass();
  protected readonly marketSkeletonRowClass = marketBrowserSkeletonRowClass();
  protected readonly marketMobileSortOptions = [
    { id: 'itemName', label: $localize`:@@market.column.item:Item` },
    { id: 'selectedPrice', label: $localize`:@@market.column.price:Price` },
    { id: 'selectedQuantity', label: $localize`:@@market.column.quantity:Quantity` },
    { id: 'quality', label: $localize`:@@market.column.quality:Quality` },
    { id: 'itemClass', label: $localize`:@@market.column.class:Class` },
    { id: 'itemSubclass', label: $localize`:@@market.column.subclass:Subclass` },
  ];
  private readonly auctionService = inject(AuctionItemService);
  protected readonly marketTableSorting = computed<SortingState>(() => {
    const params = this.auctionService.queryParams();
    if (!params) return [];
    const { sortBy, sortDirection } = params;
    return [{ id: sortBy, desc: sortDirection === 'desc' }];
  });
  readonly currentRows = this.auctionService.currentRows;
  readonly filterSections = this.auctionService.filterSections;
  readonly pageSize = computed(() => this.auctionService.pageData()?.page?.pageSize ?? 25);
  readonly paginationState = this.auctionService.paginationState;
  readonly isLoading = this.auctionService.isLoading;
  readonly loadingPaginationSummary = $localize`:@@market.loadingItems:Loading market items...`;
  readonly emptyPaginationSummary = $localize`:@@market.pagination.empty:No market items available.`;
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly platformId = inject(PLATFORM_ID);

  constructor() {
    if (isPlatformBrowser(this.platformId)) {
      fromEvent(window, 'resize')
        .pipe(
          startWith(null),
          map(() => window.innerWidth),
          distinctUntilChanged(),
          takeUntilDestroyed(this.destroyRef),
        )
        .subscribe((width) => {
          this.viewportWidth.set(width);
        });
    }

    this.searchField.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((query) => {
        this.auctionService.setSearchQuery(query ?? '');
      });

    effect(() => {
      const query = this.auctionService.queryParams()?.query ?? '';
      if (this.searchField.value !== query) {
        this.searchField.setValue(query, { emitEvent: false });
      }
    });
  }

  protected readonly marketRowId = (row: MarketItemRow) => row.id;

  protected pageTitle(): string {
    return $localize`:@@market.title:Market Browser`;
  }

  protected pageEyebrow(): string {
    return $localize`:@@market.eyebrow:Search the auction house`;
  }

  protected filterOptionsLabel(): string {
    return $localize`:@@filters.options:Filter options`;
  }

  protected onFilterToggled(optionId: string): void {
    this.auctionService.toggleFilter(optionId);
  }

  protected onFilterSelected(change: FilterOptionChanged): void {
    this.auctionService.selectFilter(change.sectionId, change.optionId);
  }

  protected onRangeChanged(change: FilterRangeChanged): void {
    this.auctionService.setRangeFilter(change.sectionId, change.bound, change.value);
  }

  protected onFiltersReset(): void {
    this.auctionService.resetFilters();
  }

  protected openMobileFilters(): void {
    this.mobileFiltersOpen.set(true);
  }

  protected closeMobileFilters(): void {
    this.mobileFiltersOpen.set(false);
  }

  protected onPageChange(page: number): void {
    this.auctionService.goToPage(page);
  }

  protected onTableSortingChange(sorting: SortingState): void {
    this.auctionService.upsertSorting(sorting);
  }
}

function activeColumnIdsForViewport(width: number): Set<string> {
  const active = new Set<string>(['itemName', 'selectedPrice', 'selectedQuantity']);
  if (width >= CLASS_MIN_WIDTH) active.add('itemClass');
  if (width >= SUBCLASS_MIN_WIDTH) active.add('itemSubclass');
  if (width >= QUALITY_MIN_WIDTH) active.add('quality');
  return active;
}
