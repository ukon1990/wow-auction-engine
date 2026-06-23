import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { firstValueFrom, of } from 'rxjs';

import { AuctionItemService } from './auction-item.service';
import { AuctionMarketApiService } from '@api/generated';
import { QueryService } from '@core/services/query.service';
import { RealmSelectionService } from '@core/services/realm-selection.service';
import { LocaleService } from '@core/services/locale.service';
import { defaultMarketBrowserQueryState } from '@core/mappers/market-browser-query.mapper';

const TEST_REGION = 'eu';
const TEST_REALM = 'argent-dawn';
const TEST_LOCALE = 'en_GB';
const TEST_LAST_MODIFIED = '2026-01-01T00:00:00Z';

describe('AuctionItemService', () => {
  let service: AuctionItemService;
  let api: { filters: ReturnType<typeof vitest.fn>; search: ReturnType<typeof vitest.fn> };
  let queryParams: ReturnType<typeof signal<typeof defaultMarketBrowserQueryState>>;
  let region: ReturnType<typeof signal<'eu' | undefined>>;
  let realmSlug: ReturnType<typeof signal<string | undefined>>;
  let locale: ReturnType<typeof signal<string | undefined>>;
  let navigateWithState: ReturnType<typeof vitest.fn>;

  beforeEach(() => {
    vitest.useFakeTimers();
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
    region = signal<'eu' | undefined>(undefined);
    realmSlug = signal<string | undefined>(undefined);
    locale = signal<string | undefined>(undefined);
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
            region,
            realmSlug,
            locale,
            navigateWithState,
          },
        },
        {
          provide: RealmSelectionService,
          useValue: {
            auctionHouseDetails: signal({
              connectedRealmId: 1,
              lastModified: TEST_LAST_MODIFIED,
            }),
            commodityDetails: signal({ connectedRealmId: 1, lastModified: TEST_LAST_MODIFIED }),
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

  afterEach(() => {
    vitest.useRealTimers();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('requests the first backend page for the default market query', async () => {
    api.search.mockReturnValueOnce(of(searchPage(0, 0, 0)));
    const result = firstValueFrom(
      service.getPageByQuery(TEST_LOCALE, TEST_REGION, TEST_REALM, defaultMarketBrowserQueryState),
    );

    expect(api.search).not.toHaveBeenCalled();

    await vitest.advanceTimersByTimeAsync(200);
    await result;

    expect(api.search).toHaveBeenCalledWith(
      TEST_REGION,
      TEST_REALM,
      TEST_LOCALE,
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

  it('goes to the next page when another page exists', async () => {
    queryParams.set({ ...defaultMarketBrowserQueryState, page: 0 });
    await loadPage(defaultMarketBrowserQueryState, searchPage(0, 2));

    service.goToNextPage();

    expect(navigateWithState).toHaveBeenCalledWith({
      ...defaultMarketBrowserQueryState,
      page: 1,
    });
  });

  it('does not go to the next page on the last page', async () => {
    queryParams.set({ ...defaultMarketBrowserQueryState, page: 1 });
    const state = { ...defaultMarketBrowserQueryState, page: 1 };
    await loadPage(state, searchPage(1, 2));

    service.goToNextPage();

    expect(navigateWithState).not.toHaveBeenCalled();
  });

  it('does not go to the next page without page metadata', () => {
    queryParams.set({ ...defaultMarketBrowserQueryState, page: 0 });

    service.goToNextPage();

    expect(navigateWithState).not.toHaveBeenCalled();
  });

  it('does not call the backend again when a cached page is requested', async () => {
    const state = { ...defaultMarketBrowserQueryState, page: 1 };
    await loadPage(state, searchPage(1, 2));
    api.search.mockClear();

    await firstValueFrom(service.getPageByQuery(TEST_LOCALE, TEST_REGION, TEST_REALM, state));

    expect(api.search).not.toHaveBeenCalled();
  });

  it('does not rerun the search effect when caching search results', async () => {
    api.search.mockReturnValue(of(searchPage(0, 2)));

    region.set(TEST_REGION);
    realmSlug.set(TEST_REALM);
    locale.set(TEST_LOCALE);
    TestBed.flushEffects();
    await vitest.advanceTimersByTimeAsync(200);
    await Promise.resolve();

    expect(api.search).toHaveBeenCalledTimes(1);

    TestBed.flushEffects();
    await vitest.advanceTimersByTimeAsync(400);
    await Promise.resolve();

    expect(api.search).toHaveBeenCalledTimes(1);
  });

  async function loadPage(
    state = defaultMarketBrowserQueryState,
    response = searchPage(state.page, 2),
  ): Promise<void> {
    api.search.mockReturnValueOnce(of(response));
    const result = firstValueFrom(
      service.getPageByQuery(TEST_LOCALE, TEST_REGION, TEST_REALM, state),
    );
    await vitest.advanceTimersByTimeAsync(200);
    await result;
  }

  function searchPage(page: number, totalPages: number, totalItems = 50) {
    return {
      items: [],
      page: { page, pageSize: 25, totalItems, totalPages },
    };
  }
});
