import { inject, Injectable, signal } from '@angular/core';
import { AdminApiService, AdminItemJob } from '@api/generated';
import { EMPTY, Subscription, catchError, exhaustMap, tap, timer } from 'rxjs';

const POLL_INTERVAL_MS = 2000;

export const APPLY_EXPANSION_RANGES_JOB = 'apply-expansion-ranges';
export const FETCH_EXPANSION_RANGE_ITEMS_JOB = 'fetch-expansion-range-items';

@Injectable({
  providedIn: 'root',
})
export class AdminExpansionJobService {
  readonly activeJob = signal<AdminItemJob | null>(null);
  readonly dismissed = signal(false);

  private readonly api = inject(AdminApiService);
  private pollingSubscription: Subscription | null = null;

  trackJob(job: AdminItemJob): void {
    this.dismissed.set(false);
    this.activeJob.set(job);
    this.stopPolling();

    if (job.status !== AdminItemJob.StatusEnum.Running) {
      return;
    }

    this.pollingSubscription = timer(POLL_INTERVAL_MS, POLL_INTERVAL_MS)
      .pipe(
        exhaustMap(() => this.api.getAdminItemJob(job.id)),
        tap((updated) => {
          this.activeJob.set(updated);
          if (updated.status !== AdminItemJob.StatusEnum.Running) {
            this.stopPolling();
          }
        }),
        catchError(() => EMPTY),
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
