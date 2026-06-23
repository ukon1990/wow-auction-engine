import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  AdminExpansion,
  AdminExpansionItemRange,
  AdminExpansionItemRangeRequest,
  AdminExpansionRequest,
  AdminItemJob,
} from '@api/generated';
import { AdminExpansionJobService } from '@features/admin/expansions/admin-expansion-job.service';
import { AdminExpansionService } from '@features/admin/expansions/admin-expansion.service';
import {
  ExpansionFormComponent,
  type ExpansionFormMode,
} from '@features/admin/expansions/expansion-form.component';
import {
  defaultExpansionRangeFilters,
  defaultCreateRangeValues,
  filterExpansionRanges,
  sortExpansionRanges,
  type CreateExpansionRangeDefaults,
  type ExpansionRangeFilterState,
} from '@features/admin/expansions/expansion-range-filters';
import {
  expansionJobSummary,
  expansionJobTitle,
} from '@features/admin/expansions/expansion-job-summary';
import {
  ExpansionRangeFormComponent,
  type ExpansionRangeFormMode,
} from '@features/admin/expansions/expansion-range-form.component';
import {
  createExpansionCatalogColumns,
  createExpansionRangeColumns,
} from '@features/admin/expansions/expansion-ranges-table.columns';
import {
  PageFrameComponent,
  PaginationState,
  SearchInputComponent,
  SelectInputComponent,
  SlideOverPanelComponent,
  TableComponent,
} from '@ui';
import { firstValueFrom } from 'rxjs';

const PAGE_SIZE = 50;

@Component({
  selector: 'app-expansions-page',
  imports: [
    FormsModule,
    PageFrameComponent,
    TableComponent,
    SearchInputComponent,
    SelectInputComponent,
    ExpansionRangeFormComponent,
    ExpansionFormComponent,
    SlideOverPanelComponent,
  ],
  templateUrl: './expansions.page.html',
  styleUrl: './expansions.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExpansionsPage {
  private readonly service = inject(AdminExpansionService);
  private readonly jobService = inject(AdminExpansionJobService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly loading = this.service.loading.asReadonly();
  protected readonly mutationLoading = this.service.mutationLoading.asReadonly();
  protected readonly expansions = this.service.expansions.asReadonly();
  protected readonly ranges = this.service.ranges.asReadonly();
  protected readonly error = this.service.error.asReadonly();
  protected readonly activeJob = this.jobService.activeJob.asReadonly();
  protected readonly jobDismissed = this.jobService.dismissed.asReadonly();

  protected readonly catalogColumns = signal(
    createExpansionCatalogColumns({
      onEdit: (expansion) => this.openEditExpansionForm(expansion),
      onDelete: (expansion) => void this.deleteExpansion(expansion),
    }),
  );
  protected readonly rangeColumns = signal(
    createExpansionRangeColumns({
      onEdit: (range) => this.openEditRangeForm(range),
      onDelete: (range) => void this.deleteRange(range),
    }),
  );

  protected readonly filters = signal<ExpansionRangeFilterState>(defaultExpansionRangeFilters());
  protected readonly page = signal(0);
  protected readonly rangeFormMode = signal<ExpansionRangeFormMode | null>(null);
  protected readonly expansionFormMode = signal<ExpansionFormMode | null>(null);
  protected readonly editingRange = signal<AdminExpansionItemRange | null>(null);
  protected readonly editingExpansion = signal<AdminExpansion | null>(null);
  protected readonly createRangeDefaults = signal<CreateExpansionRangeDefaults>({
    expansionId: '',
    startItemId: '',
  });
  protected readonly rangeFormError = signal<string | null>(null);
  protected readonly expansionFormError = signal<string | null>(null);

  protected readonly expansionFilterOptions = computed(() => [
    { id: '', label: 'All expansions' },
    ...this.expansions().map((expansion) => ({
      id: String(expansion.id),
      label: expansion.name,
    })),
  ]);

  protected readonly sourceFilterOptions = [
    { id: '', label: 'All sources' },
    { id: 'manual', label: 'manual' },
    { id: 'itemversion', label: 'itemversion' },
  ];

  protected readonly enabledFilterOptions = [
    { id: '', label: 'All states' },
    { id: 'true', label: 'Enabled only' },
    { id: 'false', label: 'Disabled only' },
  ];

  protected readonly sortedCatalog = computed(() =>
    [...this.expansions()].sort((left, right) => left.displayOrder - right.displayOrder),
  );

  protected readonly createExpansionDefaults = computed(() => {
    const expansions = this.sortedCatalog();
    const maxId = expansions.reduce((max, expansion) => Math.max(max, expansion.id), 0);
    const maxDisplayOrder = expansions.reduce(
      (max, expansion) => Math.max(max, expansion.displayOrder),
      0,
    );
    return {
      id: maxId + 1,
      displayOrder: maxDisplayOrder + 10,
    };
  });

  protected readonly filteredRanges = computed(() =>
    sortExpansionRanges(filterExpansionRanges(this.ranges(), this.filters())),
  );

  protected readonly paginationState = computed<PaginationState>(() => {
    const totalItems = this.filteredRanges().length;
    const totalPages = Math.max(1, Math.ceil(totalItems / PAGE_SIZE));
    const page = Math.min(this.page(), Math.max(0, totalPages - 1));
    return {
      page,
      pageSize: PAGE_SIZE,
      totalItems,
      totalPages,
    };
  });

  protected readonly paginatedRanges = computed(() => {
    const { page, pageSize } = this.paginationState();
    const start = page * pageSize;
    return this.filteredRanges().slice(start, start + pageSize);
  });

  protected readonly showJobBanner = computed(() => {
    const job = this.activeJob();
    return job !== null && !this.jobDismissed();
  });

  protected readonly jobTitle = computed(() => {
    const job = this.activeJob();
    return job ? expansionJobTitle(job) : '';
  });

  protected readonly jobSummary = computed(() => {
    const job = this.activeJob();
    return job ? expansionJobSummary(job) : null;
  });

  protected readonly jobIsRunning = computed(
    () => this.activeJob()?.status === AdminItemJob.StatusEnum.Running,
  );

  protected readonly jobFailed = computed(
    () => this.activeJob()?.status === AdminItemJob.StatusEnum.Failed,
  );

  protected readonly rangeFormTitle = computed(() =>
    this.rangeFormMode() === 'edit' ? 'Edit expansion range' : 'Add expansion range',
  );

  protected readonly expansionFormTitle = computed(() =>
    this.expansionFormMode() === 'edit' ? 'Edit expansion' : 'Add expansion',
  );

  constructor() {
    firstValueFrom(this.service.load());
    this.destroyRef.onDestroy(() => this.jobService.stopPolling());
  }

  protected updateFilter<K extends keyof ExpansionRangeFilterState>(
    key: K,
    value: ExpansionRangeFilterState[K],
  ): void {
    this.filters.update((current) => ({ ...current, [key]: value }));
    this.page.set(0);
  }

  protected onPageChange(page: number): void {
    this.page.set(page);
  }

  protected openCreateRangeForm(): void {
    this.expansionFormMode.set(null);
    this.rangeFormError.set(null);
    this.editingRange.set(null);
    this.createRangeDefaults.set(defaultCreateRangeValues(this.expansions(), this.ranges()));
    this.rangeFormMode.set('create');
  }

  protected openEditRangeForm(range: AdminExpansionItemRange): void {
    this.expansionFormMode.set(null);
    this.rangeFormError.set(null);
    this.editingRange.set(range);
    this.rangeFormMode.set('edit');
  }

  protected closeRangeForm(): void {
    this.rangeFormMode.set(null);
    this.editingRange.set(null);
    this.rangeFormError.set(null);
  }

  protected openCreateExpansionForm(): void {
    this.rangeFormMode.set(null);
    this.expansionFormError.set(null);
    this.editingExpansion.set(null);
    this.expansionFormMode.set('create');
  }

  protected openEditExpansionForm(expansion: AdminExpansion): void {
    this.rangeFormMode.set(null);
    this.expansionFormError.set(null);
    this.editingExpansion.set(expansion);
    this.expansionFormMode.set('edit');
  }

  protected closeExpansionForm(): void {
    this.expansionFormMode.set(null);
    this.editingExpansion.set(null);
    this.expansionFormError.set(null);
  }

  protected async submitRange(request: AdminExpansionItemRangeRequest): Promise<void> {
    this.rangeFormError.set(null);
    const mode = this.rangeFormMode();
    const editing = this.editingRange();

    try {
      if (mode === 'edit' && editing) {
        await firstValueFrom(this.service.updateRange(editing.id, request));
      } else {
        await firstValueFrom(this.service.createRange(request));
      }
      this.closeRangeForm();
    } catch (error: unknown) {
      this.rangeFormError.set(readSubmitError(error, 'Unable to save expansion range.'));
    }
  }

  protected async submitExpansion(request: AdminExpansionRequest): Promise<void> {
    this.expansionFormError.set(null);
    const mode = this.expansionFormMode();
    const editing = this.editingExpansion();

    try {
      if (mode === 'edit' && editing) {
        await firstValueFrom(this.service.updateExpansion(editing.id, request));
      } else {
        await firstValueFrom(this.service.createExpansion(request));
      }
      this.closeExpansionForm();
    } catch (error: unknown) {
      this.expansionFormError.set(readSubmitError(error, 'Unable to save expansion.'));
    }
  }

  protected async deleteRange(range: AdminExpansionItemRange): Promise<void> {
    const confirmed = globalThis.confirm?.(
      `Delete range ${range.startItemId}-${range.endItemId} for ${range.expansion.name}?`,
    );
    if (!confirmed) {
      return;
    }
    await firstValueFrom(this.service.deleteRange(range.id));
    if (this.editingRange()?.id === range.id) {
      this.closeRangeForm();
    }
  }

  protected async deleteExpansion(expansion: AdminExpansion): Promise<void> {
    const confirmed = globalThis.confirm?.(`Delete expansion ${expansion.name}?`);
    if (!confirmed) {
      return;
    }

    try {
      await firstValueFrom(this.service.deleteExpansion(expansion.id));
      if (this.editingExpansion()?.id === expansion.id) {
        this.closeExpansionForm();
      }
    } catch (error: unknown) {
      this.expansionFormError.set(
        readSubmitError(error, 'Expansion is in use by ranges or items.'),
      );
    }
  }

  protected async applyRanges(): Promise<void> {
    await firstValueFrom(this.service.startApplyJob());
  }

  protected async fetchMissingItems(): Promise<void> {
    await firstValueFrom(this.service.startFetchMissingJob());
  }

  protected dismissJobBanner(): void {
    this.jobService.dismiss();
  }

  protected readonly rangeRowId = (row: AdminExpansionItemRange): string => String(row.id);
}

function readSubmitError(error: unknown, fallback: string): string {
  if (error && typeof error === 'object' && 'error' in error) {
    const body = (error as { error?: unknown }).error;
    if (typeof body === 'string' && body.trim().length > 0) {
      return body;
    }
    if (body && typeof body === 'object' && 'message' in body && typeof body.message === 'string') {
      return body.message;
    }
  }
  return fallback;
}
