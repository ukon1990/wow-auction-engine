import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { Subject, combineLatest, debounceTime, distinctUntilChanged } from 'rxjs';

import {
  FilterPanelComponent,
  PageFrameComponent,
  SearchInputComponent,
  TableComponent,
} from '@ui';
import type { MarketItemRow, SortingState } from '@ui';

import { MarketBrowserService } from '@core/services/market-browser.service';

import {
  createMarketBrowserTableColumns,
  marketBrowserContentMinWidthClass,
  marketBrowserHeaderRowClass,
  marketBrowserRowClass,
  marketBrowserRowGridTemplateColumns,
  marketBrowserSkeletonRowClass,
} from './market-browser-table.columns';

@Component({
  selector: 'app-market-browser-page',
  host: {
    class: 'flex min-h-0 flex-1 overflow-hidden',
  },
  imports: [
    FilterPanelComponent,
    PageFrameComponent,
    SearchInputComponent,
    // SideNavComponent,
    TableComponent,
  ],
  template: `
    <div class="flex min-h-0 flex-1 overflow-hidden">
      <!-- TODO: Maybe add back later?
       <ee-side-nav
        [items]="viewModel().professionNavItems"
        [activeId]="viewModel().activeProfessionId"
        [character]="viewModel().character"
        [primaryNavItems]="viewModel().primaryNavItems"
        [activePrimaryId]="viewModel().activePrimaryNavId"
        [mobileOpen]="mobileNavOpen()"
        (mobileOpenChange)="mobileNavOpen.set($event)"
        (primarySelected)="onPrimaryNavSelected($event)"
        (selected)="onProfessionSelected($event)"
      />*/}}-->
      <ee-page-frame title="Market Browser" eyebrow="Search the auction house">
        <ee-search-input
          [value]="viewModel().searchQuery"
          (valueChanged)="onSearchChanged($event)"
        />
        <div class="flex lg:hidden">
          <button
            type="button"
            class="inline-flex items-center gap-2 rounded border border-white/10 bg-surface-container-high px-4 py-2 ee-label text-on-surface transition hover:bg-surface-container-highest"
            aria-haspopup="dialog"
            [attr.aria-expanded]="mobileFiltersOpen()"
            (click)="openMobileFilters()"
          >
            Filters
          </button>
        </div>
        <div class="flex min-h-0 min-w-0 flex-1 gap-element-gap overflow-hidden">
          <ee-filter-panel
            class="hidden lg:flex"
            [sections]="viewModel().filterSections"
            (optionToggled)="onFilterToggled($event)"
            (optionSelected)="onFilterSelected($event)"
            (rangeChanged)="onRangeChanged($event)"
            (reset)="onFiltersReset()"
          />
          <ee-table
            [data]="viewModel().rows"
            [columns]="marketColumns"
            [getRowId]="marketRowId"
            [manualSorting]="true"
            [sorting]="marketTableSorting()"
            (sortingChange)="onTableSortingChange($event)"
            [loading]="viewModel().loading"
            [skeletonRowCount]="viewModel().pageSize"
            [skeletonRowClass]="marketSkeletonRowClass"
            [rowGridTemplateColumns]="marketRowGridTemplate"
            sectionAriaLabel="Market items"
            emptyMessage="No market items available."
            [contentMinWidthClass]="marketTableMinWidth"
            [headerRowClass]="marketTableHeaderRow"
            [bodyRowClassFn]="marketRowClass"
            [showFooter]="true"
            [footerSummary]="viewModel().paginationSummary"
            [showPagination]="true"
            (previousPage)="onPreviousPage()"
            (nextPage)="onNextPage()"
          />
        </div>
        @if (mobileFiltersOpen()) {
          <div
            class="fixed inset-0 z-50 flex lg:hidden"
            role="dialog"
            aria-modal="true"
            aria-label="Filter options"
          >
            <button
              type="button"
              class="flex-1 bg-black/60"
              aria-label="Close filters"
              (click)="closeMobileFilters()"
            ></button>
            <div class="flex h-full w-[min(22rem,90vw)] flex-col border-l border-white/10 bg-surface p-4">
              <div class="mb-3 flex items-center justify-between gap-2">
                <h2 class="ee-section-heading text-primary">Filters</h2>
                <button
                  type="button"
                  class="rounded border border-white/10 bg-surface-container-high px-3 py-1.5 ee-label text-on-surface transition hover:bg-surface-container-highest"
                  (click)="closeMobileFilters()"
                >
                  Close
                </button>
              </div>
              <ee-filter-panel
                panelClass="ee-glass flex min-h-0 w-full flex-1 flex-col overflow-hidden rounded-lg"
                [sections]="viewModel().filterSections"
                (optionToggled)="onMobileFilterToggled($event)"
                (optionSelected)="onMobileFilterSelected($event)"
                (rangeChanged)="onMobileRangeChanged($event)"
                (reset)="onMobileFiltersReset()"
              />
            </div>
          </div>
        }
      </ee-page-frame>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarketBrowserPage {
  private readonly marketBrowserService = inject(MarketBrowserService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly viewModel = this.marketBrowserService.viewModel;
  protected readonly mobileNavOpen = signal(false);
  protected readonly mobileFiltersOpen = signal(false);
  private readonly searchChanged = new Subject<string>();

  protected readonly marketColumns = createMarketBrowserTableColumns();
  protected readonly marketRowGridTemplate = marketBrowserRowGridTemplateColumns(
    this.marketColumns,
  );
  protected readonly marketRowClass = marketBrowserRowClass;
  protected readonly marketRowId = (row: MarketItemRow) => row.id;
  protected readonly marketTableMinWidth = marketBrowserContentMinWidthClass();
  protected readonly marketTableHeaderRow = marketBrowserHeaderRowClass();
  protected readonly marketSkeletonRowClass = marketBrowserSkeletonRowClass();
  protected readonly marketTableSorting = computed<SortingState>(() => {
    const vm = this.viewModel();
    return [{ id: vm.sortBy, desc: vm.sortDirection === 'desc' }];
  });

  constructor() {
    this.marketBrowserService.bindRoute(this.route);
    combineLatest([this.route.parent?.paramMap ?? this.route.paramMap, this.route.queryParamMap])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(([paramMap, queryParamMap]) => {
        this.marketBrowserService.loadFromRoute(paramMap, queryParamMap);
      });
    this.searchChanged
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((query) => {
        this.marketBrowserService.setSearchQuery(query);
      });
  }

  protected toggleMobileNav(): void {
    this.mobileNavOpen.update((open) => !open);
  }

  protected onPrimaryNavSelected(id: string): void {
    this.marketBrowserService.setActivePrimaryNavId(id);
    this.mobileNavOpen.set(false);
  }

  protected onProfessionSelected(id: string): void {
    this.marketBrowserService.setActiveProfessionId(id);
  }

  protected onSearchChanged(query: string): void {
    this.searchChanged.next(query);
  }

  protected onFilterToggled(optionId: string): void {
    this.marketBrowserService.toggleFilter(optionId);
  }

  protected onFilterSelected(change: { sectionId: string; optionId: string | null }): void {
    this.marketBrowserService.selectFilter(change.sectionId, change.optionId);
  }

  protected onRangeChanged(change: {
    id: string;
    bound: 'min' | 'max';
    value: number | null;
  }): void {
    this.marketBrowserService.setRangeFilter(change.id, change.bound, change.value);
  }

  protected onFiltersReset(): void {
    this.marketBrowserService.resetFilters();
  }

  protected openMobileFilters(): void {
    this.mobileFiltersOpen.set(true);
  }

  protected closeMobileFilters(): void {
    this.mobileFiltersOpen.set(false);
  }

  protected onMobileFilterToggled(optionId: string): void {
    this.onFilterToggled(optionId);
  }

  protected onMobileFilterSelected(change: { sectionId: string; optionId: string | null }): void {
    this.onFilterSelected(change);
  }

  protected onMobileRangeChanged(change: {
    id: string;
    bound: 'min' | 'max';
    value: number | null;
  }): void {
    this.onRangeChanged(change);
  }

  protected onMobileFiltersReset(): void {
    this.onFiltersReset();
    this.closeMobileFilters();
  }

  protected onPreviousPage(): void {
    this.marketBrowserService.goToPreviousPage();
  }

  protected onNextPage(): void {
    this.marketBrowserService.goToNextPage();
  }

  protected onTableSortingChange(sorting: SortingState): void {
    this.marketBrowserService.applyTableSort(sorting);
  }
}
