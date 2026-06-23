import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { provideHighchartsTheme } from '@ui';
import { AdminStatus } from '@api/generated';
import type { AdminStatusHistoryPoint } from './admin-status.service';
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
const historyFixture: AdminStatusHistoryPoint[] = [
  {
    timestamp: new Date('2026-06-23T06:00:00Z').getTime(),
    threadsConnected: 2,
    maxUsedConnections: 6,
    usedMemoryMb: 80,
    maxMemoryMb: 500,
    processCpuLoad: 10,
    systemCpuLoad: 30,
  },
  {
    timestamp: new Date('2026-06-23T06:00:01Z').getTime(),
    threadsConnected: 3,
    maxUsedConnections: 8,
    usedMemoryMb: 100,
    maxMemoryMb: 500,
    processCpuLoad: 11.2,
    systemCpuLoad: 33.3,
  },
];

describe('AdminStatusPage', () => {
  let fixture: ComponentFixture<AdminStatusPage>;
  let serviceStub: {
    loading: ReturnType<typeof signal<boolean>>;
    status: ReturnType<typeof signal<AdminStatus | null>>;
    error: ReturnType<typeof signal<string | null>>;
    lastUpdated: ReturnType<typeof signal<Date | null>>;
    backgroundUpdates: ReturnType<typeof signal<boolean>>;
    history: ReturnType<typeof signal<readonly AdminStatusHistoryPoint[]>>;
    refresh: ReturnType<typeof vitest.fn>;
    startPagePolling: ReturnType<typeof vitest.fn>;
    setBackgroundUpdates: ReturnType<typeof vitest.fn>;
  };

  beforeEach(async () => {
    serviceStub = {
      loading: signal(false),
      status: signal(statusFixture),
      error: signal(null),
      lastUpdated: signal(new Date('2026-06-23T06:00:00Z')),
      backgroundUpdates: signal(false),
      history: signal(historyFixture),
      refresh: vitest.fn().mockReturnValue(of(statusFixture)),
      startPagePolling: vitest.fn().mockReturnValue(vitest.fn()),
      setBackgroundUpdates: vitest.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [AdminStatusPage],
      providers: [provideHighchartsTheme(), { provide: AdminStatusService, useValue: serviceStub }],
    }).compileComponents();
  });

  afterEach(() => {
    fixture?.destroy();
  });

  it('starts service-managed page polling', () => {
    fixture = TestBed.createComponent(AdminStatusPage);

    expect(serviceStub.startPagePolling).toHaveBeenCalledOnce();
  });

  it('releases service-managed page polling when destroyed', () => {
    const stopPolling = vitest.fn();
    serviceStub.startPagePolling.mockReturnValue(stopPolling);
    fixture = TestBed.createComponent(AdminStatusPage);

    fixture.destroy();

    expect(stopPolling).toHaveBeenCalledOnce();
  });

  it('renders stat cards and tables', () => {
    fixture = TestBed.createComponent(AdminStatusPage);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Threads connected');
    expect(text).toContain('Database uptime');
    expect(text).toContain('JVM memory');
    expect(text).toContain('Status history');
    expect(text).toContain('CPU over time');
    expect(text).toContain('Memory over time');
    expect(text).toContain('Threads over time');
    expect(text).toContain('Running queries');
    expect(text).toContain('SELECT * FROM auction');
    expect(text).toContain('Table and index sizes');
    expect(text).toContain('auction');
  });

  it('refreshes manually', () => {
    fixture = TestBed.createComponent(AdminStatusPage);
    fixture.detectChanges();
    serviceStub.refresh.mockClear();

    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    button.click();

    expect(serviceStub.refresh).toHaveBeenCalledWith(true);
  });

  it('toggles background updates', () => {
    fixture = TestBed.createComponent(AdminStatusPage);
    fixture.detectChanges();

    const checkbox = fixture.nativeElement.querySelector(
      'input[type="checkbox"]',
    ) as HTMLInputElement;
    checkbox.checked = true;
    checkbox.dispatchEvent(new Event('change'));

    expect(serviceStub.setBackgroundUpdates).toHaveBeenCalledWith(true);
  });
});
