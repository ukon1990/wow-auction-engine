import { ParamMap, Params } from '@angular/router';
import { AuctionHousePageSortBy } from '@api/generated';
import { AuctionHouseQueryState, defaultAuctionHouseQueryState } from './auction-house.service';

const sortKeys = new Set<AuctionHousePageSortBy>([
  'name',
  'region',
  'lowestDelay',
  'avgDelay',
  'highestDelay',
  'lastModified',
  'lastAuctionPriceDeleteEvent',
  'lastHistoryDeleteEvent',
  'lastHistoryDeleteEventDaily',
  'nextUpdate',
]);

export function readAuctionHouseQueryState(params: ParamMap): AuctionHouseQueryState {
  const page = Number(params.get('page'));
  const pageSize = Number(params.get('pageSize'));
  const sortBy = params.get('sortBy') as AuctionHousePageSortBy | null;
  return {
    page: Number.isInteger(page) && page >= 0 ? page : 0,
    pageSize: Number.isInteger(pageSize) && pageSize >= 1 && pageSize <= 100 ? pageSize : 25,
    sortBy: sortBy && sortKeys.has(sortBy) ? sortBy : defaultAuctionHouseQueryState.sortBy,
    sortDirection: params.get('sortDirection') === 'desc' ? 'desc' : 'asc',
  };
}

export function toAuctionHouseQueryParams(state: AuctionHouseQueryState): Params {
  return {
    page: state.page || null,
    pageSize: state.pageSize === 25 ? null : state.pageSize,
    sortBy: state.sortBy === 'name' ? null : state.sortBy,
    sortDirection: state.sortDirection === 'asc' ? null : state.sortDirection,
  };
}
