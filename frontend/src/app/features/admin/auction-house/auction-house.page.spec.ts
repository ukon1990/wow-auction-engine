import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AuctionHousePage } from './auction-house.page';
import { AuctionHouseService } from './auction-house.service';

describe('AuctionHousePage', () => {
  let component: AuctionHousePage;
  let fixture: ComponentFixture<AuctionHousePage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuctionHousePage],
      providers: [{ provide: AuctionHouseService, useValue: {} }],
    }).compileComponents();

    fixture = TestBed.createComponent(AuctionHousePage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
