import { isPlatformBrowser } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  PLATFORM_ID,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import {
  AdminItemCompareField,
  AdminRecipe1,
  AdminRecipeOutput,
  AdminRecipeOverrideRequest,
  AdminRecipeReagent,
} from '@api/generated';
import {
  PageFrameComponent,
  PaginationState,
  SearchInputComponent,
  SelectInputComponent,
  SelectInputOption,
  SlideOverPanelComponent,
  TableComponent,
} from '@ui';
import { firstValueFrom, fromEvent, map, startWith } from 'rxjs';
import { AdminRecipeService } from './admin-recipe.service';
import { createAdminRecipeColumns } from './admin-recipes-table.columns';
import {
  AdminRecipeFilterState,
  defaultAdminRecipeFilters,
  readAdminRecipeFilters,
  toAdminRecipeQueryParams,
} from './recipe-filters';

type PanelMode = 'edit' | 'compare';
type CompareRow = {
  readonly key: string;
  readonly value: AdminItemCompareField;
};

const DEFAULT_VIEWPORT_WIDTH = 1280;
const MOBILE_CARD_VIEW_MAX_WIDTH = 767;
const standaloneModel = { standalone: true };

@Component({
  selector: 'app-admin-recipes-page',
  imports: [
    FormsModule,
    PageFrameComponent,
    SearchInputComponent,
    SelectInputComponent,
    SlideOverPanelComponent,
    TableComponent,
  ],
  templateUrl: './recipes.page.html',
  styleUrl: './recipes.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecipesPage {
  private readonly service = inject(AdminRecipeService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly platformId = inject(PLATFORM_ID);

  protected readonly loading = this.service.loading.asReadonly();
  protected readonly mutationLoading = this.service.mutationLoading.asReadonly();
  protected readonly detailLoading = this.service.detailLoading.asReadonly();
  protected readonly compareLoading = this.service.compareLoading.asReadonly();
  protected readonly recipes = this.service.recipes.asReadonly();
  protected readonly page = this.service.page.asReadonly();
  protected readonly selectedRecipe = this.service.selectedRecipe.asReadonly();
  protected readonly compare = this.service.compare.asReadonly();
  protected readonly error = this.service.error.asReadonly();
  protected readonly detailError = this.service.detailError.asReadonly();
  protected readonly compareError = this.service.compareError.asReadonly();

  protected readonly filters = signal<AdminRecipeFilterState>(defaultAdminRecipeFilters());
  protected readonly panelMode = signal<PanelMode | null>(null);
  protected readonly formError = signal<string | null>(null);
  protected readonly overrideDraft = signal<AdminRecipeOverrideRequest>({});
  protected readonly outputsDraft = signal<AdminRecipeOutput[]>([]);
  protected readonly reagentsDraft = signal<AdminRecipeReagent[]>([]);
  protected readonly viewportWidth = signal(DEFAULT_VIEWPORT_WIDTH);
  protected readonly cardView = computed(() => this.viewportWidth() <= MOBILE_CARD_VIEW_MAX_WIDTH);
  protected readonly standaloneModel = standaloneModel;

  protected readonly hasOverrideOptions: readonly SelectInputOption[] = [
    { id: '', label: $localize`:@@admin.recipes.filter.anyOverride:All override states` },
    { id: 'true', label: $localize`:@@admin.recipes.filter.hasOverride:Has override` },
    { id: 'false', label: $localize`:@@admin.recipes.filter.noOverride:No override` },
  ];
  protected readonly pageSizeOptions: readonly SelectInputOption[] = [
    { id: '25', label: '25' },
    { id: '50', label: '50' },
    { id: '100', label: '100' },
  ];
  protected readonly rowId = (recipe: AdminRecipe1): string => String(recipe.id);
  protected readonly columns = signal(
    createAdminRecipeColumns({
      onEdit: (recipe) => void this.openEditPanel(recipe),
      onCompare: (recipe) => void this.openComparePanel(recipe),
      onDeleteOverride: (recipe) => void this.deleteOverride(recipe),
    }),
  );

  protected readonly paginationState = computed<PaginationState>(() => {
    const page = this.page();
    return {
      page: Math.max(0, page.page - 1),
      pageSize: page.pageSize,
      totalItems: page.totalItems,
      totalPages: Math.max(1, page.totalPages),
    };
  });

  protected readonly panelTitle = computed(() => {
    switch (this.panelMode()) {
      case 'compare':
        return $localize`:@@admin.recipes.panel.compare:Compare Blizzard API`;
      case 'edit':
        return $localize`:@@admin.recipes.panel.edit:Edit recipe override`;
      default:
        return '';
    }
  });

  protected readonly compareRows = computed<CompareRow[]>(() => {
    const fields = this.compare()?.fields ?? {};
    return Object.entries(fields)
      .map(([key, value]) => ({ key, value }))
      .sort((left, right) => left.key.localeCompare(right.key));
  });

  constructor() {
    if (isPlatformBrowser(this.platformId)) {
      fromEvent(window, 'resize')
        .pipe(
          startWith(null),
          map(() => window.innerWidth),
          takeUntilDestroyed(this.destroyRef),
        )
        .subscribe((width) => this.viewportWidth.set(width));
    }

    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((paramMap) => {
      this.filters.set(readAdminRecipeFilters(paramMap));
      void this.reload();
    });
  }

  protected updateFilter<K extends keyof AdminRecipeFilterState>(
    key: K,
    value: AdminRecipeFilterState[K],
  ): void {
    this.filters.update((current) => ({ ...current, [key]: value, page: 0 }));
  }

  protected updatePageSize(value: string): void {
    const pageSize = Number.parseInt(value, 10);
    this.syncFilters({
      ...this.filters(),
      page: 0,
      pageSize: Number.isFinite(pageSize) ? pageSize : 25,
    });
  }

  protected applyFilters(): void {
    this.syncFilters({ ...this.filters(), page: 0 });
  }

  protected resetFilters(): void {
    this.syncFilters(defaultAdminRecipeFilters());
  }

  protected onPageChange(page: number): void {
    this.syncFilters({ ...this.filters(), page });
  }

  protected async openEditPanel(recipe: AdminRecipe1): Promise<void> {
    this.formError.set(null);
    this.panelMode.set('edit');
    const loaded = await this.loadRecipe(recipe.id);
    if (!loaded) return;
    this.overrideDraft.set({
      craftedItemId: loaded.override?.craftedItemId ?? null,
      craftedQuantity: loaded.override?.craftedQuantity ?? null,
      rank: loaded.override?.rank ?? null,
      requiredSkillLevel: loaded.override?.requiredSkillLevel ?? null,
      overrideNote: loaded.override?.overrideNote ?? null,
    });
    this.outputsDraft.set([...(loaded.override?.outputs ?? loaded.effective.outputs ?? [])]);
    this.reagentsDraft.set([...(loaded.override?.reagents ?? loaded.effective.reagents ?? [])]);
  }

  protected async openComparePanel(recipe: AdminRecipe1): Promise<void> {
    this.formError.set(null);
    this.panelMode.set('compare');
    this.service.clearSelection();
    await firstValueFrom(this.service.compareWithApi(recipe.id)).catch(() => undefined);
  }

  protected closePanel(): void {
    this.panelMode.set(null);
    this.formError.set(null);
    this.service.clearSelection();
    this.overrideDraft.set({});
    this.outputsDraft.set([]);
    this.reagentsDraft.set([]);
  }

  protected updateDraft<K extends keyof AdminRecipeOverrideRequest>(
    key: K,
    value: AdminRecipeOverrideRequest[K],
  ): void {
    this.overrideDraft.update((current) => ({ ...current, [key]: value }));
  }

  protected addOutput(): void {
    this.outputsDraft.update((outputs) => [
      ...outputs,
      { craftedItemId: 0, craftedQuantity: 1, sortOrder: outputs.length },
    ]);
  }

  protected updateOutput(index: number, patch: Partial<AdminRecipeOutput>): void {
    this.outputsDraft.update((outputs) =>
      outputs.map((output, outputIndex) =>
        outputIndex === index ? { ...output, ...patch } : output,
      ),
    );
  }

  protected removeOutput(index: number): void {
    this.outputsDraft.update((outputs) =>
      outputs.filter((_, outputIndex) => outputIndex !== index),
    );
  }

  protected addReagent(): void {
    this.reagentsDraft.update((reagents) => [
      ...reagents,
      { itemId: 0, quantity: 1, sortOrder: reagents.length, ranks: [] },
    ]);
  }

  protected updateReagent(index: number, patch: Partial<AdminRecipeReagent>): void {
    this.reagentsDraft.update((reagents) =>
      reagents.map((reagent, reagentIndex) =>
        reagentIndex === index ? { ...reagent, ...patch } : reagent,
      ),
    );
  }

  protected removeReagent(index: number): void {
    this.reagentsDraft.update((reagents) =>
      reagents.filter((_, reagentIndex) => reagentIndex !== index),
    );
  }

  protected submitOverride(): void {
    const recipe = this.selectedRecipe();
    if (!recipe) return;
    const request: AdminRecipeOverrideRequest = {
      ...this.overrideDraft(),
      outputs: this.outputsDraft().map((output, index) => ({
        ...output,
        sortOrder: output.sortOrder ?? index,
      })),
      reagents: this.reagentsDraft().map((reagent, index) => ({
        ...reagent,
        sortOrder: reagent.sortOrder ?? index,
      })),
    };
    firstValueFrom(
      this.service.upsertOverride(recipe.id, normalizeRequest(request), this.filters()),
    )
      .then(() => this.closePanel())
      .catch((error: unknown) => {
        this.formError.set(
          error instanceof Error
            ? error.message
            : $localize`:@@admin.recipes.formError:Unable to save recipe override.`,
        );
      });
  }

  protected async deleteOverride(recipe: AdminRecipe1): Promise<void> {
    if (!recipe.hasOverride) return;
    await firstValueFrom(this.service.deleteOverride(recipe.id, this.filters())).catch(
      () => undefined,
    );
  }

  protected pageSizeValue(): string {
    return String(this.filters().pageSize);
  }

  protected formatCompare(value: object | null | undefined): string {
    if (value === undefined || value === null) return '—';
    if (typeof value === 'string') return value;
    if (typeof value === 'number' || typeof value === 'boolean') return String(value);
    return JSON.stringify(value);
  }

  private syncFilters(filters: AdminRecipeFilterState): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: toAdminRecipeQueryParams(filters),
      queryParamsHandling: '',
      replaceUrl: true,
    });
  }

  private reload(): Promise<readonly AdminRecipe1[] | undefined> {
    return firstValueFrom(this.service.search(this.filters())).catch(() => undefined);
  }

  private loadRecipe(id: number): Promise<AdminRecipe1 | undefined> {
    return firstValueFrom(this.service.loadRecipe(id)).catch(() => undefined);
  }
}

function normalizeRequest(request: AdminRecipeOverrideRequest): AdminRecipeOverrideRequest {
  return {
    craftedItemId: toNullableInt(request.craftedItemId),
    craftedQuantity: toNullableInt(request.craftedQuantity),
    rank: toNullableInt(request.rank),
    requiredSkillLevel: toNullableInt(request.requiredSkillLevel),
    overrideNote: request.overrideNote?.trim() || null,
    outputs: request.outputs?.filter(
      (output) => output.craftedItemId > 0 && output.craftedQuantity > 0,
    ),
    reagents: request.reagents?.filter((reagent) => reagent.itemId > 0 && reagent.quantity > 0),
  };
}

function toNullableInt(value: number | null | undefined): number | null {
  if (value === null || value === undefined || !Number.isFinite(Number(value))) return null;
  return Number(value);
}
