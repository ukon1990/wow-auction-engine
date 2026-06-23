import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AdminApiService, AdminExpansion, AdminExpansionItemRange } from '@api/generated';
import { ToastService } from '@core/services/toast.service';
import { AdminExpansionService } from './admin-expansion.service';

const expansionFixture: AdminExpansion = {
  id: 1,
  slug: 'vanilla',
  name: 'Vanilla',
  majorVersion: 1,
  displayOrder: 1,
};

const rangeFixture: AdminExpansionItemRange = {
  id: 10,
  expansion: expansionFixture,
  startItemId: 1,
  endItemId: 100,
  source: 'manual',
  enabled: true,
  createdAt: '2026-06-23T10:00:00Z',
  updatedAt: '2026-06-23T10:00:00Z',
};

describe('AdminExpansionService', () => {
  let service: AdminExpansionService;
  let api: {
    listExpansions: ReturnType<typeof vitest.fn>;
    listExpansionRanges: ReturnType<typeof vitest.fn>;
    createExpansionRange: ReturnType<typeof vitest.fn>;
    applyExpansionRanges: ReturnType<typeof vitest.fn>;
  };
  let toast: { error: ReturnType<typeof vitest.fn> };

  beforeEach(() => {
    api = {
      listExpansions: vitest.fn().mockReturnValue(of([expansionFixture])),
      listExpansionRanges: vitest.fn().mockReturnValue(of([rangeFixture])),
      createExpansionRange: vitest.fn().mockReturnValue(of(rangeFixture)),
      applyExpansionRanges: vitest.fn(),
    };
    toast = { error: vitest.fn() };

    TestBed.configureTestingModule({
      providers: [
        AdminExpansionService,
        { provide: AdminApiService, useValue: api },
        { provide: ToastService, useValue: toast },
      ],
    });
    service = TestBed.inject(AdminExpansionService);
  });

  it('loads expansions and ranges', () => {
    service.load().subscribe();

    expect(api.listExpansions).toHaveBeenCalledOnce();
    expect(api.listExpansionRanges).toHaveBeenCalledOnce();
    expect(service.expansions()).toEqual([expansionFixture]);
    expect(service.ranges()).toEqual([rangeFixture]);
    expect(service.loading()).toBe(false);
  });

  it('stores load errors and shows a toast', () => {
    api.listExpansions.mockReturnValue(throwError(() => new Error('network')));

    service.load().subscribe({ error: () => undefined });

    expect(service.error()).toBe('Unable to load expansion data.');
    expect(toast.error).toHaveBeenCalledWith('Unable to load expansion data.');
  });

  it('refreshes ranges after create', () => {
    const updatedRange = { ...rangeFixture, id: 11 };
    api.createExpansionRange.mockReturnValue(of(updatedRange));
    api.listExpansionRanges.mockReturnValue(of([updatedRange]));

    service
      .createRange({
        expansionId: 1,
        startItemId: 1,
        endItemId: 50,
        source: 'manual',
        enabled: true,
      })
      .subscribe();

    expect(api.createExpansionRange).toHaveBeenCalledOnce();
    expect(api.listExpansionRanges).toHaveBeenCalledOnce();
    expect(service.ranges()).toEqual([updatedRange]);
  });
});
