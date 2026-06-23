import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { AdminApiService, AdminStatus } from '@api/generated';
import { AdminStatusService } from './admin-status.service';

const statusFixture: AdminStatus = {
  connections: {
    maxUsedConnections: 4,
    threadsConnected: 2,
    uptimeSeconds: 120,
  },
  server: {
    usedMemoryMb: 128,
    totalMemoryMb: 256,
    freeMemoryMb: 128,
    maxMemoryMb: 512,
    processCpuLoad: 12.5,
    systemCpuLoad: 40,
  },
  runningQueries: [],
  tableSizes: [],
};

describe('AdminStatusService', () => {
  let service: AdminStatusService;
  let api: { getAdminStatus: ReturnType<typeof vitest.fn> };

  beforeEach(() => {
    api = {
      getAdminStatus: vitest.fn().mockReturnValue(of(statusFixture)),
    };

    TestBed.configureTestingModule({
      providers: [{ provide: AdminApiService, useValue: api }],
    });
    service = TestBed.inject(AdminStatusService);
  });

  it('updates status and loading state from the API response', () => {
    service.refresh().subscribe();

    expect(api.getAdminStatus).toHaveBeenCalledOnce();
    expect(service.status()).toEqual(statusFixture);
    expect(service.error()).toBeNull();
    expect(service.loading()).toBe(false);
    expect(service.lastUpdated()).toBeInstanceOf(Date);
  });

  it('stores an error when the API request fails', () => {
    api.getAdminStatus.mockReturnValue(throwError(() => new Error('network')));

    service.refresh().subscribe({ error: () => undefined });

    expect(service.error()).toBe('Unable to load admin status.');
    expect(service.loading()).toBe(false);
  });

  it('skips overlapping refresh requests', () => {
    const response = new Subject<AdminStatus>();
    api.getAdminStatus.mockReturnValue(response);

    service.refresh().subscribe();
    service.refresh().subscribe();

    expect(api.getAdminStatus).toHaveBeenCalledOnce();
    response.next(statusFixture);
    response.complete();
  });
});
