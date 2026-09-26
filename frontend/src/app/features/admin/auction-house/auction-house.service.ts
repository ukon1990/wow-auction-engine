import { Injectable } from '@angular/core';
import {
  AdminAuctionHouseApiService,
  AuctionHouse,
  AuctionHousePage,
  AuctionHousePageSortBy,
  UpdateAuctionHouse,
} from '@api/generated';
import { BaseSearchService } from '@core/services/base-search.service';
import { Observable } from 'rxjs';

export type AuctionHouseQueryState = {
  page: number;
  pageSize: number;
  sortBy: AuctionHousePageSortBy;
  sortDirection: 'asc' | 'desc';
};

export const defaultAuctionHouseQueryState: AuctionHouseQueryState = {
  page: 0,
  pageSize: 25,
  sortBy: 'name',
  sortDirection: 'asc',
};

@Injectable({
  providedIn: 'root',
})
export class AuctionHouseService extends BaseSearchService<
  AuctionHousePage,
  AuctionHouse,
  never,
  AuctionHouseQueryState
> {
  constructor(private api: AdminAuctionHouseApiService) {
    super(defaultAuctionHouseQueryState);
  }

  getPageByQuery(queryParams: AuctionHouseQueryState): Observable<AuctionHousePage | null> {
    return super.search(queryParams, () =>
      this.api.search(
        queryParams.page,
        queryParams.pageSize,
        queryParams.sortBy,
        queryParams.sortDirection,
      ),
    );
  }

  update(id: number, updateAuctionHouse: UpdateAuctionHouse) {
    return this.api.update(id, updateAuctionHouse);
  }

  fetchById(id: number): Observable<AuctionHouse> {
    return this.api.getById(id);
  }

  clearPages(): void {
    this.cache.set(new Map());
  }
}
