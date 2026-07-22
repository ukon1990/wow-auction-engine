import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { signal } from '@angular/core';
import { of } from 'rxjs';

import { ItemDetailModalService } from '@core/services/item-detail-modal.service';
import { RealmSelectionService } from '@core/services/realm-selection.service';
import { WowheadTooltipService } from '@core/services/wowhead-tooltip';
import { ItemLinkComponent } from './item-link.component';

describe('ItemLinkComponent', () => {
  let fixture: ComponentFixture<ItemLinkComponent>;
  let modal: ItemDetailModalService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ItemLinkComponent],
      providers: [
        {
          provide: Router,
          useValue: {
            url: '/eu/draenor/auctions',
            events: of(),
            createUrlTree: () => ({}) as never,
            serializeUrl: () => '/eu/draenor/item/238197?bonusKey=&modifierKey=&petSpeciesId=0',
          },
        },
        {
          provide: WowheadTooltipService,
          useValue: { show: vitest.fn(), clear: vitest.fn() },
        },
        {
          provide: RealmSelectionService,
          useValue: {
            selected: signal(null),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ItemLinkComponent);
    modal = TestBed.inject(ItemDetailModalService);
    fixture.componentRef.setInput('itemId', 238197);
    fixture.componentRef.setInput('name', 'Test Item');
    fixture.detectChanges();
  });

  it('opens the item modal on primary click', () => {
    const anchor = fixture.nativeElement.querySelector('a') as HTMLAnchorElement;
    anchor.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    expect(modal.state()?.itemId).toBe(238197);
  });

  it('exposes a canonical share href', () => {
    const anchor = fixture.nativeElement.querySelector('a') as HTMLAnchorElement;
    expect(anchor.getAttribute('href')).toContain('/eu/draenor/item/238197');
  });
});
