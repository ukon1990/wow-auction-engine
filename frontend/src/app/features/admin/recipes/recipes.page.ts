import { isPlatformBrowser } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  PLATFORM_ID,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AdminJob, AdminRecipe1, AdminRecipeOverrideRequest } from '@api/generated';
import { AdminModalComponent } from '@features/admin/shared/admin-modal.component';
import { AdminItemTypeaheadComponent } from '@features/admin/shared/admin-item-typeahead.component';
import { AdminExpansionService } from '@features/admin/expansions/admin-expansion.service';
import {
  ADMIN_JOB_DOMAIN_PROFESSION,
  AdminJobService,
  SYNC_PROFESSIONS_OPERATION,
} from '@features/admin/shared/admin-job.service';
import {
  ITEM_CLASS_OPTIONS,
  ITEM_SUBCLASS_OPTIONS,
} from '@features/admin/items/admin-item-override-form.component';
import {
  PageFrameComponent,
  PaginationState,
  SearchInputComponent,
  SelectInputComponent,
  SelectInputOption,
  TableComponent,
} from '@ui';
import { firstValueFrom, fromEvent, map, startWith } from 'rxjs';
import { AdminRecipeComparePanelComponent } from './admin-recipe-compare-panel.component';
import { AdminRecipeOverrideFormComponent } from './admin-recipe-override-form.component';
import { AdminRecipeService } from './admin-recipe.service';
import { createAdminRecipeColumns } from './admin-recipes-table.columns';
import { professionSyncJobSummary, professionSyncJobTitle } from './profession-sync-job-summary';
import {
  AdminRecipeFilterState,
  defaultAdminRecipeFilters,
  readAdminRecipeFilters,
  toAdminRecipeQueryParams,
} from './recipe-filters';

type PanelMode = 'edit' | 'compare';

const DEFAULT_VIEWPORT_WIDTH = 1280;
const MOBILE_CARD_VIEW_MAX_WIDTH = 767;
const standaloneModel = { standalone: true };

@Component({
  selector: 'app-admin-recipes-page',
  imports: [
    FormsModule,
    AdminModalComponent,
    AdminItemTypeaheadComponent,
    AdminRecipeComparePanelComponent,
    AdminRecipeOverrideFormComponent,
    PageFrameComponent,
    SearchInputComponent,
    SelectInputComponent,
    TableComponent,
  ],
  templateUrl: './recipes.page.html',
  styleUrl: './recipes.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecipesPage {
  private readonly service = inject(AdminRecipeService);
  private readonly expansionService = inject(AdminExpansionService);
  private readonly jobService = inject(AdminJobService);
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
  protected readonly expansions = this.expansionService.expansions.asReadonly();
  protected readonly activeJob = this.jobService.activeJob.asReadonly();
  protected readonly jobDismissed = this.jobService.dismissed.asReadonly();
  protected readonly pollingError = this.jobService.pollingError.asReadonly();

  protected readonly filters = signal<AdminRecipeFilterState>(defaultAdminRecipeFilters());
  protected readonly panelMode = signal<PanelMode | null>(null);
  protected readonly formError = signal<string | null>(null);
  protected readonly syncStarting = signal(false);
  protected readonly syncError = signal<string | null>(null);
  protected readonly viewportWidth = signal(DEFAULT_VIEWPORT_WIDTH);
  protected readonly cardView = computed(() => this.viewportWidth() <= MOBILE_CARD_VIEW_MAX_WIDTH);
  protected readonly standaloneModel = standaloneModel;

  protected readonly pageTitle = $localize`:@@route.admin.recipes:Recipes`;
  protected readonly pageEyebrow = $localize`:@@route.admin.title:Admin`;
  protected readonly pageHeading = $localize`:@@admin.recipes.page.heading:Recipe overrides`;
  protected readonly pageDescription = $localize`:@@admin.recipes.page.description:Search effective recipe rows and manage admin overrides that survive Blizzard sync.`;
  protected readonly filterNameLabel = $localize`:@@admin.recipes.filter.name:Name`;
  protected readonly filterNamePlaceholder = $localize`:@@admin.recipes.filter.namePlaceholder:Search localized names`;
  protected readonly filterRecipeIdLabel = $localize`:@@admin.recipes.table.id:Recipe ID`;
  protected readonly filterRecipeIdPlaceholder = $localize`:@@admin.recipes.filter.recipeIdPlaceholder:Exact or partial recipe ID`;
  protected readonly filterAssociatedItemLabel = $localize`:@@admin.recipes.filter.associatedItem:Associated item`;
  protected readonly filterAssociatedItemPlaceholder = $localize`:@@admin.recipes.filter.associatedItemPlaceholder:Search crafted items or reagents`;
  protected readonly filterClassLabel = $localize`:@@admin.recipes.filter.class:Class`;
  protected readonly filterSubclassLabel = $localize`:@@admin.recipes.filter.subclass:Subclass`;
  protected readonly filterExpansionLabel = $localize`:@@admin.recipes.filter.expansion:Expansion`;
  protected readonly filterAssociationTypeLabel = $localize`:@@admin.recipes.filter.associationType:Item association`;
  protected readonly filterOverrideStateLabel = $localize`:@@admin.recipes.filter.overrideState:Override state`;
  protected readonly filterPageSizeLabel = $localize`:@@admin.recipes.filter.pageSize:Page size`;
  protected readonly applyFiltersLabel = $localize`:@@admin.recipes.filter.apply:Apply`;
  protected readonly resetFiltersLabel = $localize`:@@admin.recipes.filter.reset:Reset`;
  protected readonly emptyTableMessage = $localize`:@@admin.recipes.table.empty:No recipes match the current filters.`;
  protected readonly tableSectionAriaLabel = $localize`:@@admin.recipes.table.sectionAria:Admin recipes`;
  protected readonly paginationRowLabel = $localize`:@@admin.recipes.table.paginationLabel:recipes`;
  protected readonly loadingDetailsLabel = $localize`:@@admin.recipes.form.loading:Loading recipe details...`;
  protected readonly professionSyncHeading = $localize`:@@admin.recipes.professionSync.heading:Profession sync`;
  protected readonly professionSyncDescription = $localize`:@@admin.recipes.professionSync.description:Start an asynchronous sync of professions, skill tiers, recipes, and reagents.`;
  protected readonly startProfessionSyncLabel = $localize`:@@admin.recipes.professionSync.start:Sync professions and recipes`;
  protected readonly dismissProfessionSyncLabel = $localize`:@@admin.recipes.professionSync.dismiss:Dismiss`;

  protected readonly hasOverrideOptions: readonly SelectInputOption[] = [
    { id: '', label: $localize`:@@admin.recipes.filter.anyOverride:All override states` },
    { id: 'true', label: $localize`:@@admin.recipes.filter.hasOverride:Has override` },
    { id: 'false', label: $localize`:@@admin.recipes.filter.noOverride:No override` },
  ];
  protected readonly associationTypeOptions: readonly SelectInputOption[] = [
    { id: '', label: $localize`:@@admin.recipes.filter.anyAssociation:Crafted or reagent` },
    { id: 'crafted', label: $localize`:@@admin.recipes.filter.craftedAssociation:Crafted output` },
    { id: 'reagent', label: $localize`:@@admin.recipes.filter.reagentAssociation:Reagent` },
  ];
  protected readonly itemClassOptions: readonly SelectInputOption[] = [
    { id: '', label: $localize`:@@admin.items.filter.anyClass:All classes` },
    ...ITEM_CLASS_OPTIONS,
  ];
  protected readonly itemSubclassOptions = computed<readonly SelectInputOption[]>(() => [
    { id: '', label: $localize`:@@admin.items.filter.anySubclass:All subclasses` },
    ...(ITEM_SUBCLASS_OPTIONS[this.filters().classId] ?? []),
  ]);
  protected readonly expansionOptions = computed<readonly SelectInputOption[]>(() => [
    { id: '', label: $localize`:@@admin.items.filter.anyExpansion:All expansions` },
    ...this.expansions().map((expansion) => ({ id: String(expansion.id), label: expansion.name })),
  ]);
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

  protected readonly professionJob = computed(() => {
    const job = this.activeJob();
    return job?.domain === ADMIN_JOB_DOMAIN_PROFESSION &&
      job.operation === SYNC_PROFESSIONS_OPERATION
      ? job
      : null;
  });
  protected readonly showProfessionJob = computed(
    () => this.professionJob() !== null && !this.jobDismissed(),
  );
  protected readonly professionJobTitle = computed(() => {
    const job = this.professionJob();
    return job ? professionSyncJobTitle(job) : '';
  });
  protected readonly professionJobSummary = computed(() => {
    const job = this.professionJob();
    return job ? professionSyncJobSummary(job) : null;
  });
  protected readonly professionJobRunning = computed(
    () => this.professionJob()?.status === AdminJob.StatusEnum.Running,
  );
  protected readonly professionJobFailed = computed(
    () => this.professionJob()?.status === AdminJob.StatusEnum.Failed,
  );
  protected readonly professionSyncBusy = computed(
    () => this.syncStarting() || this.professionJobRunning(),
  );

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

  constructor() {
    let refreshedJobId: number | null = null;
    effect(() => {
      const job = this.professionJob();
      if (job?.status === AdminJob.StatusEnum.Completed && refreshedJobId !== job.id) {
        refreshedJobId = job.id;
        void this.reload();
      }
    });

    this.destroyRef.onDestroy(() => this.jobService.stopPolling());

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
    void firstValueFrom(this.expansionService.load()).catch(() => undefined);
    void this.rehydrateProfessionSync();
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

  protected updateClassFilter(value: string): void {
    this.filters.update((current) => ({ ...current, classId: value, subclassId: '', page: 0 }));
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
    await this.loadRecipe(recipe.id);
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
  }

  protected submitOverride(request: AdminRecipeOverrideRequest): void {
    const recipe = this.selectedRecipe();
    if (!recipe) return;

    firstValueFrom(this.service.upsertOverride(recipe.id, request, this.filters()))
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
    const confirmed = window.confirm(
      $localize`:@@admin.recipes.deleteConfirm:Delete this recipe override? Base recipe data will be inherited again.`,
    );
    if (!confirmed) return;

    await firstValueFrom(this.service.deleteOverride(recipe.id, this.filters())).catch(
      () => undefined,
    );
  }

  protected async syncProfessionRecipes(): Promise<void> {
    if (this.professionSyncBusy()) {
      return;
    }

    this.syncStarting.set(true);
    this.syncError.set(null);
    try {
      const job = await firstValueFrom(this.service.syncProfessionRecipes());
      this.jobService.trackJob(job);
    } catch (error: unknown) {
      this.syncError.set(
        error instanceof HttpErrorResponse && error.status === 409
          ? $localize`:@@admin.recipes.professionSync.conflict:A profession sync is already running.`
          : $localize`:@@admin.recipes.professionSync.startError:Unable to start the profession sync.`,
      );
    } finally {
      this.syncStarting.set(false);
    }
  }

  protected dismissProfessionSync(): void {
    this.syncError.set(null);
    this.jobService.dismiss();
  }

  private async rehydrateProfessionSync(): Promise<void> {
    const job = await firstValueFrom(
      this.service.getActiveProfessionSyncJob().pipe(takeUntilDestroyed(this.destroyRef)),
    ).catch(() => null);
    if (job) {
      this.jobService.trackJob(job);
    }
  }

  protected pageSizeValue(): string {
    return String(this.filters().pageSize);
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
