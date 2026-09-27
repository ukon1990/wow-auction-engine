import { convertToParamMap } from '@angular/router';
import { AuctionHousePageSortBy } from '@api/generated';
import {
  readAuctionHouseQueryState,
  toAuctionHouseQueryParams,
} from './auction-house-query.mapper';

describe('auction house query mapping', () => {
  it('uses valid defaults and rejects unsupported sort fields', () => {
    expect(
      readAuctionHouseQueryState(
        convertToParamMap({
          page: '-1',
          pageSize: '0',
          sortBy: 'connectedRealmId',
          sortDirection: 'wrong',
        }),
      ),
    ).toEqual({
      page: 0,
      pageSize: 25,
      sortBy: AuctionHousePageSortBy.NextUpdate,
      sortDirection: 'asc',
    });
  });

  it('accepts only generated sort values and omits the default from the URL', () => {
    expect(readAuctionHouseQueryState(convertToParamMap({ sortBy: 'LAST_MODIFIED' })).sortBy).toBe(
      AuctionHousePageSortBy.LastModified,
    );
    expect(readAuctionHouseQueryState(convertToParamMap({ sortBy: 'lastModified' })).sortBy).toBe(
      AuctionHousePageSortBy.NextUpdate,
    );
    expect(
      toAuctionHouseQueryParams({
        page: 0,
        pageSize: 25,
        sortBy: AuctionHousePageSortBy.NextUpdate,
        sortDirection: 'asc',
      })['sortBy'],
    ).toBeNull();
  });

  it('round trips page and backend sort state', () => {
    const state = {
      page: 2,
      pageSize: 25,
      sortBy: AuctionHousePageSortBy.AvgDelay,
      sortDirection: 'desc' as const,
    };
    expect(readAuctionHouseQueryState(convertToParamMap(toAuctionHouseQueryParams(state)))).toEqual(
      state,
    );
  });
});
