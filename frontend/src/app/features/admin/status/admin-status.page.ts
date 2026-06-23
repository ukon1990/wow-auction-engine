import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ChartComponent, ItemStatCardComponent, PageFrameComponent, TableComponent } from '@ui';
import { AdminRunningQuery } from '@api/generated';
import type Highcharts from 'highcharts/esm/highcharts';
import { AdminStatusService } from './admin-status.service';
import { createTableSizeColumns } from './admin-status-table.columns';

const QUERY_PREVIEW_LIMIT = 220;

@Component({
  selector: 'app-admin-status-page',
  imports: [ChartComponent, ItemStatCardComponent, PageFrameComponent, TableComponent],
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
  protected readonly memoryGaugeOptions = computed<Highcharts.Options>(() => {
    const status = this.status();
    return this.gaugeOptions({
      title: 'Memory',
      value: status?.server.usedMemoryMb ?? 0,
      max: Math.max(status?.server.maxMemoryMb ?? 1, 1),
      unit: 'MB',
    });
  });
  protected readonly threadsGaugeOptions = computed<Highcharts.Options>(() => {
    const status = this.status();
    const threadsConnected = status?.connections.threadsConnected ?? 0;
    return this.gaugeOptions({
      title: 'Threads',
      value: threadsConnected,
      max: Math.max(status?.connections.maxUsedConnections ?? 1, threadsConnected, 1),
      unit: '',
    });
  });
  protected readonly cpuHistoryOptions = computed<Highcharts.Options>(() => {
    const points = this.history();
    return this.lineOptions({
      suffix: '%',
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

  private gaugeOptions(config: {
    readonly title: string;
    readonly value: number;
    readonly max: number;
    readonly unit: string;
  }): Highcharts.Options {
    const value = Math.min(Math.max(config.value, 0), config.max);
    const remaining = Math.max(config.max - value, 0);
    return {
      chart: {
        type: 'pie',
        height: 260,
        spacing: [0, 0, 0, 0],
      },
      plotOptions: {
        pie: {
          animation: false,
          borderWidth: 0,
          center: ['50%', '78%'],
          dataLabels: { enabled: false },
          enableMouseTracking: false,
          innerSize: '72%',
          size: '140%',
          startAngle: -90,
          endAngle: 90,
          states: {
            inactive: { opacity: 1 },
            hover: { enabled: false },
          },
        },
      },
      subtitle: {
        text: `${value}${config.unit ? ` ${config.unit}` : ''}`,
        align: 'center',
        verticalAlign: 'middle',
        y: 42,
        style: {
          color: 'var(--color-on-surface)',
          fontSize: '1rem',
          fontWeight: '600',
        },
      },
      yAxis: undefined,
      series: [
        {
          type: 'pie',
          name: config.title,
          data: [
            {
              name: config.title,
              y: value,
              color: gaugeColor(value / config.max),
            },
            {
              name: 'Available',
              y: remaining,
              color: 'rgba(255,255,255,0.08)',
            },
          ],
          dataLabels: {
            enabled: false,
          },
        },
      ],
    };
  }

  private lineOptions(config: {
    readonly suffix: string;
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
        labels: {
          format: '{value:%H:%M}',
          style: { color: 'var(--color-outline)' },
        },
      },
      yAxis: {
        min: 0,
        title: { text: undefined },
        labels: {
          format: `{value}${config.suffix}`,
          style: { color: 'var(--color-outline)' },
        },
      },
      series: config.series.map((series) => ({
        type: 'line',
        name: series.name,
        data: series.data.map(([x, y]) => [x, y]),
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
}

function gaugeColor(fraction: number): string {
  if (fraction >= 0.9) {
    return 'var(--color-error)';
  }
  if (fraction >= 0.7) {
    return 'var(--color-tertiary-container)';
  }
  return 'var(--color-primary)';
}
