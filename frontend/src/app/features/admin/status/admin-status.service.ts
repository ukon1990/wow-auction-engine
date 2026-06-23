import { inject, Injectable, signal } from '@angular/core';
import {
  EMPTY,
  finalize,
  Observable,
  Subscription,
  catchError,
  exhaustMap,
  tap,
  timer,
} from 'rxjs';
import { AdminApiService, AdminRunningQuery, AdminStatus } from '@api/generated';

const POLL_INTERVAL_MS = 1000;
const HISTORY_LIMIT = 300;

export interface AdminStatusHistoryPoint {
  readonly timestamp: number;
  readonly threadsConnected: number;
  readonly maxUsedConnections: number;
  readonly usedMemoryMb: number;
  readonly maxMemoryMb: number;
  readonly processCpuLoad: number | null;
  readonly systemCpuLoad: number | null;
}

@Injectable({
  providedIn: 'root',
})
export class AdminStatusService {
  readonly loading = signal(false);
  readonly status = signal<AdminStatus | null>(null);
  readonly error = signal<string | null>(null);
  readonly lastUpdated = signal<Date | null>(null);
  readonly backgroundUpdates = signal(false);
  readonly history = signal<readonly AdminStatusHistoryPoint[]>([]);

  private readonly api = inject(AdminApiService);
  private inFlight = false;
  private foregroundConsumers = 0;
  private pollingSubscription: Subscription | null = null;
  private queriesByKey = new Map<string, AdminRunningQuery>();

  startPagePolling(): () => void {
    this.foregroundConsumers += 1;
    this.syncPolling(true);
    return () => {
      this.foregroundConsumers = Math.max(0, this.foregroundConsumers - 1);
      this.syncPolling(false);
    };
  }

  setBackgroundUpdates(enabled: boolean): void {
    this.backgroundUpdates.set(enabled);
    this.syncPolling(false);
  }

  refresh(showLoading = true): Observable<AdminStatus> {
    if (this.inFlight) {
      return EMPTY;
    }

    this.inFlight = true;
    if (showLoading) {
      this.loading.set(true);
    }

    return this.api.getAdminStatus().pipe(
      tap({
        next: (status) => {
          const timestamp = Date.now();
          const displayStatus = {
            ...status,
            runningQueries: this.mergeRunningQueries(status.runningQueries),
          };
          this.status.set(displayStatus);
          this.error.set(null);
          this.lastUpdated.set(new Date(timestamp));
          this.appendHistory(status, timestamp);
        },
        error: () => {
          this.error.set('Unable to load admin status.');
        },
      }),
      finalize(() => {
        this.inFlight = false;
        if (showLoading) {
          this.loading.set(false);
        }
      }),
    );
  }

  private syncPolling(showInitialLoading: boolean): void {
    const shouldPoll = this.foregroundConsumers > 0 || this.backgroundUpdates();
    if (shouldPoll && !this.pollingSubscription) {
      this.pollingSubscription = timer(0, POLL_INTERVAL_MS)
        .pipe(
          exhaustMap((tick) =>
            this.refresh(showInitialLoading && tick === 0).pipe(catchError(() => EMPTY)),
          ),
        )
        .subscribe();
      return;
    }

    if (!shouldPoll && this.pollingSubscription) {
      this.pollingSubscription.unsubscribe();
      this.pollingSubscription = null;
    }
  }

  private appendHistory(status: AdminStatus, timestamp: number): void {
    const next = [
      ...this.history(),
      {
        timestamp,
        threadsConnected: status.connections.threadsConnected,
        maxUsedConnections: status.connections.maxUsedConnections,
        usedMemoryMb: status.server.usedMemoryMb,
        maxMemoryMb: status.server.maxMemoryMb,
        processCpuLoad: status.server.processCpuLoad ?? null,
        systemCpuLoad: status.server.systemCpuLoad ?? null,
      },
    ];
    this.history.set(next.slice(-HISTORY_LIMIT));
  }

  private mergeRunningQueries(currentQueries: readonly AdminRunningQuery[]): AdminRunningQuery[] {
    const activeKeys = new Set<string>();

    for (const query of currentQueries) {
      const key = queryKey(query);
      activeKeys.add(key);
      this.queriesByKey.set(key, query);
    }

    for (const [key, query] of this.queriesByKey) {
      if (!activeKeys.has(key) && query.state !== 'Completed') {
        this.queriesByKey.set(key, {
          ...query,
          state: 'Completed',
        });
      }
    }

    return [...this.queriesByKey.values()].sort((a, b) => {
      const aCompleted = a.state === 'Completed';
      const bCompleted = b.state === 'Completed';
      if (aCompleted !== bCompleted) {
        return aCompleted ? 1 : -1;
      }
      return b.timeMs - a.timeMs;
    });
  }
}

function queryKey(query: AdminRunningQuery): string {
  return `${query.id}:${query.queryId}:${query.info ?? ''}`;
}
