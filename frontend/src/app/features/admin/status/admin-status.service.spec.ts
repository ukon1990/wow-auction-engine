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
const runningQueryFixture = {
  id: 10,
  queryId: 20,
  tid: 30,
  command: 'Query',
  state: 'Running',
  time: 2,
  timeMs: 2000,
  info: 'SELECT * FROM auction',
};

describe('AdminStatusService', () => {
  let service: AdminStatusService;
  let api: { getAdminStatus: ReturnType<typeof vitest.fn> };

  beforeEach(() => {
    vitest.useFakeTimers();
    api = {
      getAdminStatus: vitest.fn().mockReturnValue(of(statusFixture)),
    };

    TestBed.configureTestingModule({
      providers: [{ provide: AdminApiService, useValue: api }],
    });
    service = TestBed.inject(AdminStatusService);
  });

  afterEach(() => {
    service.setBackgroundUpdates(false);
    vitest.useRealTimers();
  });

  it('updates status and loading state from the API response', () => {
    service.refresh().subscribe();

    expect(api.getAdminStatus).toHaveBeenCalledOnce();
    expect(service.status()).toEqual(statusFixture);
    expect(service.error()).toBeNull();
    expect(service.loading()).toBe(false);
    expect(service.lastUpdated()).toBeInstanceOf(Date);
    expect(service.history()).toHaveLength(1);
    expect(service.history()[0]).toMatchObject({
      threadsConnected: 2,
      maxUsedConnections: 4,
      usedMemoryMb: 128,
      maxMemoryMb: 512,
      processCpuLoad: 12.5,
      systemCpuLoad: 40,
    });
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

  it('marks previously seen queries as completed when they disappear', () => {
    api.getAdminStatus
      .mockReturnValueOnce(
        of({
          ...statusFixture,
          runningQueries: [runningQueryFixture],
        }),
      )
      .mockReturnValueOnce(of(statusFixture));

    service.refresh().subscribe();
    service.refresh().subscribe();

    expect(service.status()?.runningQueries).toEqual([
      {
        ...runningQueryFixture,
        state: 'Completed',
      },
    ]);
  });

  it('polls while a page consumer is active and stops when released', async () => {
    const stopPolling = service.startPagePolling();

    await vitest.advanceTimersByTimeAsync(0);
    expect(api.getAdminStatus).toHaveBeenCalledTimes(1);

    await vitest.advanceTimersByTimeAsync(1000);
    expect(api.getAdminStatus).toHaveBeenCalledTimes(2);

    stopPolling();
    await vitest.advanceTimersByTimeAsync(1000);

    expect(api.getAdminStatus).toHaveBeenCalledTimes(2);
  });

  it('keeps polling after page release when background updates are enabled', async () => {
    const stopPolling = service.startPagePolling();
    service.setBackgroundUpdates(true);

    await vitest.advanceTimersByTimeAsync(0);
    expect(api.getAdminStatus).toHaveBeenCalledTimes(1);

    stopPolling();
    await vitest.advanceTimersByTimeAsync(1000);

    expect(api.getAdminStatus).toHaveBeenCalledTimes(2);
  });

  it('stops background polling when background updates are disabled', async () => {
    service.setBackgroundUpdates(true);

    await vitest.advanceTimersByTimeAsync(0);
    expect(api.getAdminStatus).toHaveBeenCalledTimes(1);

    service.setBackgroundUpdates(false);
    await vitest.advanceTimersByTimeAsync(1000);

    expect(api.getAdminStatus).toHaveBeenCalledTimes(1);
  });
});
