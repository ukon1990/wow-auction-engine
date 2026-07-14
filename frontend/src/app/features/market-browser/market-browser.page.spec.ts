import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { MarketBrowserPage } from './market-browser.page';
import { AuctionItemService } from '@core/services/auction-item.service';

describe('MarketBrowserPage', () => {
  const pageData = signal({
    page: {
      page: 0,
      pageSize: 10,
      totalItems: 42,
      totalPages: 5,
    },
  });
  const serviceStub = {
    queryParams: signal({
      sortBy: 'itemName' as const,
      sortDirection: 'asc' as const,
      query: '',
    }),
    currentRows: signal([
      {
        id: '19019',
        name: 'Healing Potion',
        itemClassName: 'Consumable',
        itemSubclassName: 'Potion',
        quality: 'rare' as const,
        minBuyout: { gold: 1 },
        marketValue: {},
        regionalAverage: { gold: 1 },
        saleRate: 0,
        soldPerDay: null,
        p25PriceCopper: 95,
        p75PriceCopper: 120,
        selectedQuantity: 4,
      },
    ]),
    filterSections: signal([]),
    pageData,
    paginationState: () => pageData().page,
    isLoading: signal(false),
    setSearchQuery: vitest.fn(),
    toggleFilter: vitest.fn(),
    selectFilter: vitest.fn(),
    setRangeFilter: vitest.fn(),
    resetFilters: vitest.fn(),
    goToPage: vitest.fn(),
    upsertSorting: vitest.fn(),
  };

  beforeEach(async () => {
    pageData.set({
      page: {
        page: 0,
        pageSize: 10,
        totalItems: 42,
        totalPages: 5,
      },
    });
    vitest.clearAllMocks();

    await TestBed.configureTestingModule({
      imports: [MarketBrowserPage],
      providers: [
        { provide: AuctionItemService, useValue: serviceStub },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({}),
            },
            parent: {
              snapshot: {
                paramMap: convertToParamMap({ region: 'eu', realm: 'argent-dawn' }),
              },
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
    expect(compiled.textContent).toContain('p25');
    expect(compiled.textContent).toContain('p75');
    expect(compiled.textContent).toContain('Healing Potion');
    expect(compiled.textContent).toContain('Consumable');
    expect(compiled.textContent).toContain('Potion');
  });

  it('shows the visible market row range and total row count', () => {
    const fixture = TestBed.createComponent(MarketBrowserPage);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Showing 1-10 of 42 rows');
  });

  it('renders market results as cards with compact sorting on mobile', () => {
    Object.defineProperty(window, 'innerWidth', {
      configurable: true,
      writable: true,
      value: 480,
    });
    const fixture = TestBed.createComponent(MarketBrowserPage);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const sortSelect = compiled.querySelector('select') as HTMLSelectElement | null;

    expect(sortSelect).not.toBeNull();
    expect(sortSelect?.value).toBe('itemName');
    expect(compiled.querySelector('[role="columnheader"]')).toBeNull();
    expect(compiled.textContent).toContain('Healing Potion');
    expect(compiled.textContent).toContain('Price');
    expect(compiled.textContent).toContain('Quantity');
    expect(compiled.textContent).toContain('Class');
    expect(compiled.textContent).toContain('Consumable');
    expect(compiled.textContent).toContain('Subclass');
    expect(compiled.textContent).toContain('Potion');
  });

  it('shows the empty market summary when there are no rows', () => {
    pageData.set({
      page: {
        page: 0,
        pageSize: 10,
        totalItems: 0,
        totalPages: 0,
      },
    });
    const fixture = TestBed.createComponent(MarketBrowserPage);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('No market items available.');
  });

  it('delegates selected page events to the market service', () => {
    const fixture = TestBed.createComponent(MarketBrowserPage);
    fixture.detectChanges();

    (fixture.componentInstance as unknown as { onPageChange: (page: number) => void }).onPageChange(
      2,
    );

    expect(serviceStub.goToPage).toHaveBeenCalledWith(2);
  });
});
