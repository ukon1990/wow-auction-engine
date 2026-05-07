import { isPlatformBrowser } from '@angular/common';
import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  PLATFORM_ID,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { A11yModule } from '@angular/cdk/a11y';
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
import type { SortingState } from '@ui';

import { CraftingBrowserService } from '@core/services/crafting-browser.service';

import type { CraftingTableRow } from './crafting-browser.models';
import {
  createCraftingBrowserTableColumns,
  craftingBrowserHeaderRowClass,
  craftingBrowserRowClass,
  craftingBrowserRowGridTemplateColumns,
  craftingBrowserSkeletonRowClass,
} from './crafting-browser-table.columns';
import { activeColumnIdsForViewport, realmAncestorRoute } from './crafting-browser.helpers';

const DEFAULT_VIEWPORT_WIDTH = 1280;

@Component({
  selector: 'app-crafting-browser-page',
  host: {
    class: 'flex min-h-0 flex-1 overflow-hidden',
  },
  imports: [
    A11yModule,
    FilterPanelComponent,
    PageFrameComponent,
    SearchInputComponent,
    SymbolIconComponent,
    TableComponent,
  ],
  templateUrl: './crafting-browser.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CraftingBrowserPage implements AfterViewInit {
  private readonly craftingBrowserService = inject(CraftingBrowserService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly platformId = inject(PLATFORM_ID);
  protected readonly viewModel = this.craftingBrowserService.viewModel;
  protected readonly mobileFiltersOpen = signal(false);
  protected readonly viewportWidth = signal(DEFAULT_VIEWPORT_WIDTH);
  private readonly searchChanged = new Subject<string>();
  private readonly mobileFiltersTrigger =
    viewChild<ElementRef<HTMLButtonElement>>('mobileFiltersTrigger');
  private viewReady = false;

  protected readonly allColumns = createCraftingBrowserTableColumns();
  protected readonly activeColumns = computed(() =>
    this.allColumns.filter((column) =>
      activeColumnIdsForViewport(this.viewportWidth()).has(String(column.id ?? '')),
    ),
  );
  protected readonly rowGridTemplate = computed(() =>
    craftingBrowserRowGridTemplateColumns(this.activeColumns()),
  );
  protected readonly bodyRowClass = craftingBrowserRowClass;
  protected readonly rowId = (row: CraftingTableRow) => row.rowId;
  protected readonly tableMinWidth = 'min-w-0 w-full';
  protected readonly headerRowClass = craftingBrowserHeaderRowClass();
  protected readonly skeletonRowClass = craftingBrowserSkeletonRowClass();
  protected readonly tableSorting = computed<SortingState>(() => {
    const vm = this.viewModel();
    return [{ id: vm.sortBy, desc: vm.sortDirection === 'desc' }];
  });

  protected pageTitle(): string {
    return $localize`:@@route.crafting:Crafting`;
  }

  protected pageEyebrow(): string {
    return $localize`:@@crafting.eyebrow:Recipe economics`;
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

    effect(() => {
      const open = this.mobileFiltersOpen();
      if (!open && this.viewReady) {
        // Restore focus to the trigger when the dialog closes (CDK trap auto-captures focus on
        // open but does not always restore correctly when the trap is toggled via inputs).
        this.mobileFiltersTrigger()?.nativeElement.focus();
      }
    });

    this.craftingBrowserService.bindRoute(this.route);
    const realmRoute = realmAncestorRoute(this.route);
    combineLatest([realmRoute.paramMap, this.route.queryParamMap])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(([paramMap, queryParamMap]) => {
        this.craftingBrowserService.loadFromRoute(paramMap, queryParamMap);
      });
    this.searchChanged
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((query) => {
        this.craftingBrowserService.setSearchQuery(query);
      });
  }

  protected onSearchChanged(query: string): void {
    this.searchChanged.next(query);
  }

  protected onFilterToggled(optionId: string): void {
    this.craftingBrowserService.toggleFilter(optionId);
  }

  protected onFilterSelected(_change: { sectionId: string; optionId: string | null }): void {}

  protected onRangeChanged(change: {
    id: string;
    bound: 'min' | 'max';
    value: number | null;
  }): void {
    this.craftingBrowserService.setRangeFilter(change.id, change.bound, change.value);
  }

  protected onFiltersReset(): void {
    this.craftingBrowserService.resetFilters();
  }

  ngAfterViewInit(): void {
    this.viewReady = true;
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
    this.craftingBrowserService.goToPreviousPage();
  }

  protected onNextPage(): void {
    this.craftingBrowserService.goToNextPage();
  }

  protected onTableSortingChange(sorting: SortingState): void {
    this.craftingBrowserService.applyTableSort(sorting);
  }
}
