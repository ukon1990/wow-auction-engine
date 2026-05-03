import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';

import { MarketBrowserService } from '../../core/services/market-browser.service';
import { MarketBrowserPage } from './market-browser.page';

describe('MarketBrowserPage', () => {
  const serviceStub = {
    viewModel: signal({
      primaryNavItems: [],
      activePrimaryNavId: 'market-browser',
      professionNavItems: [],
      activeProfessionId: 'alchemy',
      character: {
        name: 'Tester',
        realm: 'Argent Dawn-EU',
        level: 70,
        profession: 'Alchemy',
        skill: 'Skill Level 100/100',
      },
      filterSections: [],
      tableColumns: [
        { id: 'item', label: 'Item' },
        { id: 'quality', label: 'Quality' },
        { id: 'selected-price', label: 'Realm Price', align: 'right' as const },
        { id: 'selected-quantity', label: 'Realm Qty', align: 'right' as const },
        { id: 'community-price', label: 'Region Price', align: 'right' as const },
        { id: 'community-quantity', label: 'Region Qty', align: 'right' as const },
      ],
      rows: [
        {
          id: '19019',
          name: 'Healing Potion',
          quality: 'rare' as const,
          minBuyout: { gold: 1 },
          marketValue: {},
          regionalAverage: { silver: 90 },
          saleRate: 0,
          selectedQuantity: 4,
          communityQuantity: 8,
        },
      ],
      paginationSummary: 'Showing 1-1 of 1 items',
      searchQuery: '',
      page: 0,
      totalPages: 1,
      loading: false,
    }),
    bindRoute: vitest.fn(),
    loadFromRoute: vitest.fn(),
    setActivePrimaryNavId: vitest.fn(),
    setActiveProfessionId: vitest.fn(),
    setSearchQuery: vitest.fn(),
    toggleFilter: vitest.fn(),
    setRangeFilter: vitest.fn(),
    resetFilters: vitest.fn(),
    goToPreviousPage: vitest.fn(),
    goToNextPage: vitest.fn(),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MarketBrowserPage],
      providers: [
        { provide: MarketBrowserService, useValue: serviceStub },
        {
          provide: ActivatedRoute,
          useValue: {
            parent: {
              paramMap: of(convertToParamMap({ region: 'eu', realm: 'argent-dawn' })),
            },
            queryParamMap: of(convertToParamMap({})),
          },
        },
      ],
    }).compileComponents();
  });

  it('renders the market browser shell with service data from the API-backed service', () => {
    const fixture = TestBed.createComponent(MarketBrowserPage);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Market Browser');
    expect(compiled.textContent).toContain('Healing Potion');
    expect(compiled.textContent).toContain('Showing 1-1 of 1 items');
    expect(serviceStub.loadFromRoute).toHaveBeenCalled();
  });
});
