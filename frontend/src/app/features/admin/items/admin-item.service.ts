import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import {
  AdminApiService,
  AdminItem1,
  AdminItemCompareResponse,
  AdminItemCreateRequest,
  AdminItemOverrideRequest,
  AdminRecipeAssociationRequest,
  AdminRecipeSearchResult,
  PageMetadata,
} from '@api/generated';
import { LocaleService } from '@core/services/locale.service';
import { ToastService } from '@core/services/toast.service';
import { AdminItemFilterState, toAdminItemSearchParams } from './item-filters';
import { catchError, finalize, map, Observable, switchMap, tap, throwError } from 'rxjs';

const EMPTY_PAGE: PageMetadata = {
  page: 1,
  pageSize: 25,
  totalItems: 0,
  totalPages: 1,
};

@Injectable({
  providedIn: 'root',
})
export class AdminItemService {
  readonly loading = signal(false);
  readonly mutationLoading = signal(false);
  readonly detailLoading = signal(false);
  readonly compareLoading = signal(false);
  readonly items = signal<readonly AdminItem1[]>([]);
  readonly page = signal<PageMetadata>(EMPTY_PAGE);
  readonly selectedItem = signal<AdminItem1 | null>(null);
  readonly compare = signal<AdminItemCompareResponse | null>(null);
  readonly error = signal<string | null>(null);
  readonly detailError = signal<string | null>(null);
  readonly compareError = signal<string | null>(null);

  private readonly api = inject(AdminApiService);
  private readonly localeService = inject(LocaleService);
  private readonly toast = inject(ToastService);

  search(filters: AdminItemFilterState): Observable<readonly AdminItem1[]> {
    this.loading.set(true);
    this.error.set(null);
    const params = toAdminItemSearchParams(filters);
    const locale = this.localeService.apiLocaleOverride();

    return this.api
      .searchAdminItems(
        params.query,
        locale,
        undefined,
        params.hasOverride,
        params.itemClassId,
        params.itemSubclassId,
        params.expansionId,
        params.hasRecipe,
        params.page,
        params.pageSize,
      )
      .pipe(
        tap({
          next: (result) => {
            this.items.set(result.items);
            this.page.set(result.page);
          },
          error: (error: unknown) => {
            const message = readHttpErrorMessage(
              error,
              $localize`:@@admin.items.loadError:Unable to load items.`,
            );
            this.error.set(message);
            this.toast.error(message);
          },
        }),
        map((result) => result.items),
        finalize(() => this.loading.set(false)),
      );
  }

  loadItem(id: number): Observable<AdminItem1> {
    this.detailLoading.set(true);
    this.detailError.set(null);
    const locale = this.localeService.apiLocaleOverride();

    return this.api.getAdminItem(id, locale, true, true).pipe(
      tap({
        next: (item) => this.selectedItem.set(item),
        error: (error: unknown) => {
          const message = readHttpErrorMessage(
            error,
            $localize`:@@admin.items.detailError:Unable to load item details.`,
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
    request: AdminItemOverrideRequest,
    filters: AdminItemFilterState,
  ): Observable<AdminItem1> {
    return this.mutate(() => this.api.upsertAdminItemOverride(id, request), filters).pipe(
      switchMap(() => this.loadItem(id)),
    );
  }

  createItem(
    request: AdminItemCreateRequest,
    filters: AdminItemFilterState,
  ): Observable<AdminItem1> {
    return this.mutate(() => this.api.createAdminItem(request), filters).pipe(
      tap((item) => this.selectedItem.set(item)),
    );
  }

  deleteOverride(id: number, filters: AdminItemFilterState): Observable<void> {
    return this.mutate(() => this.api.deleteAdminItemOverride(id), filters).pipe(
      switchMap(() => this.loadItem(id)),
      map(() => undefined),
    );
  }

  searchRecipes(query: string, limit = 20): Observable<readonly AdminRecipeSearchResult[]> {
    const locale = this.localeService.apiLocaleOverride();
    return this.api
      .searchAdminRecipes(
        query,
        locale,
        undefined,
        undefined,
        undefined,
        undefined,
        undefined,
        undefined,
        undefined,
        1,
        limit,
      )
      .pipe(
        map((page) =>
          page.recipes.map((recipe) => ({
            recipeId: recipe.id,
            name: recipe.effective.name ?? String(recipe.id),
            professionName: recipe.effective.professionName ?? '',
            skillTierName: recipe.effective.skillTierName ?? '',
            professionCategoryName: recipe.effective.professionCategoryName ?? '',
            craftedItemId: recipe.effective.craftedItemId,
            craftedItemName: recipe.effective.craftedItemName,
            craftedQuantity: recipe.effective.craftedQuantity,
          })),
        ),
        catchError((error: unknown) => {
          this.toast.error(
            readHttpErrorMessage(
              error,
              $localize`:@@admin.items.recipeSearchError:Unable to search recipes.`,
            ),
          );
          return throwError(() => error);
        }),
      );
  }

  upsertRecipeAssociation(
    id: number,
    recipeId: number,
    request: AdminRecipeAssociationRequest,
    filters: AdminItemFilterState,
  ): Observable<AdminItem1> {
    return this.mutate(
      () => this.api.upsertAdminItemRecipeAssociation(id, recipeId, request),
      filters,
    ).pipe(switchMap(() => this.loadItem(id)));
  }

  compareWithApi(id: number): Observable<AdminItemCompareResponse> {
    this.compareLoading.set(true);
    this.compareError.set(null);
    this.compare.set(null);

    return this.api.compareAdminItemWithApi(id).pipe(
      tap({
        next: (result) => this.compare.set(result),
        error: (error: unknown) => {
          const message = readHttpErrorMessage(
            error,
            $localize`:@@admin.items.compareError:Unable to compare item with Blizzard API.`,
          );
          this.compareError.set(message);
          this.toast.error(message);
        },
      }),
      finalize(() => this.compareLoading.set(false)),
    );
  }

  clearSelection(): void {
    this.selectedItem.set(null);
    this.detailError.set(null);
    this.compare.set(null);
    this.compareError.set(null);
  }

  private mutate<T>(request: () => Observable<T>, filters: AdminItemFilterState): Observable<T> {
    this.mutationLoading.set(true);
    return request().pipe(
      switchMap((result) => this.search(filters).pipe(map(() => result))),
      catchError((error: unknown) => {
        this.toast.error(
          readHttpErrorMessage(error, $localize`:@@admin.items.mutationError:Item request failed.`),
        );
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
    'message' in body &&
    typeof body.message === 'string' &&
    body.message.trim().length > 0
  ) {
    return body.message;
  }

  return fallback;
}
