import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import {
  AdminApiService,
  AdminExpansion,
  AdminExpansionItemRange,
  AdminExpansionItemRangeRequest,
  AdminExpansionRequest,
  AdminJob,
} from '@api/generated';
import { LocaleService } from '@core/services/locale.service';
import { ToastService } from '@core/services/toast.service';
import { AdminJobService } from '@features/admin/shared/admin-job.service';
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
  private readonly jobs = inject(AdminJobService);
  private readonly toast = inject(ToastService);
  private readonly localeService = inject(LocaleService);

  load(): Observable<[AdminExpansion[], AdminExpansionItemRange[]]> {
    this.loading.set(true);
    this.error.set(null);
    const locale = this.localeService.apiLocaleOverride();

    return forkJoin([this.api.listExpansions(locale), this.api.listExpansionRanges(locale)]).pipe(
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

  createExpansion(request: AdminExpansionRequest): Observable<AdminExpansion> {
    return this.mutateExpansionCatalog(() => this.api.createExpansion(request));
  }

  updateExpansion(id: number, request: AdminExpansionRequest): Observable<AdminExpansion> {
    return this.mutateExpansionCatalog(() => this.api.updateExpansion(id, request));
  }

  deleteExpansion(id: number): Observable<void> {
    return this.mutateExpansionCatalog(() => this.api.deleteExpansion(id), {
      refreshRanges: false,
      fallbackMessage: 'Unable to delete expansion.',
    });
  }

  createRange(request: AdminExpansionItemRangeRequest): Observable<AdminExpansionItemRange> {
    return this.mutateRanges(() => this.api.createExpansionRange(request));
  }

  updateRange(
    id: number,
    request: AdminExpansionItemRangeRequest,
  ): Observable<AdminExpansionItemRange> {
    return this.mutateRanges(() => this.api.updateExpansionRange(id, request));
  }

  deleteRange(id: number): Observable<void> {
    return this.mutateRanges(() => this.api.deleteExpansionRange(id));
  }

  startApplyJob(): Observable<AdminJob> {
    return this.startJob(() => this.api.applyExpansionRanges());
  }

  startFetchMissingJob(): Observable<AdminJob> {
    return this.startJob(() => this.api.fetchMissingExpansionRangeItems());
  }

  private mutateExpansionCatalog<T>(
    request: () => Observable<T>,
    options: { refreshRanges?: boolean; fallbackMessage?: string } = {},
  ): Observable<T> {
    const refreshRanges = options.refreshRanges ?? true;
    const fallbackMessage = options.fallbackMessage ?? 'Expansion request failed.';
    this.mutationLoading.set(true);
    const locale = this.localeService.apiLocaleOverride();

    return request().pipe(
      switchMap((result) => {
        const expansions$ = this.api.listExpansions(locale);
        if (!refreshRanges) {
          return expansions$.pipe(
            tap((expansions) => this.expansions.set(expansions)),
            map(() => result),
          );
        }
        return forkJoin([expansions$, this.api.listExpansionRanges(locale)]).pipe(
          tap(([expansions, ranges]) => {
            this.expansions.set(expansions);
            this.ranges.set(ranges);
          }),
          map(() => result),
        );
      }),
      catchError((error: unknown) => {
        this.toast.error(readHttpErrorMessage(error, fallbackMessage));
        return throwError(() => error);
      }),
      finalize(() => this.mutationLoading.set(false)),
    );
  }

  private mutateRanges<T>(request: () => Observable<T>): Observable<T> {
    this.mutationLoading.set(true);
    const locale = this.localeService.apiLocaleOverride();

    return request().pipe(
      switchMap((result) =>
        this.api.listExpansionRanges(locale).pipe(
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

  private startJob(request: () => Observable<AdminJob>): Observable<AdminJob> {
    this.mutationLoading.set(true);
    return request().pipe(
      tap({
        next: (job) => this.jobs.trackJob(job),
        error: (error: unknown) => {
          if (error instanceof HttpErrorResponse && error.status === 409) {
            this.toast.error('A job of this type is already running.');
            return;
          }
          this.toast.error(readHttpErrorMessage(error, 'Unable to start admin job.'));
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
