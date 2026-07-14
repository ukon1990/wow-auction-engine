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
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { debounceTime, distinctUntilChanged, fromEvent, map, startWith } from 'rxjs';

import {
  FilterOptionChanged,
  FilterPanelComponent,
  FilterRangeChanged,
  PageFrameComponent,
  SearchInputComponent,
  SortingState,
  SymbolIconComponent,
  TableComponent,
} from '@ui';

import { CraftingItemService } from '@core/services/crafting-item.service';

import type { CraftingTableRow } from './crafting-browser.models';
import {
  craftingBrowserHeaderRowClass,
  craftingBrowserRowClass,
  craftingBrowserRowGridTemplateColumns,
  craftingBrowserSkeletonRowClass,
  createCraftingBrowserTableColumns,
} from './crafting-browser-table.columns';
import { activeColumnIdsForViewport } from './crafting-browser.helpers';

const DEFAULT_VIEWPORT_WIDTH = 1280;
const CARD_VIEW_MAX_WIDTH = 1023;

@Component({
  selector: 'app-crafting-browser-page',
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
  templateUrl: './crafting-browser.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CraftingBrowserPage {
  readonly searchField = new FormControl();
  protected readonly mobileFiltersOpen = signal(false);
  protected readonly viewportWidth = signal(DEFAULT_VIEWPORT_WIDTH);
  protected readonly allColumns = createCraftingBrowserTableColumns();
  protected readonly cardView = computed(() => this.viewportWidth() <= CARD_VIEW_MAX_WIDTH);
  protected readonly activeColumns = computed(() =>
    this.allColumns.filter((column) =>
      activeColumnIdsForViewport(this.viewportWidth()).has(String(column.id ?? '')),
    ),
  );
  protected readonly tableColumns = computed(() =>
    this.cardView() ? this.allColumns : this.activeColumns(),
  );
  protected readonly rowGridTemplate = computed(() =>
    craftingBrowserRowGridTemplateColumns(this.tableColumns()),
  );
  protected readonly bodyRowClass = craftingBrowserRowClass;
  protected readonly tableMinWidth = 'min-w-0 w-full';
  protected readonly headerRowClass = craftingBrowserHeaderRowClass();
  protected readonly skeletonRowClass = craftingBrowserSkeletonRowClass();
  protected readonly mobileSortOptions = [
    { id: 'itemName', label: $localize`:@@crafting.column.item:Item` },
    { id: 'outputPrice', label: $localize`:@@crafting.column.buyout:Buyout` },
    { id: 'profit', label: $localize`:@@crafting.column.profit:Profit` },
    { id: 'reagentCost', label: $localize`:@@crafting.column.materialCost:Mat. cost` },
    { id: 'roiPercent', label: $localize`:@@crafting.column.roi:ROI` },
    { id: 'saleRate', label: $localize`:@@crafting.column.saleRate:Sale rate` },
    { id: 'soldPerDay', label: $localize`:@@crafting.column.soldPerDay:Avg sold/day` },
    { id: 'outputPriceChangePercent', label: $localize`:@@crafting.column.trend:Trend` },
    { id: 'professionName', label: $localize`:@@crafting.column.profession:Profession` },
  ];
  private readonly craftingService = inject(CraftingItemService);
  protected readonly tableSorting = computed<SortingState>(() => {
    const params = this.craftingService.queryParams();
    if (!params) return [];
    const { sortBy, sortDirection } = params;
    return [{ id: sortBy, desc: sortDirection === 'desc' }];
  });
  readonly currentRows = this.craftingService.currentRows;
  readonly filterSections = this.craftingService.filterSections;
  readonly pageSize = computed(() => this.craftingService.pageData()?.page?.pageSize ?? 25);
  readonly paginationState = this.craftingService.paginationState;
  readonly isLoading = this.craftingService.isLoading;
  protected readonly loadingPaginationSummary = $localize`:@@crafting.loadingRecipes:Loading recipes...`;
  protected readonly emptyPaginationSummary = $localize`:@@crafting.pagination.empty:No recipes available.`;
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
        this.craftingService.setSearchQuery(query ?? '');
      });

    effect(() => {
      const query = this.craftingService.queryParams()?.query ?? '';
      if (this.searchField.value !== query) {
        this.searchField.setValue(query, { emitEvent: false });
      }
    });
  }

  protected readonly rowId = (row: CraftingTableRow) => row.rowId;

  protected pageTitle(): string {
    return $localize`:@@route.crafting:Crafting`;
  }

  protected pageEyebrow(): string {
    return $localize`:@@crafting.eyebrow:Recipe economics`;
  }

  protected filterOptionsLabel(): string {
    return $localize`:@@filters.options:Filter options`;
  }

  protected onFilterToggled(optionId: string): void {
    this.craftingService.toggleFilter(optionId);
  }

  protected onFilterSelected(_change: FilterOptionChanged): void {}

  protected onRangeChanged(change: FilterRangeChanged): void {
    this.craftingService.setRangeFilter(change.sectionId, change.bound, change.value);
  }

  protected onFiltersReset(): void {
    this.craftingService.resetFilters();
  }

  protected openMobileFilters(): void {
    this.mobileFiltersOpen.set(true);
  }

  protected closeMobileFilters(): void {
    this.mobileFiltersOpen.set(false);
  }

  protected onPageChange(page: number): void {
    this.craftingService.goToPage(page);
  }

  protected onTableSortingChange(sorting: SortingState): void {
    this.craftingService.upsertSorting(sorting);
  }
}
