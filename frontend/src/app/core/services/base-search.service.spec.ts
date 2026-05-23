import { TestBed } from '@angular/core/testing';
import { Injectable, signal } from '@angular/core';
import { firstValueFrom, Observable, of } from 'rxjs';
import { BaseSearchService, QueryBase, SearchPageBase } from './base-search.service';
import { QueryService } from '@core/services/query.service';
import { RealmSelectionService } from '@core/services/realm-selection.service';
import { LocaleService } from '@core/services/locale.service';

const TEST_LAST_MODIFIED = '2026-01-01T00:00:00Z';

function cacheKeyFor(query: QueryBase): string {
  const queryKey = Object.keys(query).map((key) => `${key}:${query[key as keyof QueryBase]}`);
  return `${queryKey}|c=${TEST_LAST_MODIFIED}-r=${TEST_LAST_MODIFIED};|en|`;
}

@Injectable()
class TestSearchService extends BaseSearchService<SearchPageBase, unknown, unknown, QueryBase> {
  constructor() {
    super({ page: 0, pageSize: 25 }, () => of({ filters: [] }));
  }

  seedPage(query: QueryBase, data: SearchPageBase): void {
    this.cache.set(
      new Map([
        [
          cacheKeyFor(query),
          {
            metadata: {
              commodity: { id: 1, lastModified: TEST_LAST_MODIFIED },
              auctionHouse: { id: 1, lastModified: TEST_LAST_MODIFIED },
              locale: 'en',
            },
            data,
          },
        ],
      ]),
    );
  }
}

describe('BaseService', () => {
  let service: TestSearchService;
  let queryParams: ReturnType<typeof signal<QueryBase>>;
  let navigateWithState: ReturnType<typeof vitest.fn>;

  beforeEach(() => {
    queryParams = signal<QueryBase>({ page: 1, pageSize: 25 });
    navigateWithState = vitest.fn();

    TestBed.configureTestingModule({
      providers: [
        TestSearchService,
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
    service = TestBed.inject(TestSearchService);
  });

  afterEach(() => {
    vitest.useRealTimers();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('goes to the previous page without requiring cached page metadata', () => {
    queryParams.set({ page: 3, pageSize: 25, query: 'cloth' });

    service.goToPreviousPage();

    expect(navigateWithState).toHaveBeenCalledWith({ page: 2, pageSize: 25, query: 'cloth' });
  });

  it('does not go to a previous page before page zero', () => {
    queryParams.set({ page: 0, pageSize: 25 });

    service.goToPreviousPage();

    expect(navigateWithState).not.toHaveBeenCalled();
  });

  it('goes to the next page when cached page metadata has another page', () => {
    queryParams.set({ page: 1, pageSize: 25 });
    cachePage({ page: 1, pageSize: 25, totalItems: 100, totalPages: 4 });

    service.goToNextPage();

    expect(navigateWithState).toHaveBeenCalledWith({ page: 2, pageSize: 25 });
  });

  it('does not go to the next page without cached page metadata', () => {
    queryParams.set({ page: 1, pageSize: 25 });

    service.goToNextPage();

    expect(navigateWithState).not.toHaveBeenCalled();
  });

  it('goes directly to a selected page and clamps to the last page', () => {
    queryParams.set({ page: 1, pageSize: 25 });
    cachePage({ page: 1, pageSize: 25, totalItems: 100, totalPages: 4 });

    service.goToPage(99);

    expect(navigateWithState).toHaveBeenCalledWith({ page: 3, pageSize: 25 });
  });

  it('goes to the first page without requiring cached page metadata', () => {
    queryParams.set({ page: 2, pageSize: 25 });

    service.goToFirstPage();

    expect(navigateWithState).toHaveBeenCalledWith({ page: 0, pageSize: 25 });
  });

  it('goes to the last page from cached page metadata', () => {
    queryParams.set({ page: 1, pageSize: 25 });
    cachePage({ page: 1, pageSize: 25, totalItems: 100, totalPages: 4 });

    service.goToLastPage();

    expect(navigateWithState).toHaveBeenCalledWith({ page: 3, pageSize: 25 });
  });

  it('does not navigate when the requested target is the current page', () => {
    queryParams.set({ page: 1, pageSize: 25 });
    cachePage({ page: 1, pageSize: 25, totalItems: 100, totalPages: 4 });

    service.goToPage(1);

    expect(navigateWithState).not.toHaveBeenCalled();
  });

  it('keeps the last known pagination totals while a new page is loading', async () => {
    vitest.useFakeTimers();
    const cachedPage = { page: 1, pageSize: 25, totalItems: 100, totalPages: 4 };
    const cacheResult = firstValueFrom(
      service.search({ page: 1, pageSize: 25 }, of({ page: cachedPage })),
    );
    await vitest.advanceTimersByTimeAsync(200);
    await cacheResult;
    queryParams.set({ page: 2, pageSize: 25 });

    firstValueFrom(service.search({ page: 2, pageSize: 25 }, new Observable<SearchPageBase>()));

    expect(service.paginationState()).toEqual({
      page: 2,
      pageSize: 25,
      totalItems: 100,
      totalPages: 4,
    });
  });

  it('only subscribes to the latest uncached search after the debounce time', async () => {
    vitest.useFakeTimers();
    const firstPage = { page: { page: 0, pageSize: 25, totalItems: 1, totalPages: 1 } };
    const secondPage = { page: { page: 0, pageSize: 25, totalItems: 2, totalPages: 1 } };
    const firstSubscribe = vitest.fn();
    const secondSubscribe = vitest.fn();

    const firstResult = firstValueFrom(
      service.search(
        { page: 0, pageSize: 25, query: 'cl' },
        new Observable<SearchPageBase>((subscriber) => {
          firstSubscribe();
          subscriber.next(firstPage);
          subscriber.complete();
        }),
      ),
    );
    const secondResult = firstValueFrom(
      service.search(
        { page: 0, pageSize: 25, query: 'cloth' },
        new Observable<SearchPageBase>((subscriber) => {
          secondSubscribe();
          subscriber.next(secondPage);
          subscriber.complete();
        }),
      ),
    );

    await vitest.advanceTimersByTimeAsync(199);

    expect(firstSubscribe).not.toHaveBeenCalled();
    expect(secondSubscribe).not.toHaveBeenCalled();

    await vitest.advanceTimersByTimeAsync(1);

    await expect(firstResult).resolves.toBeNull();
    await expect(secondResult).resolves.toEqual(secondPage);
    expect(firstSubscribe).not.toHaveBeenCalled();
    expect(secondSubscribe).toHaveBeenCalledTimes(1);
  });

  it('does not subscribe twice for duplicate pending searches', async () => {
    vitest.useFakeTimers();
    const page = { page: { page: 0, pageSize: 25, totalItems: 1, totalPages: 1 } };
    const query = { page: 0, pageSize: 25, query: 'cloth' };
    const firstSubscribe = vitest.fn();
    const secondSubscribe = vitest.fn();

    const firstResult = firstValueFrom(
      service.search(
        query,
        new Observable<SearchPageBase>((subscriber) => {
          firstSubscribe();
          subscriber.next(page);
          subscriber.complete();
        }),
      ),
    );
    const secondResult = firstValueFrom(
      service.search(
        query,
        new Observable<SearchPageBase>((subscriber) => {
          secondSubscribe();
          subscriber.next(page);
          subscriber.complete();
        }),
      ),
    );

    await expect(secondResult).resolves.toBeNull();
    await vitest.advanceTimersByTimeAsync(200);

    await expect(firstResult).resolves.toEqual(page);
    expect(firstSubscribe).toHaveBeenCalledTimes(1);
    expect(secondSubscribe).not.toHaveBeenCalled();
  });

  it('returns cached search results without waiting for the debounce time', async () => {
    vitest.useFakeTimers();
    const page = { page: { page: 0, pageSize: 25, totalItems: 1, totalPages: 1 } };
    const query = { page: 0, pageSize: 25, query: 'cloth' };
    const subscribe = vitest.fn();
    service.seedPage(query, page);

    await expect(
      firstValueFrom(
        service.search(
          query,
          new Observable<SearchPageBase>((subscriber) => {
            subscribe();
            subscriber.next({ page: { page: 0, pageSize: 25, totalItems: 2, totalPages: 1 } });
            subscriber.complete();
          }),
        ),
      ),
    ).resolves.toEqual(page);
    expect(subscribe).not.toHaveBeenCalled();
  });

  it('does not subscribe to a pending search when a cached search is requested last', async () => {
    vitest.useFakeTimers();
    const cachedPage = { page: { page: 1, pageSize: 25, totalItems: 2, totalPages: 1 } };
    const pendingSubscribe = vitest.fn();
    service.seedPage({ page: 1, pageSize: 25, query: 'cloth' }, cachedPage);

    const pendingResult = firstValueFrom(
      service.search(
        { page: 0, pageSize: 25, query: 'cl' },
        new Observable<SearchPageBase>((subscriber) => {
          pendingSubscribe();
          subscriber.next({ page: { page: 0, pageSize: 25, totalItems: 1, totalPages: 1 } });
          subscriber.complete();
        }),
      ),
    );
    const cachedResult = firstValueFrom(
      service.search({ page: 1, pageSize: 25, query: 'cloth' }, of(cachedPage)),
    );

    await vitest.advanceTimersByTimeAsync(200);

    await expect(pendingResult).resolves.toBeNull();
    await expect(cachedResult).resolves.toEqual(cachedPage);
    expect(pendingSubscribe).not.toHaveBeenCalled();
    expect(service.isLoading()).toBe(false);
  });

  function cachePage(page: SearchPageBase['page']): void {
    service.seedPage({ page: page.page, pageSize: page.pageSize }, { page });
  }
});
