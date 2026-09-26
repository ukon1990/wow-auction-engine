import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AuctionHouse, AuctionHousePage as AuctionHousePageDto } from '@api/generated';
import { QueryService } from '@core/services/query.service';
import { ToastService } from '@core/services/toast.service';
import { of, throwError } from 'rxjs';
import { AuctionHousePage } from './auction-house.page';
import {
  AuctionHouseQueryState,
  AuctionHouseService,
  defaultAuctionHouseQueryState,
} from './auction-house.service';

describe('AuctionHousePage', () => {
  const house: AuctionHouse = {
    id: 1,
    connectedRealmId: 1080,
    region: 'Europe',
    realms: [
      {
        region: 'eu',
        name: 'Bloodhoof',
        category: '',
        slug: 'bloodhoof',
        locale: 'en_US',
        timezone: 'Europe/Oslo',
      },
    ],
    autoUpdate: true,
    lowestDelay: 90,
    avgDelay: 120,
    highestDelay: 150,
    nextUpdate: '2026-09-26T17:34:00Z',
  };
  const result: AuctionHousePageDto = {
    items: [house],
    page: { page: 0, pageSize: 25, totalItems: 41, totalPages: 2 },
    sort: { sortBy: 'name', sortDirection: 'asc' },
  };
  let query: ReturnType<typeof signal<AuctionHouseQueryState>>;
  let service: {
    getPageByQuery: ReturnType<typeof vitest.fn>;
    fetchById: ReturnType<typeof vitest.fn>;
    update: ReturnType<typeof vitest.fn>;
    clearPages: ReturnType<typeof vitest.fn>;
  };
  let page: AuctionHousePage;
  let toast: { success: ReturnType<typeof vitest.fn>; error: ReturnType<typeof vitest.fn> };

  beforeEach(async () => {
    query = signal(defaultAuctionHouseQueryState);
    service = {
      getPageByQuery: vitest.fn(() => of(result)),
      fetchById: vitest.fn(() => of(house)),
      update: vitest.fn(() => of(house)),
      clearPages: vitest.fn(),
    };
    toast = { success: vitest.fn(), error: vitest.fn() };
    await TestBed.configureTestingModule({
      imports: [AuctionHousePage],
      providers: [
        { provide: AuctionHouseService, useValue: service },
        { provide: ToastService, useValue: toast },
        {
          provide: QueryService,
          useValue: {
            queryParams: query,
            navigateWithState: (state: AuctionHouseQueryState) => query.set(state),
          },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(AuctionHousePage);
    page = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('loads 25 rows per page and requests new pages and sorts from the backend', async () => {
    expect(service.getPageByQuery).toHaveBeenCalledWith(defaultAuctionHouseQueryState);
    page.onPageChange(1);
    TestBed.flushEffects();
    expect(service.getPageByQuery).toHaveBeenLastCalledWith({
      ...defaultAuctionHouseQueryState,
      page: 1,
    });
    page.onSortingChange([{ id: 'avgDelay', desc: true }]);
    TestBed.flushEffects();
    expect(service.getPageByQuery).toHaveBeenLastCalledWith({
      ...defaultAuctionHouseQueryState,
      page: 0,
      sortBy: 'avgDelay',
      sortDirection: 'desc',
    });
  });

  it('only enables sorting for backend-supported visible columns', () => {
    const byId = (id: string) => page.columns.find((column) => column.id === id);
    expect(byId('connectedRealmId')?.enableSorting).toBe(false);
    expect(byId('realms')?.enableSorting).toBe(false);
    expect(byId('autoUpdate')?.enableSorting).toBe(false);
    expect(byId('actions')?.enableSorting).toBe(false);
    expect(byId('avgDelay')?.enableSorting).not.toBe(false);
  });

  it('disables Update now when auto update is off and refreshes after a successful action', async () => {
    await page.updateNow({ ...house, autoUpdate: false });
    expect(service.update).not.toHaveBeenCalled();
    await page.updateNow(house);
    expect(service.update).toHaveBeenCalledWith(1080, {
      nextUpdate: expect.any(String),
    });
    expect(service.clearPages).toHaveBeenCalled();
    expect(service.getPageByQuery).toHaveBeenCalledTimes(2);
  });

  it('sends the new auto update value and reports a failed update', async () => {
    service.update.mockImplementationOnce(() => throwError(() => new Error('failed')));
    await page.toggleAutoUpdate(house);
    expect(service.update).toHaveBeenCalledWith(1080, { autoUpdate: false });
    expect(toast.error).toHaveBeenCalled();
    expect(service.clearPages).not.toHaveBeenCalled();
  });

  it('loads the selected record and saves supported detail fields', async () => {
    await page.openDetails(house);
    expect(service.fetchById).toHaveBeenCalledWith(1080);
    page.autoUpdateInput.set(false);
    page.nextUpdateInput.set('2026-09-27T10:00');
    await page.saveDetails();
    expect(service.update).toHaveBeenCalledWith(1080, {
      autoUpdate: false,
      nextUpdate: new Date('2026-09-27T10:00').toISOString(),
    });
  });

  it('keeps a load error visible until retry succeeds', async () => {
    service.getPageByQuery.mockImplementationOnce(() => throwError(() => new Error('failed')));
    page.retryLoad();
    await vitest.waitFor(() => expect(page.loadError()).toContain('Could not load'));
    expect(service.getPageByQuery).toHaveBeenCalledTimes(2);
    page.retryLoad();
    await vitest.waitFor(() => expect(page.loadError()).toBeNull());
    expect(service.getPageByQuery).toHaveBeenCalledTimes(3);
  });
});
