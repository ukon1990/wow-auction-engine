import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { combineLatest } from 'rxjs';

import {
  FilterPanelComponent,
  MarketTableComponent,
  PageFrameComponent,
  SearchInputComponent,
  SideNavComponent,
} from '@ui';

import { MarketBrowserService } from '../../core/services/market-browser.service';

@Component({
  selector: 'app-market-browser-page',
  imports: [
    FilterPanelComponent,
    MarketTableComponent,
    PageFrameComponent,
    SearchInputComponent,
    SideNavComponent,
  ],
  template: `
    <div class="flex min-h-0 flex-1">
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
      />
      <ee-page-frame title="Market Browser" eyebrow="Exchange Intelligence">
        <ee-search-input
          [value]="viewModel().searchQuery"
          (valueChanged)="onSearchChanged($event)"
        />
        <div class="flex min-h-0 flex-1 gap-element-gap overflow-hidden">
          <ee-filter-panel
            [sections]="viewModel().filterSections"
            (optionToggled)="onFilterToggled($event)"
            (rangeChanged)="onRangeChanged($event)"
            (reset)="onFiltersReset()"
          />
          <ee-market-table
            [columns]="viewModel().tableColumns"
            [rows]="viewModel().rows"
            [summary]="viewModel().paginationSummary"
            (previousPage)="onPreviousPage()"
            (nextPage)="onNextPage()"
          />
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
  protected readonly viewModel = this.marketBrowserService.viewModel;
  protected readonly mobileNavOpen = signal(false);

  constructor() {
    this.marketBrowserService.bindRoute(this.route);
    combineLatest([this.route.parent?.paramMap ?? this.route.paramMap, this.route.queryParamMap])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(([paramMap, queryParamMap]) => {
        this.marketBrowserService.loadFromRoute(paramMap, queryParamMap);
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
    this.marketBrowserService.setSearchQuery(query);
  }

  protected onFilterToggled(optionId: string): void {
    this.marketBrowserService.toggleFilter(optionId);
  }

  protected onRangeChanged(change: { id: string; bound: 'min' | 'max'; value: number | null }): void {
    this.marketBrowserService.setRangeFilter(change.id, change.bound, change.value);
  }

  protected onFiltersReset(): void {
    this.marketBrowserService.resetFilters();
  }

  protected onPreviousPage(): void {
    this.marketBrowserService.goToPreviousPage();
  }

  protected onNextPage(): void {
    this.marketBrowserService.goToNextPage();
  }
}
