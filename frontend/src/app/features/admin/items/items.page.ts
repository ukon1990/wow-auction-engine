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
import { AdminItem1, AdminItemCreateRequest, AdminItemOverrideRequest } from '@api/generated';
import { AdminItemComparePanelComponent } from './admin-item-compare-panel.component';
import { AdminItemCreateFormComponent } from './admin-item-create-form.component';
import { AdminItemOverrideFormComponent } from './admin-item-override-form.component';
import { AdminItemService } from './admin-item.service';
import { createAdminItemColumns } from './admin-items-table.columns';
import { AdminItemFilterState, defaultAdminItemFilters } from './item-filters';
import { AdminExpansionService } from '@features/admin/expansions/admin-expansion.service';
import {
  PageFrameComponent,
  PaginationState,
  SearchInputComponent,
  SelectInputComponent,
  SlideOverPanelComponent,
  TableComponent,
} from '@ui';
import { firstValueFrom, fromEvent, map, startWith } from 'rxjs';

type PanelMode = 'edit' | 'create' | 'compare';

const DEFAULT_VIEWPORT_WIDTH = 1280;
const MOBILE_CARD_VIEW_MAX_WIDTH = 767;

@Component({
  selector: 'app-admin-items-page',
  imports: [
    FormsModule,
    AdminItemComparePanelComponent,
    AdminItemCreateFormComponent,
    AdminItemOverrideFormComponent,
    PageFrameComponent,
    SearchInputComponent,
    SelectInputComponent,
    SlideOverPanelComponent,
    TableComponent,
  ],
  templateUrl: './items.page.html',
  styleUrl: './items.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ItemsPage {
  private readonly service = inject(AdminItemService);
  private readonly expansionService = inject(AdminExpansionService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly platformId = inject(PLATFORM_ID);

  protected readonly loading = this.service.loading.asReadonly();
  protected readonly mutationLoading = this.service.mutationLoading.asReadonly();
  protected readonly detailLoading = this.service.detailLoading.asReadonly();
  protected readonly compareLoading = this.service.compareLoading.asReadonly();
  protected readonly items = this.service.items.asReadonly();
  protected readonly page = this.service.page.asReadonly();
  protected readonly selectedItem = this.service.selectedItem.asReadonly();
  protected readonly compare = this.service.compare.asReadonly();
  protected readonly error = this.service.error.asReadonly();
  protected readonly detailError = this.service.detailError.asReadonly();
  protected readonly compareError = this.service.compareError.asReadonly();
  protected readonly expansions = this.expansionService.expansions.asReadonly();

  protected readonly filters = signal<AdminItemFilterState>(defaultAdminItemFilters());
  protected readonly panelMode = signal<PanelMode | null>(null);
  protected readonly formError = signal<string | null>(null);
  protected readonly viewportWidth = signal(DEFAULT_VIEWPORT_WIDTH);
  protected readonly cardView = computed(() => this.viewportWidth() <= MOBILE_CARD_VIEW_MAX_WIDTH);

  protected readonly hasOverrideOptions = [
    { id: '', label: $localize`:@@admin.items.filter.anyOverride:All override states` },
    { id: 'true', label: $localize`:@@admin.items.filter.hasOverride:Has override` },
    { id: 'false', label: $localize`:@@admin.items.filter.noOverride:No override` },
  ];

  protected readonly pageSizeOptions = [
    { id: '25', label: '25' },
    { id: '50', label: '50' },
    { id: '100', label: '100' },
  ];
  protected readonly rowId = (item: AdminItem1): string => String(item.id);

  protected readonly columns = signal(
    createAdminItemColumns({
      onEdit: (item) => void this.openEditPanel(item),
      onCompare: (item) => void this.openComparePanel(item),
      onDeleteOverride: (item) => void this.deleteOverride(item),
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
      case 'create':
        return $localize`:@@admin.items.panel.create:Create manual item`;
      case 'compare':
        return $localize`:@@admin.items.panel.compare:Compare Blizzard API`;
      case 'edit':
        return $localize`:@@admin.items.panel.edit:Edit item override`;
      default:
        return '';
    }
  });

  constructor() {
    if (isPlatformBrowser(this.platformId)) {
      fromEvent(window, 'resize')
        .pipe(
          startWith(null),
          map(() => window.innerWidth),
          takeUntilDestroyed(this.destroyRef),
        )
        .subscribe((width) => {
          this.viewportWidth.set(width);
        });
    }

    void this.reload();
    void firstValueFrom(this.expansionService.load()).catch(() => undefined);
  }

  protected updateFilter<K extends keyof AdminItemFilterState>(
    key: K,
    value: AdminItemFilterState[K],
  ): void {
    this.filters.update((current) => ({ ...current, [key]: value, page: 0 }));
  }

  protected updatePageSize(value: string): void {
    const pageSize = Number.parseInt(value, 10);
    this.filters.update((current) => ({
      ...current,
      page: 0,
      pageSize: Number.isFinite(pageSize) ? pageSize : 25,
    }));
    void this.reload();
  }

  protected applyFilters(): void {
    this.filters.update((current) => ({ ...current, page: 0 }));
    void this.reload();
  }

  protected resetFilters(): void {
    this.filters.set(defaultAdminItemFilters());
    void this.reload();
  }

  protected onPageChange(page: number): void {
    this.filters.update((current) => ({ ...current, page }));
    void this.reload();
  }

  protected async openEditPanel(item: AdminItem1): Promise<void> {
    this.formError.set(null);
    this.panelMode.set('edit');
    await this.loadItem(item.id);
  }

  protected openCreatePanel(): void {
    this.service.clearSelection();
    this.formError.set(null);
    this.panelMode.set('create');
  }

  protected async openComparePanel(item: AdminItem1): Promise<void> {
    this.formError.set(null);
    this.panelMode.set('compare');
    await this.loadItem(item.id);
    await firstValueFrom(this.service.compareWithApi(item.id)).catch(() => undefined);
  }

  protected closePanel(): void {
    this.panelMode.set(null);
    this.formError.set(null);
    this.service.clearSelection();
  }

  protected async submitOverride(request: AdminItemOverrideRequest): Promise<void> {
    const item = this.selectedItem();
    if (!item) return;
    this.formError.set(null);
    await firstValueFrom(this.service.upsertOverride(item.id, request, this.filters())).catch(
      (error: unknown) => {
        this.formError.set(error instanceof Error ? error.message : 'Unable to save override.');
      },
    );
  }

  protected async submitCreate(request: AdminItemCreateRequest): Promise<void> {
    this.formError.set(null);
    await firstValueFrom(this.service.createItem(request, this.filters()))
      .then((item) => {
        this.panelMode.set('edit');
        return this.loadItem(item.id);
      })
      .catch((error: unknown) => {
        this.formError.set(error instanceof Error ? error.message : 'Unable to create item.');
      });
  }

  protected async deleteOverride(item: AdminItem1): Promise<void> {
    if (!item.hasOverride) return;
    const confirmed = window.confirm(
      $localize`:@@admin.items.deleteConfirm:Delete this item override? Base item data will be inherited again.`,
    );
    if (!confirmed) return;

    await firstValueFrom(this.service.deleteOverride(item.id, this.filters())).catch(
      () => undefined,
    );
  }

  protected pageSizeValue(): string {
    return String(this.filters().pageSize);
  }

  private async reload(): Promise<void> {
    await firstValueFrom(this.service.search(this.filters())).catch(() => undefined);
  }

  private async loadItem(id: number): Promise<void> {
    await firstValueFrom(this.service.loadItem(id)).catch(() => undefined);
  }
}
