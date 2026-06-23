import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import {
  AdminApiService,
  AdminExpansion,
  AdminExpansionItemRange,
  AdminExpansionItemRangeRequest,
  AdminItemJob,
} from '@api/generated';
import { ToastService } from '@core/services/toast.service';
import { AdminExpansionJobService } from '@features/admin/expansions/admin-expansion-job.service';
import { catchError, finalize, forkJoin, map, Observable, switchMap, tap, throwError } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class AdminExpansionService {
  readonly loading = signal(false);
  readonly mutationLoading = signal(false);
  readonly expansions = signal<AdminExpansion[]>([]);
  readonly ranges = signal<AdminExpansionItemRange[]>([]);
  readonly error = signal<string | null>(null);

  private readonly api = inject(AdminApiService);
  private readonly jobs = inject(AdminExpansionJobService);
  private readonly toast = inject(ToastService);

  load(): Observable<[AdminExpansion[], AdminExpansionItemRange[]]> {
    this.loading.set(true);
    this.error.set(null);

    return forkJoin([this.api.listExpansions(), this.api.listExpansionRanges()]).pipe(
      tap({
        next: ([expansions, ranges]) => {
          this.expansions.set(expansions);
          this.ranges.set(ranges);
        },
        error: (error: unknown) => {
          const message = readHttpErrorMessage(error, 'Unable to load expansion data.');
          this.error.set(message);
          this.toast.error(message);
        },
      }),
      finalize(() => this.loading.set(false)),
    );
  }

  createRange(request: AdminExpansionItemRangeRequest): Observable<AdminExpansionItemRange> {
    return this.mutate(() => this.api.createExpansionRange(request));
  }

  updateRange(
    id: number,
    request: AdminExpansionItemRangeRequest,
  ): Observable<AdminExpansionItemRange> {
    return this.mutate(() => this.api.updateExpansionRange(id, request));
  }

  deleteRange(id: number): Observable<void> {
    return this.mutate(() => this.api.deleteExpansionRange(id));
  }

  startApplyJob(): Observable<AdminItemJob> {
    return this.startJob(() => this.api.applyExpansionRanges());
  }

  startFetchMissingJob(): Observable<AdminItemJob> {
    return this.startJob(() => this.api.fetchMissingExpansionRangeItems());
  }

  private mutate<T>(request: () => Observable<T>): Observable<T> {
    this.mutationLoading.set(true);
    return request().pipe(
      switchMap((result) =>
        this.api.listExpansionRanges().pipe(
          tap((ranges) => this.ranges.set(ranges)),
          map(() => result),
        ),
      ),
      catchError((error: unknown) => {
        this.toast.error(readHttpErrorMessage(error, 'Expansion range request failed.'));
        return throwError(() => error);
      }),
      finalize(() => this.mutationLoading.set(false)),
    );
  }

  private startJob(request: () => Observable<AdminItemJob>): Observable<AdminItemJob> {
    this.mutationLoading.set(true);
    return request().pipe(
      tap({
        next: (job) => this.jobs.trackJob(job),
        error: (error: unknown) => {
          if (error instanceof HttpErrorResponse && error.status === 409) {
            this.toast.error('A job of this type is already running.');
            return;
          }
          this.toast.error(readHttpErrorMessage(error, 'Unable to start admin item job.'));
        },
      }),
      finalize(() => this.mutationLoading.set(false)),
    );
  }
}

function readHttpErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof HttpErrorResponse)) {
    return fallback;
  }

  const body = error.error;
  if (typeof body === 'string' && body.trim().length > 0) {
    return body;
  }
  if (
    body &&
    typeof body === 'object' &&
    'message' in body &&
    typeof body.message === 'string' &&
    body.message.trim().length > 0
  ) {
    return body.message;
  }

  return fallback;
}
