import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { CraftingBrowserPage } from './crafting-browser.page';
import { CraftingItemService } from '@core/services/crafting-item.service';

describe('CraftingBrowserPage', () => {
  const pageData = signal({
    page: {
      page: 0,
      pageSize: 25,
      totalItems: 1,
      totalPages: 1,
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
        rowId: '1',
        recipeId: 1,
        craftedItemId: 19019,
        craftedItemName: 'Healing Potion',
        recipeName: 'Healing Potion',
        professionName: 'Alchemy',
        variantSummary: '',
        listingKey: { bonusKey: '', modifierKey: '', petSpeciesId: 0 },
        quality: 'rare' as const,
        outputPriceCopper: 100,
        outputP25PriceCopper: 90,
        outputP75PriceCopper: 120,
        reagentCostCopper: 50,
        profitCopper: 50,
        roiPercent: 100,
        outputPriceChangePercent: null,
        listingQuantity: 4,
        minBuyoutCopper: 100,
        profileFit: null,
        saleRate: null,
        soldPerDay: null,
      },
    ]),
    filterSections: signal([]),
    pageData,
    paginationState: () => pageData().page,
    isLoading: signal(false),
    setSearchQuery: vitest.fn(),
    toggleFilter: vitest.fn(),
    setRangeFilter: vitest.fn(),
    resetFilters: vitest.fn(),
    goToPage: vitest.fn(),
    upsertSorting: vitest.fn(),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CraftingBrowserPage],
      providers: [
        { provide: CraftingItemService, useValue: serviceStub },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap({}) },
            queryParamMap: of(convertToParamMap({})),
          },
        },
      ],
    }).compileComponents();
  });

  it('renders the crafting browser shell with service data', () => {
    Object.defineProperty(window, 'innerWidth', {
      configurable: true,
      writable: true,
      value: 1400,
    });
    const fixture = TestBed.createComponent(CraftingBrowserPage);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Crafting');
    expect(compiled.textContent).toContain('Item');
    expect(compiled.textContent).toContain('Healing Potion');
    expect(compiled.textContent).toContain('Alchemy');
    expect(compiled.querySelector('[role="columnheader"]')?.textContent).not.toContain('Recipe');
    expect(compiled.textContent).toContain('p25');
    expect(compiled.textContent).toContain('p75');
  });

  it('delegates selected page events to the crafting service', () => {
    const fixture = TestBed.createComponent(CraftingBrowserPage);
    fixture.detectChanges();

    (fixture.componentInstance as unknown as { onPageChange: (page: number) => void }).onPageChange(
      2,
    );

    expect(serviceStub.goToPage).toHaveBeenCalledWith(2);
  });

  it('renders crafting results as cards with compact sorting on mobile', () => {
    Object.defineProperty(window, 'innerWidth', {
      configurable: true,
      writable: true,
      value: 480,
    });
    const fixture = TestBed.createComponent(CraftingBrowserPage);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const sortSelect = compiled.querySelector('select') as HTMLSelectElement | null;

    expect(sortSelect).not.toBeNull();
    expect(sortSelect?.value).toBe('itemName');
    expect(compiled.querySelector('[role="columnheader"]')).toBeNull();
    expect(compiled.textContent).toContain('Healing Potion');
    expect(compiled.textContent).toContain('Buyout');
    expect(compiled.textContent).toContain('Profit');
    expect(compiled.textContent).toContain('Mat. cost');
    expect(compiled.textContent).toContain('Profession');
    expect(compiled.textContent).toContain('Alchemy');
  });
});
