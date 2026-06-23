import { inject, Injectable, signal } from '@angular/core';
import { AdminApiService, AdminJob } from '@api/generated';
import { EMPTY, Subscription, catchError, exhaustMap, tap, timer } from 'rxjs';

const POLL_INTERVAL_MS = 2000;

export const ADMIN_JOB_DOMAIN_ITEM = 'item';
export const APPLY_EXPANSION_RANGES_OPERATION = 'apply-expansion-ranges';
export const FETCH_EXPANSION_RANGE_ITEMS_OPERATION = 'fetch-expansion-range-items';

@Injectable({
  providedIn: 'root',
})
export class AdminJobService {
  readonly activeJob = signal<AdminJob | null>(null);
  readonly dismissed = signal(false);

  private readonly api = inject(AdminApiService);
  private pollingSubscription: Subscription | null = null;

  trackJob(job: AdminJob): void {
    this.dismissed.set(false);
    this.activeJob.set(job);
    this.stopPolling();

    if (job.status !== AdminJob.StatusEnum.Running) {
      return;
    }

    this.pollingSubscription = timer(POLL_INTERVAL_MS, POLL_INTERVAL_MS)
      .pipe(
        exhaustMap(() => this.api.getAdminJob(job.id)),
        tap((updated) => {
          this.activeJob.set(updated);
          if (updated.status !== AdminJob.StatusEnum.Running) {
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
