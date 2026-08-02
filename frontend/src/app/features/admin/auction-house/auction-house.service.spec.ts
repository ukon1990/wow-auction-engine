import { TestBed } from '@angular/core/testing';

import { AuctionHouseService } from './auction-house.service';

describe('AuctionHouseService', () => {
  let service: AuctionHouseService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(AuctionHouseService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
