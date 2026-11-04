import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  ButtonDirective,
  ChartComponent,
  PageFrameComponent,
  SkeletonDirective,
  TableComponent,
  TabOption,
  TabsComponent,
} from '@ui';
import { AdminRunningQuery } from '@api/generated';
import type Highcharts from 'highcharts/esm/highcharts';
import { AdminQueryDialogComponent } from './admin-query-dialog.component';
import { AdminSqlEditorComponent } from './admin-sql-editor.component';
import { AdminStatusService } from './admin-status.service';
import { createTableSizeColumns } from './admin-status-table.columns';

const QUERY_PREVIEW_LIMIT = 220;

@Component({
  selector: 'app-admin-status-page',
  imports: [
    AdminQueryDialogComponent,
    AdminSqlEditorComponent,
    ButtonDirective,
    ChartComponent,
    PageFrameComponent,
    SkeletonDirective,
    TableComponent,
    TabsComponent,
  ],
  templateUrl: './admin-status.page.html',
  host: {
    class: 'flex min-h-0 flex-1 flex-col',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminStatusPage {
  private readonly service = inject(AdminStatusService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly loading = this.service.loading.asReadonly();
  protected readonly status = this.service.status.asReadonly();
  protected readonly error = this.service.error.asReadonly();
  protected readonly lastUpdated = this.service.lastUpdated.asReadonly();
  protected readonly backgroundUpdates = this.service.backgroundUpdates.asReadonly();
  protected readonly history = this.service.history.asReadonly();
  protected readonly tableSizeColumns = signal(createTableSizeColumns());
  protected readonly selectedQuery = signal<AdminRunningQuery | null>(null);
  protected readonly runningQuerySearch = signal('');
  protected readonly filteredRunningQueries = computed(() => {
    const queries = this.status()?.runningQueries ?? [];
    const needle = this.runningQuerySearch().trim().toLowerCase();
    if (!needle) {
      return queries;
    }
    return queries.filter((query) => this.runningQuerySearchText(query).includes(needle));
  });
  protected readonly contentLoading = computed(() => this.loading() && !this.status());
  protected readonly runningQueriesLoading = computed(
    () => this.loading() && this.filteredRunningQueries().length === 0,
  );
  protected readonly runningQuerySkeletonRows = [0, 1, 2, 3, 4] as const;
  protected readonly toolTabs: readonly TabOption[] = [
    { id: 'queries', label: 'Running queries' },
    { id: 'tables', label: 'Table and index sizes' },
    { id: 'sql', label: 'SQL editor' },
  ];
  protected readonly activeToolTab = signal('queries');
  protected readonly memoryPercent = computed(() => {
    const server = this.status()?.server;
    if (!server?.maxMemoryMb) return 0;
    return Math.min(100, Math.round((server.usedMemoryMb / server.maxMemoryMb) * 100));
  });
  protected readonly cpuHistoryOptions = computed<Highcharts.Options>(() => {
    const points = this.history();
    return this.lineOptions({
      suffix: '%',
      yTitle: 'Load (%)',
      max: 100,
      series: [
        {
          name: 'Process CPU',
          data: points.map((point) => [
            point.timestamp,
            point.processCpuLoad === null ? null : Number(point.processCpuLoad.toFixed(1)),
          ]),
          color: 'var(--color-primary-container)',
        },
        {
          name: 'System CPU',
          data: points.map((point) => [
            point.timestamp,
            point.systemCpuLoad === null ? null : Number(point.systemCpuLoad.toFixed(1)),
          ]),
          color: 'var(--color-secondary-container)',
        },
      ],
    });
  });
  protected readonly memoryHistoryOptions = computed<Highcharts.Options>(() => {
    const points = this.history();
    return this.lineOptions({
      suffix: ' MB',
      yTitle: 'Memory (MB)',
      series: [
        {
          name: 'Used memory',
          data: points.map((point) => [point.timestamp, point.usedMemoryMb]),
          color: 'var(--color-primary)',
        },
        {
          name: 'Max memory',
          data: points.map((point) => [point.timestamp, point.maxMemoryMb]),
          color: 'var(--color-outline)',
        },
      ],
    });
  });
  protected readonly threadsHistoryOptions = computed<Highcharts.Options>(() => {
    const points = this.history();
    return this.lineOptions({
      suffix: '',
      yTitle: 'Connections',
      series: [
        {
          name: 'Connected threads',
          data: points.map((point) => [point.timestamp, point.threadsConnected]),
          color: 'var(--color-tertiary-container)',
        },
        {
          name: 'Max used connections',
          data: points.map((point) => [point.timestamp, point.maxUsedConnections]),
          color: 'var(--color-outline)',
        },
      ],
    });
  });

  constructor() {
    const stopPolling = this.service.startPagePolling();
    this.destroyRef.onDestroy(stopPolling);
  }

  protected refresh(): void {
    this.service.refresh(true).pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
  }

  protected updateBackgroundUpdates(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.service.setBackgroundUpdates(input.checked);
  }

  protected updateRunningQuerySearch(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.runningQuerySearch.set(input.value);
  }

  protected uptimeLabel(seconds: number | undefined): string {
    if (seconds === undefined) {
      return '-';
    }
    const days = Math.floor(seconds / 86_400);
    const hours = Math.floor((seconds % 86_400) / 3_600);
    const minutes = Math.floor((seconds % 3_600) / 60);
    if (days > 0) {
      return `${days}d ${hours}h`;
    }
    if (hours > 0) {
      return `${hours}h ${minutes}m`;
    }
    return `${minutes}m`;
  }

  protected percentLabel(value: number | null | undefined): string {
    if (value === null || value === undefined) {
      return '-';
    }
    return `${value.toFixed(1)}%`;
  }

  protected lastUpdatedLabel(): string {
    const value = this.lastUpdated();
    return value ? value.toLocaleTimeString() : '-';
  }

  private lineOptions(config: {
    readonly suffix: string;
    readonly yTitle: string;
    readonly max?: number;
    readonly series: readonly {
      readonly name: string;
      readonly data: readonly (readonly [number, number | null])[];
      readonly color: string;
    }[];
  }): Highcharts.Options {
    return {
      chart: { type: 'line' },
      legend: {
        enabled: true,
        itemStyle: { color: 'var(--color-outline)' },
        itemHoverStyle: { color: 'var(--color-on-surface)' },
      },
      tooltip: {
        enabled: true,
        shared: true,
        valueSuffix: config.suffix,
        xDateFormat: '%H:%M',
      },
      xAxis: {
        type: 'datetime',
        title: { text: 'Time (HH:MM)', style: { color: 'var(--color-outline)' } },
        tickPixelInterval: 90,
        labels: {
          format: '{value:%H:%M}',
          style: { color: 'var(--color-outline)' },
        },
      },
      yAxis: {
        min: 0,
        max: config.max,
        title: { text: config.yTitle, style: { color: 'var(--color-outline)' } },
        labels: {
          format: `{value}${config.suffix}`,
          style: { color: 'var(--color-outline)' },
        },
      },
      series: config.series.map((series) => ({
        type: 'line',
        name: series.name,
        data: series.data.map(([timestamp, value]) => [timestamp, value]),
        color: series.color,
        connectNulls: false,
      })),
    };
  }

  protected startedAtLabel(value: string): string {
    const date = new Date(value);
    return Number.isNaN(date.getTime())
      ? '-'
      : date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  }

  protected durationLabel(timeMs: number): string {
    if (timeMs >= 1000) {
      return `${(timeMs / 1000).toFixed(1)} s`;
    }
    return `${Math.round(timeMs)} ms`;
  }

  protected queryPreview(query: AdminRunningQuery): string {
    const sql = query.info?.trim() || '-';
    if (sql.length <= QUERY_PREVIEW_LIMIT) {
      return sql;
    }
    return `${sql.slice(0, QUERY_PREVIEW_LIMIT).trimEnd()}...`;
  }

  protected isQueryTruncated(query: AdminRunningQuery): boolean {
    return (query.info?.trim().length ?? 0) > QUERY_PREVIEW_LIMIT;
  }

  protected nullableLabel(value: string | null | undefined): string {
    return value?.trim() || '-';
  }

  protected nullableNumberLabel(value: number | null | undefined): string {
    return value === null || value === undefined ? '-' : new Intl.NumberFormat().format(value);
  }

  protected showQuery(query: AdminRunningQuery): void {
    this.selectedQuery.set(query);
  }

  protected closeQuery(): void {
    this.selectedQuery.set(null);
  }

  private runningQuerySearchText(query: AdminRunningQuery): string {
    return [
      query.id,
      query.queryId,
      query.tid,
      query.command,
      query.state,
      query.time,
      query.timeMs,
      this.startedAtLabel(query.startedAt),
      this.durationLabel(query.timeMs),
      query.info,
      query.stage,
      query.maxStage,
      query.progress,
      query.memoryUsed,
      query.examinedRows,
    ]
      .filter((value) => value !== null && value !== undefined)
      .join(' ')
      .toLowerCase();
  }
}
