import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';

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
        <ee-search-input />
        <div class="flex min-h-0 flex-1 gap-element-gap overflow-hidden">
          <ee-filter-panel [sections]="viewModel().filterSections" />
          <ee-market-table
            [columns]="viewModel().tableColumns"
            [rows]="viewModel().rows"
            [summary]="viewModel().paginationSummary"
          />
        </div>
      </ee-page-frame>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarketBrowserPage {
  private readonly marketBrowserService = inject(MarketBrowserService);
  protected readonly viewModel = this.marketBrowserService.viewModel;
  protected readonly mobileNavOpen = signal(false);

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
}
