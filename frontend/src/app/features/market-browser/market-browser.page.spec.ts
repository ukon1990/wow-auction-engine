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
      rows: [
        {
          id: '19019',
          name: 'Healing Potion',
          itemClassName: 'Consumable',
          itemSubclassName: 'Potion',
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
      pageSize: 10,
      totalPages: 1,
      sortBy: 'itemName' as const,
      sortDirection: 'asc' as const,
      loading: false,
    }),
    bindRoute: vitest.fn(),
    loadFromRoute: vitest.fn(),
    setActivePrimaryNavId: vitest.fn(),
    setActiveProfessionId: vitest.fn(),
    setSearchQuery: vitest.fn(),
    toggleFilter: vitest.fn(),
    selectFilter: vitest.fn(),
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
    Object.defineProperty(window, 'innerWidth', {
      configurable: true,
      writable: true,
      value: 1400,
    });
    const fixture = TestBed.createComponent(MarketBrowserPage);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Market Browser');
    expect(compiled.textContent).toContain('Item');
    expect(compiled.textContent).toContain('Class');
    expect(compiled.textContent).toContain('Subclass');
    expect(compiled.textContent).toContain('Quality');
    expect(compiled.textContent).toContain('Price');
    expect(compiled.textContent).toContain('Quantity');
    expect(compiled.textContent).toContain('Healing Potion');
    expect(compiled.textContent).toContain('Consumable');
    expect(compiled.textContent).toContain('Potion');
    expect(compiled.textContent).toContain('Showing 1-1 of 1 items');
    expect(serviceStub.loadFromRoute).toHaveBeenCalled();
  });
});
