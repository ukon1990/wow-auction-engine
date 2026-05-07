import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { of } from 'rxjs';

import { AuctionMarketApiService } from '@api/generated';

import { MarketBrowserCache } from './market-browser.cache';
import { MarketItemDetailService } from './market-item-detail.service';
import { RealmSelectionService } from './realm-selection.service';

describe('MarketItemDetailService caching', () => {
  const variant = { bonusKey: 'b', modifierKey: 'm', petSpeciesId: 0 };

  const api = {
    getAuctionMarketItemDetail: vi.fn(),
    getAuctionMarketItemCraftingAnalytics: vi.fn(),
  } as unknown as AuctionMarketApiService;

  const cache = {
    getItemDetail: vi.fn(),
    setItemDetail: vi.fn(),
    getCraftingAnalytics: vi.fn(),
    setCraftingAnalytics: vi.fn(),
  } as unknown as MarketBrowserCache;

  const realmSelection = {
    selected: vi.fn(() => ({ locale: 'en_US' })),
    marketDataVersion: vi.fn(() => 'ah=1|commodity=2'),
  } as unknown as RealmSelectionService;

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        MarketItemDetailService,
        { provide: AuctionMarketApiService, useValue: api },
        { provide: MarketBrowserCache, useValue: cache },
        { provide: RealmSelectionService, useValue: realmSelection },
      ],
    });
  });

  it('reuses cached item detail for same snapshot+params', async () => {
    const service = TestBed.inject(MarketItemDetailService);
    const cached = { item: { id: 1, name: 'Foo' } } as never;
    (cache.getItemDetail as ReturnType<typeof vi.fn>).mockReturnValue(cached);

    const response = await firstValueFrom(
      service.loadItemDetail('eu', 'draenor', 1, variant, 'realm'),
    );
    expect(response).toBe(cached);
    expect((api.getAuctionMarketItemDetail as ReturnType<typeof vi.fn>).mock.calls.length).toBe(0);
  });

  it('stores crafting analytics response in cache keyed by snapshot+params', async () => {
    const service = TestBed.inject(MarketItemDetailService);
    const response = { dailySeries: [], heatmap: [] } as never;
    (cache.getCraftingAnalytics as ReturnType<typeof vi.fn>).mockReturnValue(undefined);
    (api.getAuctionMarketItemCraftingAnalytics as ReturnType<typeof vi.fn>).mockReturnValue(
      of(response),
    );

    const actual = await firstValueFrom(
      service.loadCraftingAnalytics('eu', 'draenor', 19019, 42, variant),
    );
    expect(actual).toBe(response);
    expect(api.getAuctionMarketItemCraftingAnalytics).toHaveBeenCalledTimes(1);
    expect(cache.setCraftingAnalytics).toHaveBeenCalledTimes(1);
  });
});
