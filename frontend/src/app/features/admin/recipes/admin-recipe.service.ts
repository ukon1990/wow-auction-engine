import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import {
  AdminApiService,
  AdminRecipe1,
  AdminRecipeCompareResponse,
  AdminRecipeOverrideRequest,
  PageMetadata,
} from '@api/generated';
import { LocaleService } from '@core/services/locale.service';
import { ToastService } from '@core/services/toast.service';
import { catchError, finalize, map, Observable, switchMap, tap, throwError } from 'rxjs';
import { AdminRecipeFilterState, toAdminRecipeSearchParams } from './recipe-filters';

const EMPTY_PAGE: PageMetadata = {
  page: 1,
  pageSize: 25,
  totalItems: 0,
  totalPages: 1,
};

@Injectable({ providedIn: 'root' })
export class AdminRecipeService {
  readonly loading = signal(false);
  readonly mutationLoading = signal(false);
  readonly detailLoading = signal(false);
  readonly compareLoading = signal(false);
  readonly recipes = signal<readonly AdminRecipe1[]>([]);
  readonly page = signal<PageMetadata>(EMPTY_PAGE);
  readonly selectedRecipe = signal<AdminRecipe1 | null>(null);
  readonly compare = signal<AdminRecipeCompareResponse | null>(null);
  readonly error = signal<string | null>(null);
  readonly detailError = signal<string | null>(null);
  readonly compareError = signal<string | null>(null);

  private readonly api = inject(AdminApiService);
  private readonly localeService = inject(LocaleService);
  private readonly toast = inject(ToastService);

  search(filters: AdminRecipeFilterState): Observable<readonly AdminRecipe1[]> {
    this.loading.set(true);
    this.error.set(null);
    const params = toAdminRecipeSearchParams(filters);
    const locale = this.localeService.apiLocaleOverride();

    return this.api
      .searchAdminRecipes(
        params.query,
        locale,
        undefined,
        params.hasOverride,
        params.itemClassId,
        params.itemSubclassId,
        params.expansionId,
        params.associatedItemId,
        params.associationType,
        params.page,
        params.pageSize,
      )
      .pipe(
        tap({
          next: (result) => {
            this.recipes.set(result.recipes);
            this.page.set(result.page);
          },
          error: (error: unknown) => {
            const message = readHttpErrorMessage(
              error,
              $localize`:@@admin.recipes.loadError:Unable to load recipes.`,
            );
            this.error.set(message);
            this.toast.error(message);
          },
        }),
        map((result) => result.recipes),
        finalize(() => this.loading.set(false)),
      );
  }

  loadRecipe(id: number): Observable<AdminRecipe1> {
    this.detailLoading.set(true);
    this.detailError.set(null);
    const locale = this.localeService.apiLocaleOverride();

    return this.api.getAdminRecipe(id, locale, true, true).pipe(
      tap({
        next: (recipe) => this.selectedRecipe.set(recipe),
        error: (error: unknown) => {
          const message = readHttpErrorMessage(
            error,
            $localize`:@@admin.recipes.detailError:Unable to load recipe details.`,
          );
          this.detailError.set(message);
          this.toast.error(message);
        },
      }),
      finalize(() => this.detailLoading.set(false)),
    );
  }

  upsertOverride(
    id: number,
    request: AdminRecipeOverrideRequest,
    filters: AdminRecipeFilterState,
  ): Observable<AdminRecipe1> {
    return this.mutate(() => this.api.upsertAdminRecipeOverride(id, request), filters).pipe(
      switchMap(() => this.loadRecipe(id)),
    );
  }

  deleteOverride(id: number, filters: AdminRecipeFilterState): Observable<void> {
    return this.mutate(() => this.api.deleteAdminRecipeOverride(id), filters).pipe(
      switchMap(() => this.loadRecipe(id)),
      map(() => undefined),
    );
  }

  compareWithApi(id: number): Observable<AdminRecipeCompareResponse> {
    this.compareLoading.set(true);
    this.compareError.set(null);
    this.compare.set(null);

    return this.api.compareAdminRecipeWithApi(id).pipe(
      tap({
        next: (result) => this.compare.set(result),
        error: (error: unknown) => {
          const message = readHttpErrorMessage(
            error,
            $localize`:@@admin.recipes.compareError:Unable to compare recipe with Blizzard API.`,
          );
          this.compareError.set(message);
          this.toast.error(message);
        },
      }),
      finalize(() => this.compareLoading.set(false)),
    );
  }

  clearSelection(): void {
    this.selectedRecipe.set(null);
    this.detailError.set(null);
    this.compare.set(null);
    this.compareError.set(null);
  }

  private mutate<T>(request: () => Observable<T>, filters: AdminRecipeFilterState): Observable<T> {
    this.mutationLoading.set(true);
    return request().pipe(
      switchMap((result) => this.search(filters).pipe(map(() => result))),
      catchError((error: unknown) => {
        this.toast.error(
          readHttpErrorMessage(
            error,
            $localize`:@@admin.recipes.mutationError:Recipe request failed.`,
          ),
        );
        return throwError(() => error);
      }),
      finalize(() => this.mutationLoading.set(false)),
    );
  }
}

function readHttpErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof HttpErrorResponse)) return fallback;
  const body = error.error;
  if (typeof body === 'string' && body.trim().length > 0) return body;
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
