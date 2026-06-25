import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import {
  AdminApiService,
  AdminItem,
  AdminItemApiCompareResponse,
  AdminItemCreateRequest,
  AdminItemOverrideRequest,
  AdminItemPage,
} from '@api/generated';
import { LocaleService } from '@core/services/locale.service';
import { ToastService } from '@core/services/toast.service';
import { ItemSearchParams } from '@features/admin/items/item-filters';
import { catchError, finalize, Observable, tap, throwError } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class AdminItemService {
  readonly loading = signal(false);
  readonly mutationLoading = signal(false);
  readonly page = signal<AdminItemPage | null>(null);
  readonly error = signal<string | null>(null);

  private readonly api = inject(AdminApiService);
  private readonly toast = inject(ToastService);
  private readonly localeService = inject(LocaleService);

  load(params: ItemSearchParams): Observable<AdminItemPage> {
    this.loading.set(true);
    this.error.set(null);
    const locale = this.localeService.apiLocaleOverride();

    return this.api
      .listAdminItems(
        params.page,
        params.pageSize,
        params.itemId,
        params.name,
        params.qualityId,
        params.classId,
        params.subclassId,
        params.expansionId,
        params.hasOverride,
        params.sort,
        locale,
      )
      .pipe(
        tap({
          next: (result) => this.page.set(result),
          error: (error: unknown) => {
            const message = readHttpErrorMessage(error, 'Unable to load items.');
            this.error.set(message);
            this.toast.error(message);
          },
        }),
        finalize(() => this.loading.set(false)),
      );
  }

  getItem(id: number, includeBase = false, includeOverride = false): Observable<AdminItem> {
    const locale = this.localeService.apiLocaleOverride();
    return this.api.getAdminItem(id, includeBase, includeOverride, locale);
  }

  createItem(request: AdminItemCreateRequest): Observable<AdminItem> {
    return this.mutate(() => this.api.createAdminItem(request), 'Unable to create item.');
  }

  upsertOverride(id: number, request: AdminItemOverrideRequest): Observable<AdminItem> {
    return this.mutate(
      () => this.api.upsertAdminItemOverride(id, request),
      'Unable to save item override.',
    );
  }

  deleteOverride(id: number): Observable<void> {
    return this.mutate(() => this.api.deleteAdminItemOverride(id), 'Unable to delete override.');
  }

  compareWithApi(id: number): Observable<AdminItemApiCompareResponse> {
    const locale = this.localeService.apiLocaleOverride();
    return this.api.compareAdminItemApi(id, locale).pipe(
      catchError((error: unknown) => {
        this.toast.error(readHttpErrorMessage(error, 'Unable to compare item with Blizzard API.'));
        return throwError(() => error);
      }),
    );
  }

  private mutate<T>(request: () => Observable<T>, fallbackMessage: string): Observable<T> {
    this.mutationLoading.set(true);
    return request().pipe(
      catchError((error: unknown) => {
        this.toast.error(readHttpErrorMessage(error, fallbackMessage));
        return throwError(() => error);
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
    'detail' in body &&
    typeof body.detail === 'string' &&
    body.detail.trim().length > 0
  ) {
    return body.detail;
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
