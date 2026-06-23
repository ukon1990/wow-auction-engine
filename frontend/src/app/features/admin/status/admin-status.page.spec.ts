import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AdminStatus } from '@api/generated';
import { AdminStatusPage } from './admin-status.page';
import { AdminStatusService } from './admin-status.service';

const statusFixture: AdminStatus = {
  connections: {
    maxUsedConnections: 8,
    threadsConnected: 3,
    uptimeSeconds: 3_900,
  },
  server: {
    usedMemoryMb: 100,
    totalMemoryMb: 200,
    freeMemoryMb: 100,
    maxMemoryMb: 500,
    processCpuLoad: 11.2,
    systemCpuLoad: 33.3,
  },
  runningQueries: [
    {
      id: 1,
      queryId: 2,
      tid: 3,
      command: 'Query',
      state: 'Sending data',
      time: 4,
      timeMs: 4000,
      info: 'SELECT * FROM auction',
    },
  ],
  tableSizes: [
    {
      name: 'auction',
      rows: 100,
      tableSizeInMb: 10,
      indexSizeInMb: 5,
      sizeInMb: 15,
      freeTableSizeInMb: 1,
      allocatedTableSize: 16,
    },
  ],
};

describe('AdminStatusPage', () => {
  let fixture: ComponentFixture<AdminStatusPage>;
  let serviceStub: {
    loading: ReturnType<typeof signal<boolean>>;
    status: ReturnType<typeof signal<AdminStatus | null>>;
    error: ReturnType<typeof signal<string | null>>;
    lastUpdated: ReturnType<typeof signal<Date | null>>;
    refresh: ReturnType<typeof vitest.fn>;
  };

  beforeEach(async () => {
    vitest.useFakeTimers();
    serviceStub = {
      loading: signal(false),
      status: signal(statusFixture),
      error: signal(null),
      lastUpdated: signal(new Date('2026-06-23T06:00:00Z')),
      refresh: vitest.fn().mockReturnValue(of(statusFixture)),
    };

    await TestBed.configureTestingModule({
      imports: [AdminStatusPage],
      providers: [{ provide: AdminStatusService, useValue: serviceStub }],
    }).compileComponents();
  });

  afterEach(() => {
    fixture?.destroy();
    vitest.useRealTimers();
  });

  it('loads immediately and then polls every 1000ms', async () => {
    fixture = TestBed.createComponent(AdminStatusPage);

    await vitest.advanceTimersByTimeAsync(0);
    expect(serviceStub.refresh).toHaveBeenCalledWith(true);

    await vitest.advanceTimersByTimeAsync(1000);
    expect(serviceStub.refresh).toHaveBeenCalledWith(false);
  });

  it('stops polling when destroyed', async () => {
    fixture = TestBed.createComponent(AdminStatusPage);
    await vitest.advanceTimersByTimeAsync(0);
    fixture.destroy();
    serviceStub.refresh.mockClear();

    await vitest.advanceTimersByTimeAsync(1000);

    expect(serviceStub.refresh).not.toHaveBeenCalled();
  });

  it('renders stat cards and tables', async () => {
    fixture = TestBed.createComponent(AdminStatusPage);
    await vitest.advanceTimersByTimeAsync(0);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Threads connected');
    expect(text).toContain('Database uptime');
    expect(text).toContain('JVM memory');
    expect(text).toContain('Running queries');
    expect(text).toContain('SELECT * FROM auction');
    expect(text).toContain('Table and index sizes');
    expect(text).toContain('auction');
  });

  it('refreshes manually', async () => {
    fixture = TestBed.createComponent(AdminStatusPage);
    await vitest.advanceTimersByTimeAsync(0);
    fixture.detectChanges();
    serviceStub.refresh.mockClear();

    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    button.click();

    expect(serviceStub.refresh).toHaveBeenCalledWith(true);
  });
});
