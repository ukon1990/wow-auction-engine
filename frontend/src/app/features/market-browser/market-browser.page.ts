import { isPlatformBrowser } from '@angular/common';
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
import { ActivatedRoute } from '@angular/router';
import {
  Subject,
  combineLatest,
  debounceTime,
  distinctUntilChanged,
  fromEvent,
  map,
  startWith,
} from 'rxjs';

import {
  FilterPanelComponent,
  PageFrameComponent,
  SearchInputComponent,
  SymbolIconComponent,
  TableComponent,
} from '@ui';
import type { MarketItemRow, SortingState } from '@ui';

import { MarketBrowserService } from '@core/services/market-browser.service';

import {
  createMarketBrowserTableColumns,
  marketBrowserHeaderRowClass,
  marketBrowserRowClass,
  marketBrowserRowGridTemplateColumns,
  marketBrowserSkeletonRowClass,
} from './market-browser-table.columns';

const DEFAULT_VIEWPORT_WIDTH = 1280;
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
    // SideNavComponent,
    SymbolIconComponent,
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
      <ee-page-frame [title]="pageTitle()" [eyebrow]="pageEyebrow()" bodyLayout="fill">
        <div class="flex items-center gap-2">
          <ee-search-input
            class="min-w-0 flex-1"
            [value]="viewModel().searchQuery"
            (valueChanged)="onSearchChanged($event)"
          />
          <button
            type="button"
            class="inline-flex shrink-0 items-center gap-2 rounded border border-white/10 bg-surface-container-high px-4 py-2 ee-label text-on-surface transition hover:bg-surface-container-highest lg:hidden"
            aria-label="Open filters"
            i18n-aria-label="@@filters.open"
            aria-haspopup="dialog"
            [attr.aria-expanded]="mobileFiltersOpen()"
            (click)="openMobileFilters()"
          >
            <ee-symbol-icon class="text-base" name="filter_alt" aria-hidden="true" />
            <ng-container i18n="@@filters.titleShort">Filters</ng-container>
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
            [columns]="activeMarketColumns()"
            [getRowId]="marketRowId"
            [manualSorting]="true"
            [sorting]="marketTableSorting()"
            (sortingChange)="onTableSortingChange($event)"
            [clickableRows]="false"
            [loading]="viewModel().loading"
            [skeletonRowCount]="viewModel().pageSize"
            [skeletonRowClass]="marketSkeletonRowClass"
            [rowGridTemplateColumns]="marketRowGridTemplate()"
            i18n-sectionAriaLabel="@@market.table.aria"
            sectionAriaLabel="Market items"
            i18n-emptyMessage="@@market.pagination.empty"
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
        <div
          class="fixed inset-0 z-50 flex transition-opacity duration-300 lg:hidden"
          [class.pointer-events-none]="!mobileFiltersOpen()"
          [class.opacity-0]="!mobileFiltersOpen()"
          [class.opacity-100]="mobileFiltersOpen()"
          [attr.inert]="mobileFiltersOpen() ? null : ''"
          [attr.aria-hidden]="!mobileFiltersOpen()"
          [attr.role]="mobileFiltersOpen() ? 'dialog' : null"
          [attr.aria-modal]="mobileFiltersOpen() ? 'true' : null"
          [attr.aria-label]="mobileFiltersOpen() ? filterOptionsLabel() : null"
        >
          <button
            type="button"
            class="flex-1 bg-black/60 transition-opacity duration-300"
            aria-label="Close filters"
            i18n-aria-label="@@filters.close"
            (click)="closeMobileFilters()"
          ></button>
          <div
            class="flex h-full min-h-0 w-[min(22rem,90vw)] flex-col overflow-hidden border-l border-white/10 bg-surface p-4 transition-transform duration-300 ease-out"
            [class.translate-x-full]="!mobileFiltersOpen()"
            [class.translate-x-0]="mobileFiltersOpen()"
          >
            <div class="mb-3 flex items-center justify-between gap-2">
              <h2 class="ee-section-heading text-primary" i18n="@@filters.titleShort">Filters</h2>
              <button
                type="button"
                class="rounded border border-white/10 bg-surface-container-high px-3 py-1.5 ee-label text-on-surface transition hover:bg-surface-container-highest"
                (click)="closeMobileFilters()"
              >
                <ng-container i18n="@@common.close">Close</ng-container>
              </button>
            </div>
            <ee-filter-panel
              class="flex min-h-0 flex-1"
              panelClass="ee-glass flex min-h-0 w-full flex-1 flex-col overflow-hidden rounded-lg"
              [sections]="viewModel().filterSections"
              (optionToggled)="onMobileFilterToggled($event)"
              (optionSelected)="onMobileFilterSelected($event)"
              (rangeChanged)="onMobileRangeChanged($event)"
              (reset)="onMobileFiltersReset()"
            />
          </div>
        </div>
      </ee-page-frame>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarketBrowserPage {
  private readonly marketBrowserService = inject(MarketBrowserService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly platformId = inject(PLATFORM_ID);
  protected readonly viewModel = this.marketBrowserService.viewModel;
  protected readonly mobileNavOpen = signal(false);
  protected readonly mobileFiltersOpen = signal(false);
  protected readonly viewportWidth = signal(DEFAULT_VIEWPORT_WIDTH);
  private readonly searchChanged = new Subject<string>();

  protected readonly marketColumns = createMarketBrowserTableColumns();
  protected readonly activeMarketColumns = computed(() =>
    this.marketColumns.filter((column) =>
      activeColumnIdsForViewport(this.viewportWidth()).has(String(column.id ?? '')),
    ),
  );
  protected readonly marketRowGridTemplate = computed(() =>
    marketBrowserRowGridTemplateColumns(this.activeMarketColumns()),
  );
  protected readonly marketRowClass = marketBrowserRowClass;
  protected readonly marketRowId = (row: MarketItemRow) => row.id;
  protected readonly marketTableMinWidth = 'min-w-0 w-full';
  protected readonly marketTableHeaderRow = marketBrowserHeaderRowClass();
  protected readonly marketSkeletonRowClass = marketBrowserSkeletonRowClass();
  protected readonly marketTableSorting = computed<SortingState>(() => {
    const vm = this.viewModel();
    return [{ id: vm.sortBy, desc: vm.sortDirection === 'desc' }];
  });

  protected pageTitle(): string {
    return $localize`:@@market.title:Market Browser`;
  }

  protected pageEyebrow(): string {
    return $localize`:@@market.eyebrow:Search the auction house`;
  }

  protected filterOptionsLabel(): string {
    return $localize`:@@filters.options:Filter options`;
  }

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

    this.marketBrowserService.bindRoute(this.route);
    const realmRoute = realmAncestorRoute(this.route);
    combineLatest([realmRoute.paramMap, this.route.queryParamMap])
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

function activeColumnIdsForViewport(width: number): Set<string> {
  const active = new Set<string>(['itemName', 'selectedPrice', 'selectedQuantity']);
  if (width >= CLASS_MIN_WIDTH) active.add('itemClass');
  if (width >= SUBCLASS_MIN_WIDTH) active.add('itemSubclass');
  if (width >= QUALITY_MIN_WIDTH) active.add('quality');
  return active;
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
