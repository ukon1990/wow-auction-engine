import { computed, effect, inject, Injectable, signal } from '@angular/core';
import { RealmSelectionService } from '@core/services/realm-selection.service';
import { LocaleService } from '@core/services/locale.service';
import { catchError, finalize, firstValueFrom, Observable, of, switchMap, tap, timer } from 'rxjs';
import { QueryService } from '@core/services/query.service';
import { AuctionMarketFilter, AuctionMarketFilterResponse } from '@api/generated';
import { MarketBrowserQueryState } from '@core/models/market-browser.models';
import { toFilterSections } from '@core/mappers/filter-mapper';
import { FilterSection, SortingState } from '@ui';
import { ToastService } from '@core/services/toast.service';

export type QueryBase = {
  query?: string;
  page: number;
  pageSize: number;
  sortBy?: string;
  sortDirection?: 'asc' | 'desc';
};

export type PageMetadataBase = {
  page: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
};

export type SearchPageBase = {
  page: PageMetadataBase;
};

export const BASE_QUERY = (): QueryBase => ({
  page: 0,
  pageSize: 25,
});

type Metadata = {
  id: number;
  lastModified: string;
};
interface CacheEntry<DataType> {
  metadata: {
    commodity: Metadata;
    auctionHouse: Metadata;
    locale: string;
  };
  data: DataType;
}

type SearchSource<PageType> = Observable<PageType> | (() => Observable<PageType>);

const SEARCH_DEBOUNCE_MS = 200;

@Injectable({
  providedIn: 'root',
})
export abstract class BaseSearchService<
  PageType extends SearchPageBase,
  SingularType,
  OutputDataType,
  QueryParams extends QueryBase,
> {
  readonly isLoading = signal<boolean>(false);
  readonly isLoadingFilters = signal<boolean>(false);
  protected readonly filterDefinitions = signal<readonly AuctionMarketFilter[]>([]);
  readonly filterSections = computed(() => {
    const filters = this.filterDefinitions();
    if (!filters.length) return [];
    return this.mapFiltersToSections(filters, this.currentQueryState());
  });
  protected readonly cache = signal(new Map<string, CacheEntry<PageType>>());
  protected readonly cacheById = signal(new Map<string, CacheEntry<SingularType>>());
  private readonly latestPageMetadata = signal<PageMetadataBase | undefined>(undefined);
  protected readonly queryService = inject(QueryService<QueryParams>);
  readonly queryParams = this.queryService.queryParams;
  readonly pageData = computed(() => {
    const query = this.queryParams();
    if (!query) return undefined;
    const key = this.getCacheKey(query);
    const cachedValue = this.cache().get(key);
    return cachedValue?.data;
  });
  readonly paginationState = computed(() => {
    const pageData = this.pageData();
    if (pageData) return pageData.page;

    const query = this.queryParams();
    const latestPageMetadata = this.latestPageMetadata();
    if (!query || !latestPageMetadata || !this.isLoading()) return undefined;

    return {
      ...latestPageMetadata,
      page: query.page,
      pageSize: query.pageSize,
    };
  });
  private readonly realmService = inject(RealmSelectionService);
  private readonly locale = inject(LocaleService);
  private readonly toast = inject(ToastService);
  private readonly auctionHouseDetails = this.realmService.auctionHouseDetails;
  private readonly commodityDetails = this.realmService.commodityDetails;
  protected readonly defaultQueryParams: QueryParams;
  private searchRequestId = 0;
  private pendingSearchKey: string | undefined;

  private readonly _filterEffect = effect(() => {
    const region = this.queryService.region();
    const slug = this.queryService.realmSlug();
    if (!region || !slug) return;

    firstValueFrom(this.fetchFilterDefinitions());
  });

  protected constructor(
    defaultQueryParams: QueryParams,
    private getFiltersCallback: (
      region: 'us' | 'eu' | 'kr' | 'tw',
      realmSlug: string,
      locale?: string,
    ) => Observable<AuctionMarketFilterResponse>,
  ) {
    this.defaultQueryParams = defaultQueryParams;
  }

  getById(
    id: number,
    query: QueryParams,
    observable: Observable<SingularType>,
  ): Observable<SingularType> {
    const cacheKey = this.getCacheKey(query, id);
    const cache = this.cacheById().get(cacheKey);
    if (cache) return of(cache.data);
    const metadata = this.getAhMetadata();

    // Realm and commodity ah details and locale must be available before we can search
    if (!metadata) return observable;

    this.isLoading.set(true);
    return observable.pipe(
      tap((data: SingularType) => {
        const cache = this.cacheById();
        cache.set(cacheKey, { metadata, data });
        this.cacheById.set(cache);
      }),
      catchError((error) => {
        this.toast.error(
          $localize`:@@search.loadItemError:Could not load item details. Please try again later.`,
        );
        console.error(error);
        return of();
      }),
      finalize(() => this.isLoading.set(false)),
    );
  }

  search(query: QueryParams, source: SearchSource<PageType>): Observable<PageType | null> {
    const cacheKey = this.getCacheKey(query);
    const cache = this.cache().get(cacheKey);
    if (cache) {
      this.searchRequestId++;
      this.pendingSearchKey = undefined;
      this.isLoading.set(false);
      return of(cache.data);
    }
    if (this.pendingSearchKey === cacheKey) return of(null);

    const metadata = this.getAhMetadata();

    // Realm and commodity ah details and locale must be available before we can search
    if (!metadata) return this.resolveSearchSource(source);

    const requestId = ++this.searchRequestId;
    this.pendingSearchKey = cacheKey;
    this.isLoading.set(true);
    return timer(SEARCH_DEBOUNCE_MS).pipe(
      switchMap(() => {
        if (requestId !== this.searchRequestId) return of(null);

        return this.resolveSearchSource(source).pipe(
          tap((data: PageType) => {
            const cache = this.cache();
            cache.set(cacheKey, { metadata, data });
            this.cache.set(new Map(cache));
            this.latestPageMetadata.set(data.page);
          }),
          catchError((error) => {
            this.toast.error(
              $localize`:@@search.loadResultsError:Could not load search results. Please try again later.`,
            );
            console.error(error);
            return of();
          }),
        );
      }),
      finalize(() => {
        if (requestId === this.searchRequestId) {
          this.pendingSearchKey = undefined;
          this.isLoading.set(false);
        }
      }),
    );
  }

  private resolveSearchSource(source: SearchSource<PageType>): Observable<PageType> {
    return typeof source === 'function' ? source() : source;
  }

  protected currentQueryState(): QueryParams {
    return this.queryParams() ?? this.defaultQueryParams;
  }

  protected navigateQueryState(state: QueryParams): void {
    this.queryService.navigateWithState(state);
  }

  protected mapFiltersToSections(
    filters: readonly AuctionMarketFilter[],
    state: QueryParams,
  ): readonly FilterSection[] {
    return toFilterSections(filters, state as unknown as MarketBrowserQueryState);
  }

  public setSearchQuery(query: string): void {
    this.queryService.navigateWithState({
      ...this.currentQueryState(),
      query,
      page: this.defaultQueryParams.page,
    });
  }
  public upsertSorting(sorting: SortingState): void {
    this.queryService.navigateWithState({
      ...this.currentQueryState(),
      sortBy: sorting[0]?.id,
      sortDirection: sorting[0]?.desc ? 'desc' : 'asc',
      page: this.defaultQueryParams.page,
    });
  }

  public resetFilters(): void {
    this.queryService.navigateWithState(this.defaultQueryParams);
  }

  public goToPreviousPage(): void {
    const state = this.currentQueryState();
    if (state.page <= 0) return;
    this.navigateToPage(state.page - 1, false);
  }

  public goToNextPage(): void {
    const state = this.currentQueryState();
    this.navigateToPage(state.page + 1, true);
  }

  public goToPage(page: number): void {
    this.navigateToPage(page, true);
  }

  public goToFirstPage(): void {
    this.navigateToPage(0, false);
  }

  public goToLastPage(): void {
    const pageMeta = this.paginationState();
    if (!pageMeta) return;
    this.navigateToPage(pageMeta.totalPages - 1, true);
  }

  protected fetchFilterDefinitions() {
    const region = this.queryService.region();
    const slug = this.queryService.realmSlug();
    const locale = this.queryService.locale();
    if (!region || !slug) return of(null);

    this.isLoadingFilters.set(true);
    return this.getFiltersCallback(region, slug, locale).pipe(
      tap((response) => this.filterDefinitions.set(response.filters ?? [])),
      catchError((error) => {
        this.toast.error(
          $localize`:@@search.loadFiltersError:Could not load filter definitions. Please try again later.`,
        );
        console.error(error);
        return of();
      }),
      finalize(() => this.isLoadingFilters.set(false)),
    );
  }

  private navigateToPage(page: number, requirePageMetadata: boolean): void {
    if (!Number.isFinite(page)) return;

    const state = this.currentQueryState();
    const pageMeta = this.paginationState();
    if (requirePageMetadata && !pageMeta) return;

    const maxPage = pageMeta ? Math.max(0, pageMeta.totalPages - 1) : Number.MAX_SAFE_INTEGER;
    const targetPage = Math.max(0, Math.min(Math.floor(page), maxPage));
    if (targetPage === state.page) return;

    this.navigateQueryState({ ...state, page: targetPage });
  }

  private getAhMetadata(): CacheEntry<OutputDataType>['metadata'] | null {
    const auctionHouse = this.auctionHouseDetails();
    const commodity = this.commodityDetails();
    const locale = this.locale.activeLocale();
    if (!auctionHouse || !commodity || !locale) return null;

    return {
      commodity: {
        id: commodity.connectedRealmId,
        lastModified: commodity.lastModified!,
      },
      auctionHouse: {
        id: auctionHouse?.connectedRealmId,
        lastModified: auctionHouse.lastModified!,
      },
      locale,
    };
  }

  private getCacheKey(query: QueryParams, id?: number) {
    const queryKey = Object.keys(query).map((key) => {
      let value: string = (query as never)[key] as string;
      if (Array.isArray(value)) {
        value = value.join(', ');
      }
      return `${key}:${value}`;
    });
    const auctionHouseLastModified = this.auctionHouseDetails()?.lastModified || 0;
    const commodityLastModified = this.commodityDetails()?.lastModified || 0;
    const timeKey = `c=${commodityLastModified}-r=${auctionHouseLastModified};`;

    return `${queryKey}|${timeKey}|${this.locale.activeLocale()}|${id || ''}`;
  }
}
