import { convertToParamMap } from '@angular/router';
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
    ).toEqual({ page: 0, pageSize: 25, sortBy: 'name', sortDirection: 'asc' });
  });

  it('round trips page and backend sort state', () => {
    const state = {
      page: 2,
      pageSize: 25,
      sortBy: 'avgDelay' as const,
      sortDirection: 'desc' as const,
    };
    expect(readAuctionHouseQueryState(convertToParamMap(toAuctionHouseQueryParams(state)))).toEqual(
      state,
    );
  });
});
