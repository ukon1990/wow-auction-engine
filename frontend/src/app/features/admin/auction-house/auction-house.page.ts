import { DatePipe, isPlatformBrowser } from '@angular/common';
import {
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
import {
  AuctionHouse,
  AuctionHousePage as AuctionHousePageDto,
  AuctionHousePageSortBy,
  UpdateAuctionHouse,
} from '@api/generated';
import { QueryService } from '@core/services/query.service';
import { ToastService } from '@core/services/toast.service';
import { firstValueFrom, fromEvent, map, startWith } from 'rxjs';
import type { SortingState } from '@tanstack/table-core';
import { PageFrameComponent, PaginationState, SlideOverPanelComponent, TableComponent } from '@ui';
import { createAuctionHouseColumns } from './auction-house-table.columns';
import {
  AuctionHouseQueryState,
  AuctionHouseService,
  defaultAuctionHouseQueryState,
} from './auction-house.service';

@Component({
  selector: 'app-auction-house',
  imports: [DatePipe, FormsModule, PageFrameComponent, SlideOverPanelComponent, TableComponent],
  templateUrl: './auction-house.page.html',
  styleUrl: './auction-house.page.css',
})
export class AuctionHousePage {
  private readonly service = inject(AuctionHouseService);
  private readonly queryService = inject(QueryService<AuctionHouseQueryState>);
  private readonly toast = inject(ToastService);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly destroyRef = inject(DestroyRef);
  private loadId = 0;
  private detailLoadId = 0;

  readonly isLoading = signal(false);
  readonly loadError = signal<string | null>(null);
  readonly savingId = signal<number | null>(null);
  readonly page = signal<AuctionHousePageDto | null>(null);
  readonly selectedHouse = signal<AuctionHouse | null>(null);
  readonly detailLoading = signal(false);
  readonly formError = signal<string | null>(null);
  readonly nextUpdateInput = signal('');
  readonly autoUpdateInput = signal(false);
  readonly viewportWidth = signal(1280);
  readonly cardView = computed(() => this.viewportWidth() <= 767);
  readonly mobileSortOptions = [
    {
      id: AuctionHousePageSortBy.Name,
      label: $localize`:@@admin.auction-house.column.realms:Realms`,
    },
    {
      id: AuctionHousePageSortBy.Region,
      label: $localize`:@@admin.auction-house.column.region:Region`,
    },
    {
      id: AuctionHousePageSortBy.LastModified,
      label: $localize`:@@admin.auction-house.column.lastModified:Updated at`,
    },
    {
      id: AuctionHousePageSortBy.NextUpdate,
      label: $localize`:@@admin.auction-house.column.nextUpdate:Next update`,
    },
    {
      id: AuctionHousePageSortBy.AvgDelay,
      label: $localize`:@@admin.auction-house.column.delays:Delay (min / avg / max)`,
    },
  ];
  readonly columns = createAuctionHouseColumns({
    onDetails: (house) => void this.openDetails(house),
    onUpdateNow: (house) => void this.updateNow(house),
    onToggleAutoUpdate: (house) => void this.toggleAutoUpdate(house),
    isSaving: (house) => this.savingId() === house.connectedRealmId,
  });
  readonly rows = computed(() => this.page()?.items ?? []);
  readonly query = computed(() => this.queryService.queryParams() ?? defaultAuctionHouseQueryState);
  readonly pagination = computed<PaginationState>(
    () =>
      this.page()?.page ?? {
        page: this.query().page,
        pageSize: this.query().pageSize,
        totalItems: 0,
        totalPages: 0,
      },
  );
  readonly sorting = computed<SortingState>(() => {
    const query = this.query();
    return [{ id: query.sortBy, desc: query.sortDirection === 'desc' }];
  });
  readonly canSave = computed(() => {
    const house = this.selectedHouse();
    if (!house || this.savingId() !== null || this.detailLoading()) return false;
    return (
      this.autoUpdateInput() !== Boolean(house.autoUpdate) ||
      (this.nextUpdateInput() !== '' &&
        this.nextUpdateInput() !== this.localDateTime(house.nextUpdate))
    );
  });
  readonly rowId = (house: AuctionHouse) => String(house.connectedRealmId);

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
    effect(() => void this.loadPage(this.query()));
  }

  onPageChange(page: number): void {
    this.queryService.navigateWithState({ ...this.query(), page }, false);
  }

  retryLoad(): void {
    void this.loadPage(this.query());
  }

  onSortingChange(sorting: SortingState): void {
    const sort = sorting[0];
    if (!sort) return;
    const sortBy = Object.values(AuctionHousePageSortBy).find((value) => value === sort.id);
    if (
      !sortBy ||
      !this.columns.some((column) => column.id === sortBy && column.enableSorting !== false)
    )
      return;
    this.queryService.navigateWithState(
      {
        ...this.query(),
        page: 0,
        sortBy,
        sortDirection: sort.desc ? 'desc' : 'asc',
      },
      false,
    );
  }

  async openDetails(house: AuctionHouse): Promise<void> {
    this.setDetail(house);
    this.formError.set(null);
    this.detailLoading.set(true);
    const requestId = ++this.detailLoadId;
    try {
      const fresh = await firstValueFrom(this.service.fetchById(house.connectedRealmId));
      if (requestId !== this.detailLoadId) return;
      this.setDetail(fresh);
    } catch {
      if (requestId === this.detailLoadId)
        this.formError.set(
          $localize`:@@admin.auction-house.detailError:Could not load auction house details.`,
        );
    } finally {
      if (requestId === this.detailLoadId) this.detailLoading.set(false);
    }
  }

  closeDetails(): void {
    this.detailLoadId++;
    this.selectedHouse.set(null);
    this.formError.set(null);
  }

  async saveDetails(): Promise<void> {
    const house = this.selectedHouse();
    if (!house || !this.canSave()) return;
    const update: UpdateAuctionHouse = {};
    if (this.autoUpdateInput() !== Boolean(house.autoUpdate))
      update.autoUpdate = this.autoUpdateInput();
    if (this.nextUpdateInput() && this.nextUpdateInput() !== this.localDateTime(house.nextUpdate)) {
      const timestamp = new Date(this.nextUpdateInput());
      if (Number.isNaN(timestamp.getTime())) {
        this.formError.set(
          $localize`:@@admin.auction-house.invalidDate:Enter a valid next update time.`,
        );
        return;
      }
      update.nextUpdate = timestamp.toISOString();
    }
    await this.mutate(house, update);
  }

  async updateNow(house: AuctionHouse): Promise<void> {
    if (!house.autoUpdate) return;
    await this.mutate(house, { nextUpdate: new Date().toISOString() });
  }

  async toggleAutoUpdate(house: AuctionHouse): Promise<void> {
    await this.mutate(house, { autoUpdate: !house.autoUpdate });
  }

  private async loadPage(query: AuctionHouseQueryState): Promise<void> {
    const requestId = ++this.loadId;
    this.isLoading.set(true);
    this.loadError.set(null);
    this.page.set(null);
    try {
      const page = await firstValueFrom(this.service.getPageByQuery(query));
      if (requestId === this.loadId && page) this.page.set(page);
    } catch {
      if (requestId === this.loadId)
        this.loadError.set(
          $localize`:@@admin.auction-house.loadError:Could not load auction houses.`,
        );
    } finally {
      if (requestId === this.loadId) this.isLoading.set(false);
    }
  }

  private async mutate(house: AuctionHouse, update: UpdateAuctionHouse): Promise<void> {
    if (this.savingId() !== null) return;
    this.savingId.set(house.connectedRealmId);
    this.formError.set(null);
    try {
      const updated = await firstValueFrom(this.service.update(house.connectedRealmId, update));
      this.service.clearPages();
      if (this.selectedHouse()?.connectedRealmId === house.connectedRealmId)
        this.setDetail(updated);
      await this.loadPage(this.query());
      this.toast.success($localize`:@@admin.auction-house.saved:Auction house updated.`);
    } catch {
      const message = $localize`:@@admin.auction-house.saveError:Could not update auction house. Please try again.`;
      this.formError.set(message);
      this.toast.error(message);
    } finally {
      this.savingId.set(null);
    }
  }

  private setDetail(house: AuctionHouse): void {
    this.selectedHouse.set(house);
    this.autoUpdateInput.set(Boolean(house.autoUpdate));
    this.nextUpdateInput.set(this.localDateTime(house.nextUpdate));
  }

  private localDateTime(value?: string | null): string {
    if (!value) return '';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '';
    return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
  }
}
