import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { exhaustMap, timer } from 'rxjs';
import { ItemStatCardComponent, PageFrameComponent, TableComponent } from '@ui';
import { AdminStatusService } from './admin-status.service';
import { createRunningQueryColumns, createTableSizeColumns } from './admin-status-table.columns';

const POLL_INTERVAL_MS = 1000;

@Component({
  selector: 'app-admin-status-page',
  imports: [ItemStatCardComponent, PageFrameComponent, TableComponent],
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
  protected readonly runningQueryColumns = signal(createRunningQueryColumns());
  protected readonly tableSizeColumns = signal(createTableSizeColumns());

  constructor() {
    timer(0, POLL_INTERVAL_MS)
      .pipe(
        exhaustMap((tick) => this.service.refresh(tick === 0)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  protected refresh(): void {
    this.service.refresh(true).subscribe();
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
}
