import { TestBed } from '@angular/core/testing';
import { Injectable, signal } from '@angular/core';
import { firstValueFrom, Observable, of } from 'rxjs';
import { BaseSearchService, QueryBase, SearchPageBase } from './base-search.service';
import { QueryService } from '@core/services/query.service';
import { RealmSelectionService } from '@core/services/realm-selection.service';
import { LocaleService } from '@core/services/locale.service';

@Injectable()
class TestSearchService extends BaseSearchService<SearchPageBase, unknown, unknown, QueryBase> {
  constructor() {
    super({ page: 0, pageSize: 25 }, () => of({ filters: [] }));
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
    service = TestBed.inject(TestSearchService);
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

  it('keeps the last known pagination totals while a new page is loading', () => {
    cachePage({ page: 1, pageSize: 25, totalItems: 100, totalPages: 4 });
    queryParams.set({ page: 2, pageSize: 25 });

    firstValueFrom(service.search({ page: 2, pageSize: 25 }, new Observable<SearchPageBase>()));

    expect(service.paginationState()).toEqual({
      page: 2,
      pageSize: 25,
      totalItems: 100,
      totalPages: 4,
    });
  });

  function cachePage(page: SearchPageBase['page']): void {
    firstValueFrom(service.search({ page: page.page, pageSize: page.pageSize }, of({ page })));
  }
});
