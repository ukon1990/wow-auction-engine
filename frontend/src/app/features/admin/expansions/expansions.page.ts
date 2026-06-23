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
  AdminExpansionItemRange,
  AdminExpansionItemRangeRequest,
  AdminItemJob,
} from '@api/generated';
import { AdminExpansionJobService } from '@features/admin/expansions/admin-expansion-job.service';
import { AdminExpansionService } from '@features/admin/expansions/admin-expansion.service';
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

  protected readonly catalogColumns = signal(createExpansionCatalogColumns());
  protected readonly rangeColumns = signal(
    createExpansionRangeColumns({
      onEdit: (range) => this.openEditForm(range),
      onDelete: (range) => void this.deleteRange(range),
    }),
  );

  protected readonly filters = signal<ExpansionRangeFilterState>(defaultExpansionRangeFilters());
  protected readonly page = signal(0);
  protected readonly formMode = signal<ExpansionRangeFormMode | null>(null);
  protected readonly editingRange = signal<AdminExpansionItemRange | null>(null);
  protected readonly createRangeDefaults = signal<CreateExpansionRangeDefaults>({
    expansionId: '',
    startItemId: '',
  });
  protected readonly formError = signal<string | null>(null);

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

  protected readonly formTitle = computed(() =>
    this.formMode() === 'edit' ? 'Edit expansion range' : 'Add expansion range',
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

  protected openCreateForm(): void {
    this.formError.set(null);
    this.editingRange.set(null);
    this.createRangeDefaults.set(defaultCreateRangeValues(this.expansions(), this.ranges()));
    this.formMode.set('create');
  }

  protected openEditForm(range: AdminExpansionItemRange): void {
    this.formError.set(null);
    this.editingRange.set(range);
    this.formMode.set('edit');
  }

  protected closeForm(): void {
    this.formMode.set(null);
    this.editingRange.set(null);
    this.formError.set(null);
  }

  protected async submitRange(request: AdminExpansionItemRangeRequest): Promise<void> {
    this.formError.set(null);
    const mode = this.formMode();
    const editing = this.editingRange();

    try {
      if (mode === 'edit' && editing) {
        await firstValueFrom(this.service.updateRange(editing.id, request));
      } else {
        await firstValueFrom(this.service.createRange(request));
      }
      this.closeForm();
    } catch (error: unknown) {
      this.formError.set(readSubmitError(error));
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
      this.closeForm();
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

function readSubmitError(error: unknown): string {
  if (error && typeof error === 'object' && 'error' in error) {
    const body = (error as { error?: unknown }).error;
    if (typeof body === 'string' && body.trim().length > 0) {
      return body;
    }
    if (body && typeof body === 'object' && 'message' in body && typeof body.message === 'string') {
      return body.message;
    }
  }
  return 'Unable to save expansion range.';
}
