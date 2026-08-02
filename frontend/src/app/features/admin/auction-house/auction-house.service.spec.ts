import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AdminApiService } from '@api/generated';
import { LocaleService } from '@core/services/locale.service';
import { QueryService } from '@core/services/query.service';
import { RealmSelectionService } from '@core/services/realm-selection.service';

import { AuctionHouseService } from './auction-house.service';

describe('AuctionHouseService', () => {
  let service: AuctionHouseService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        AuctionHouseService,
        { provide: AdminApiService, useValue: {} },
        {
          provide: QueryService,
          useValue: {
            queryParams: signal(null),
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
          useValue: { activeLocale: signal('en') },
        },
      ],
    });
    service = TestBed.inject(AuctionHouseService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
