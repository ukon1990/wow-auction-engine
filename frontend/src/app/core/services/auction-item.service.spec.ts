import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of } from 'rxjs';

import { AuctionItemService } from './auction-item.service';
import { AuctionMarketApiService } from '@api/generated';
import { QueryService } from '@core/services/query.service';
import { RealmSelectionService } from '@core/services/realm-selection.service';
import { LocaleService } from '@core/services/locale.service';
import { defaultMarketBrowserQueryState } from '@core/mappers/market-browser-query.mapper';

describe('AuctionItemService', () => {
  let service: AuctionItemService;
  let api: { filters: ReturnType<typeof vitest.fn>; search: ReturnType<typeof vitest.fn> };

  beforeEach(() => {
    const queryParams = signal({
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
            navigateWithState: vitest.fn(),
          },
        },
        {
          provide: RealmSelectionService,
          useValue: {
            auctionHouseDetails: signal(undefined),
            commodityDetails: signal(undefined),
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
});
