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
  ActiveFilterChipsComponent,
  ButtonDirective,
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
import { DecimalPipe } from '@angular/common';
import { LocaleService } from '@core/services/locale.service';
import { RealmSelectionService } from '@core/services/realm-selection.service';
import { parseActiveFilterChipId, toActiveFilterChips } from '@core/utils/active-filter-chips';

const DEFAULT_VIEWPORT_WIDTH = 1280;
const CARD_VIEW_MAX_WIDTH = 1023;

@Component({
  selector: 'app-market-browser-page',
  host: {
    class: 'flex min-h-0 flex-1 overflow-hidden',
  },
  imports: [
    ActiveFilterChipsComponent,
    ButtonDirective,
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
    { id: 'saleRate', label: $localize`:@@market.column.saleRate:Sale rate` },
    { id: 'soldPerDay', label: $localize`:@@market.column.soldPerDay:Avg sold/day` },
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
  protected readonly activeFilterChips = computed(() => toActiveFilterChips(this.filterSections()));
  private readonly realmSelection = inject(RealmSelectionService);
  private readonly locale = inject(LocaleService);
  protected readonly resultSummary = computed(() => {
    const total = this.paginationState()?.totalItems;
    if (total == null) return '';
    const count =
      new DecimalPipe(this.locale.formatLocale()).transform(total, '1.0-0') ?? String(total);
    const sortBy = this.auctionService.queryParams()?.sortBy;
    const sortLabel = this.marketMobileSortOptions.find((option) => option.id === sortBy)?.label;
    return sortLabel
      ? $localize`:@@market.resultSummarySorted:${count}:count: items · sorted by ${sortLabel}:sort:`
      : $localize`:@@market.resultSummary:${count}:count: items`;
  });
  readonly pageSize = computed(() => this.auctionService.pageData()?.page?.pageSize ?? 25);
  readonly paginationState = this.auctionService.paginationState;
  readonly isLoading = this.auctionService.isLoading;
  readonly loadingPaginationSummary = $localize`:@@market.loadingItems:Loading market items...`;
  readonly emptyPaginationSummary = $localize`:@@market.pagination.empty:No items match these filters.`;
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
    return $localize`:@@market.title:Market`;
  }

  protected pageDescription(): string {
    const realm = this.realmSelection.selected();
    if (!realm?.name) return '';
    const region = realm.region.toUpperCase();
    return $localize`:@@market.description:Prices for ${realm.name}:realm: (${region}:region:) and region commodities`;
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

  protected onChipRemoved(chipId: string): void {
    const chip = parseActiveFilterChipId(chipId);
    if (!chip) return;
    if (chip.kind === 'range') this.auctionService.clearRangeFilter(chip.sectionId);
    else if (chip.kind === 'select') this.auctionService.selectFilter(chip.sectionId, null);
    else this.auctionService.toggleFilter(chip.optionId);
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

function activeColumnIdsForViewport(_width: number): Set<string> {
  return new Set<string>([
    'itemName',
    'selectedPrice',
    'selectedQuantity',
    'saleRate',
    'soldPerDay',
  ]);
}
