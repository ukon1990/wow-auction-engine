import { Injectable } from '@angular/core';
import { AdminApiService, AuctionHouse, AuctionHousePage } from '@api/generated';
import { BaseSearchService } from '@core/services/base-search.service';

type AuctionHouseQueryState = {
  query: string;
  page: number;
  pageSize: number;
  sortBy: string;
  sortDirection: 'asc' | 'desc';
};

const defaultAuctionHouseQueryState: AuctionHouseQueryState = {
  query: '',
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
  constructor(private api: AdminApiService) {
    super(defaultAuctionHouseQueryState);
  }
}
