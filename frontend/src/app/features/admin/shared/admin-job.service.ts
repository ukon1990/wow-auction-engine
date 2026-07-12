import { inject, Injectable, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { AdminApiService, AdminJob } from '@api/generated';
import {
  EMPTY,
  Subscription,
  catchError,
  defer,
  exhaustMap,
  retry,
  tap,
  throwError,
  timer,
} from 'rxjs';

const POLL_INTERVAL_MS = 2000;
const MAX_POLL_RETRIES = 2;

export const ADMIN_JOB_DOMAIN_ITEM = 'item';
export const ADMIN_JOB_DOMAIN_PROFESSION = 'profession';
export const APPLY_EXPANSION_RANGES_OPERATION = 'apply-expansion-ranges';
export const FETCH_EXPANSION_RANGE_ITEMS_OPERATION = 'fetch-expansion-range-items';
export const SYNC_PROFESSIONS_OPERATION = 'sync-professions';

@Injectable({
  providedIn: 'root',
})
export class AdminJobService {
  readonly activeJob = signal<AdminJob | null>(null);
  readonly dismissed = signal(false);
  readonly pollingError = signal<string | null>(null);

  private readonly api = inject(AdminApiService);
  private pollingSubscription: Subscription | null = null;

  trackJob(job: AdminJob): void {
    this.dismissed.set(false);
    this.pollingError.set(null);
    this.activeJob.set(job);
    this.stopPolling();

    if (job.status !== AdminJob.StatusEnum.Running) {
      return;
    }

    this.pollingSubscription = timer(POLL_INTERVAL_MS, POLL_INTERVAL_MS)
      .pipe(
        exhaustMap(() =>
          defer(() => this.api.getAdminJob(job.id)).pipe(
            retry({
              count: MAX_POLL_RETRIES,
              delay: (error: unknown, retryCount) =>
                isTerminalPollingError(error)
                  ? throwError(() => error)
                  : timer(POLL_INTERVAL_MS * retryCount),
            }),
            catchError(() => {
              this.stopPolling();
              this.activeJob.set(null);
              this.pollingError.set(
                $localize`:@@admin.jobs.pollingError:Unable to monitor the admin job. You can try starting it again.`,
              );
              return EMPTY;
            }),
          ),
        ),
        tap((updated) => {
          this.activeJob.set(updated);
          if (updated.status !== AdminJob.StatusEnum.Running) {
            this.stopPolling();
          }
        }),
      )
      .subscribe();
  }

  dismiss(): void {
    this.dismissed.set(true);
  }

  stopPolling(): void {
    this.pollingSubscription?.unsubscribe();
    this.pollingSubscription = null;
  }
}

function isTerminalPollingError(error: unknown): boolean {
  return (
    error instanceof HttpErrorResponse &&
    (error.status === 401 || error.status === 403 || error.status === 404)
  );
}
