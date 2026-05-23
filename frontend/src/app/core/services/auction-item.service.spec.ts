import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { firstValueFrom, of } from 'rxjs';

import { AuctionItemService } from './auction-item.service';
import { AuctionMarketApiService } from '@api/generated';
import { QueryService } from '@core/services/query.service';
import { RealmSelectionService } from '@core/services/realm-selection.service';
import { LocaleService } from '@core/services/locale.service';
import { defaultMarketBrowserQueryState } from '@core/mappers/market-browser-query.mapper';

describe('AuctionItemService', () => {
  let service: AuctionItemService;
  let api: { filters: ReturnType<typeof vitest.fn>; search: ReturnType<typeof vitest.fn> };
  let queryParams: ReturnType<typeof signal<typeof defaultMarketBrowserQueryState>>;
  let navigateWithState: ReturnType<typeof vitest.fn>;

  beforeEach(() => {
    queryParams = signal({
      ...defaultMarketBrowserQueryState,
      query: '',
      qualityIds: [],
      itemClassIds: [],
      itemSubclassIds: [],
      recipeOnly: null,
      minPrice: null,
      maxPrice: null,
      minQuantity: null,
      maxQuantity: null,
      page: 0,
      pageSize: 25,
      sortBy: 'itemName',
      sortDirection: 'asc',
    });
    api = {
      filters: vitest.fn(() => of({ filters: [] })),
      search: vitest.fn(() => of({ items: [] })),
    };
    navigateWithState = vitest.fn();

    TestBed.configureTestingModule({
      providers: [
        AuctionItemService,
        {
          provide: AuctionMarketApiService,
          useValue: api,
        },
        {
          provide: QueryService,
          useValue: {
            queryParams,
            region: signal(undefined),
            realmSlug: signal(undefined),
            locale: signal(undefined),
            navigateWithState,
          },
        },
        {
          provide: RealmSelectionService,
          useValue: {
            auctionHouseDetails: signal({
              connectedRealmId: 1,
              lastModified: '2026-01-01T00:00:00Z',
            }),
            commodityDetails: signal({ connectedRealmId: 1, lastModified: '2026-01-01T00:00:00Z' }),
          },
        },
        {
          provide: LocaleService,
          useValue: {
            activeLocale: signal('en'),
          },
        },
      ],
    });
    service = TestBed.inject(AuctionItemService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('requests the first backend page for the default market query', () => {
    service.getPageByQuery('en_GB', 'eu', 'argent-dawn', defaultMarketBrowserQueryState);

    expect(api.search).toHaveBeenCalledWith(
      'eu',
      'argent-dawn',
      'en_GB',
      0,
      25,
      'itemName',
      'asc',
      undefined,
      undefined,
      undefined,
      undefined,
      undefined,
      undefined,
      undefined,
      undefined,
      undefined,
      'body',
      false,
      { transferCache: false },
    );
  });

  it('goes to the previous page when the current page is after the first page', () => {
    queryParams.set({ ...defaultMarketBrowserQueryState, page: 1 });

    service.goToPreviousPage();

    expect(navigateWithState).toHaveBeenCalledWith({
      ...defaultMarketBrowserQueryState,
      page: 0,
    });
  });

  it('does not go to the previous page from the first page', () => {
    queryParams.set({ ...defaultMarketBrowserQueryState, page: 0 });

    service.goToPreviousPage();

    expect(navigateWithState).not.toHaveBeenCalled();
  });

  it('goes to the next page when another page exists', () => {
    queryParams.set({ ...defaultMarketBrowserQueryState, page: 0 });
    api.search.mockReturnValueOnce(
      of({
        items: [],
        page: { page: 0, pageSize: 25, totalItems: 50, totalPages: 2 },
      }),
    );
    firstValueFrom(
      service.getPageByQuery('en_GB', 'eu', 'argent-dawn', defaultMarketBrowserQueryState),
    );

    service.goToNextPage();

    expect(navigateWithState).toHaveBeenCalledWith({
      ...defaultMarketBrowserQueryState,
      page: 1,
    });
  });

  it('does not go to the next page on the last page', () => {
    queryParams.set({ ...defaultMarketBrowserQueryState, page: 1 });
    const state = { ...defaultMarketBrowserQueryState, page: 1 };
    api.search.mockReturnValueOnce(
      of({
        items: [],
        page: { page: 1, pageSize: 25, totalItems: 50, totalPages: 2 },
      }),
    );
    firstValueFrom(service.getPageByQuery('en_GB', 'eu', 'argent-dawn', state));

    service.goToNextPage();

    expect(navigateWithState).not.toHaveBeenCalled();
  });

  it('does not go to the next page without page metadata', () => {
    queryParams.set({ ...defaultMarketBrowserQueryState, page: 0 });

    service.goToNextPage();

    expect(navigateWithState).not.toHaveBeenCalled();
  });
});
