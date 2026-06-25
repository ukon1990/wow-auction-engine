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
  AdminItem,
  AdminItemApiCompareResponse,
  AdminItemCreateRequest,
  AdminItemOverrideRequest,
} from '@api/generated';
import { AdminItemService } from '@features/admin/items/admin-item.service';
import { ItemApiCompareComponent } from '@features/admin/items/item-api-compare.component';
import { ItemFormComponent, type ItemFormMode } from '@features/admin/items/item-form.component';
import {
  defaultItemFilters,
  toItemSearchParams,
  type ItemFilterState,
} from '@features/admin/items/item-filters';
import { createItemColumns } from '@features/admin/items/items-table.columns';
import {
  PageFrameComponent,
  PaginationState,
  SearchInputComponent,
  SelectInputComponent,
  SlideOverPanelComponent,
  TableComponent,
} from '@ui';
import { debounceTime, distinctUntilChanged, firstValueFrom, Subject, switchMap } from 'rxjs';

const PAGE_SIZE = 50;

@Component({
  selector: 'app-items-page',
  imports: [
    FormsModule,
    PageFrameComponent,
    TableComponent,
    SearchInputComponent,
    SelectInputComponent,
    ItemFormComponent,
    ItemApiCompareComponent,
    SlideOverPanelComponent,
  ],
  templateUrl: './items.page.html',
  styleUrl: './items.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ItemsPage {
  private readonly service = inject(AdminItemService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly searchTrigger = new Subject<void>();

  protected readonly loading = this.service.loading.asReadonly();
  protected readonly mutationLoading = this.service.mutationLoading.asReadonly();
  protected readonly error = this.service.error.asReadonly();
  protected readonly pageData = this.service.page.asReadonly();

  protected readonly filters = signal<ItemFilterState>(defaultItemFilters());
  protected readonly page = signal(0);
  protected readonly formMode = signal<ItemFormMode | null>(null);
  protected readonly editingItem = signal<AdminItem | null>(null);
  protected readonly formError = signal<string | null>(null);
  protected readonly compareResult = signal<AdminItemApiCompareResponse | null>(null);
  protected readonly compareLoading = signal(false);

  protected readonly columns = signal(
    createItemColumns({
      onEdit: (item) => this.openEditForm(item),
      onRemoveOverride: (item) => void this.removeOverride(item),
    }),
  );

  protected readonly sortOptions = [
    { id: 'id', label: 'Item ID' },
    { id: 'name', label: 'Name' },
    { id: 'quality', label: 'Quality' },
    { id: 'expansion', label: 'Expansion' },
    { id: 'updatedAt', label: 'Updated' },
  ];

  protected readonly overrideFilterOptions = [
    { id: '', label: 'All items' },
    { id: 'true', label: 'With override' },
    { id: 'false', label: 'Without override' },
  ];

  protected readonly items = computed(() => this.pageData()?.items ?? []);

  protected readonly paginationState = computed<PaginationState>(() => {
    const metadata = this.pageData()?.page;
    return {
      page: metadata?.page ?? 0,
      pageSize: metadata?.pageSize ?? PAGE_SIZE,
      totalItems: metadata?.totalItems ?? 0,
      totalPages: Math.max(1, metadata?.totalPages ?? 1),
    };
  });

  protected readonly formTitle = computed(() =>
    this.formMode() === 'create' ? 'Create manual item' : 'Edit item override',
  );

  constructor() {
    const subscription = this.searchTrigger
      .pipe(
        debounceTime(250),
        switchMap(() =>
          this.service.load(
            toItemSearchParams(this.filters(), this.page(), PAGE_SIZE),
          ),
        ),
      )
      .subscribe();

    this.destroyRef.onDestroy(() => subscription.unsubscribe());
    this.refresh();
  }

  protected updateFilter<K extends keyof ItemFilterState>(key: K, value: ItemFilterState[K]): void {
    this.filters.update((current) => ({ ...current, [key]: value }));
    this.page.set(0);
    this.refresh();
  }

  protected onPageChange(page: number): void {
    this.page.set(page);
    this.refresh();
  }

  protected openCreateForm(): void {
    this.formError.set(null);
    this.compareResult.set(null);
    this.editingItem.set(null);
    this.formMode.set('create');
  }

  protected openEditForm(item: AdminItem): void {
    this.formError.set(null);
    this.compareResult.set(null);
    this.editingItem.set(item);
    this.formMode.set('edit');
    void this.loadCompare(item.id);
  }

  protected closeForm(): void {
    this.formMode.set(null);
    this.editingItem.set(null);
    this.formError.set(null);
    this.compareResult.set(null);
  }

  protected async submitForm(
    request: AdminItemCreateRequest | AdminItemOverrideRequest,
  ): Promise<void> {
    this.formError.set(null);
    const mode = this.formMode();
    const editing = this.editingItem();

    try {
      if (mode === 'create') {
        await firstValueFrom(this.service.createItem(request as AdminItemCreateRequest));
      } else if (editing) {
        await firstValueFrom(
          this.service.upsertOverride(editing.id, request as AdminItemOverrideRequest),
        );
      }
      this.closeForm();
      this.refresh();
    } catch (error: unknown) {
      this.formError.set(readSubmitError(error, 'Unable to save item.'));
    }
  }

  protected async removeOverride(item: AdminItem): Promise<void> {
    const confirmed = globalThis.confirm?.(`Clear override for item ${item.id}?`);
    if (!confirmed) {
      return;
    }
    await firstValueFrom(this.service.deleteOverride(item.id));
    if (this.editingItem()?.id === item.id) {
      this.closeForm();
    }
    this.refresh();
  }

  protected readonly itemRowId = (row: AdminItem): string => String(row.id);

  private refresh(): void {
    this.searchTrigger.next();
  }

  private async loadCompare(itemId: number): Promise<void> {
    this.compareLoading.set(true);
    try {
      const result = await firstValueFrom(this.service.compareWithApi(itemId));
      this.compareResult.set(result);
    } catch {
      this.compareResult.set(null);
    } finally {
      this.compareLoading.set(false);
    }
  }
}

function readSubmitError(error: unknown, fallback: string): string {
  if (error && typeof error === 'object' && 'error' in error) {
    const body = (error as { error?: unknown }).error;
    if (typeof body === 'string' && body.trim().length > 0) {
      return body;
    }
    if (
      body &&
      typeof body === 'object' &&
      'detail' in body &&
      typeof body.detail === 'string'
    ) {
      return body.detail;
    }
  }
  return fallback;
}
