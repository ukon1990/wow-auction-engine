import { ScrollingModule } from '@angular/cdk/scrolling';
import { NgTemplateOutlet } from '@angular/common';
import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
  signal,
} from '@angular/core';
import {
  ColumnDef,
  createAngularTable,
  FlexRenderDirective,
  functionalUpdate,
  getCoreRowModel,
  getSortedRowModel,
  type RowData,
} from '@tanstack/angular-table';
import type { Cell, Column, Header, Row, SortingState, Table } from '@tanstack/table-core';

import { SymbolIconComponent } from '../primitives/symbol-icon.component';
import { PaginationComponent, PaginationState } from './pagination.component';

const DEFAULT_CONTENT_MIN_WIDTH_CLASS = 'min-w-0 w-full';
const DEFAULT_HEADER_ROW_CLASS =
  'grid w-full gap-4 border-b border-white/10 bg-surface-container-high px-container-padding py-4 ee-label text-outline';
const DEFAULT_BODY_ROW_CLASS =
  'grid w-full items-center gap-4 px-container-padding py-3 text-left transition hover:bg-white/5 select-text';
const DEFAULT_SKELETON_ROW_CLASS =
  'grid w-full items-center gap-4 px-container-padding py-3 text-left';
const DEFAULT_GRID_TRACK = 'minmax(12rem, 1fr)';

type TableColumnMeta = {
  readonly align?: 'left' | 'right';
  readonly gridTrack?: string;
  readonly cardRole?: 'primary' | 'metric' | 'detail';
  readonly cardLabel?: string;
  readonly cardPriority?: number;
};

export interface MobileSortOption {
  readonly id: string;
  readonly label: string;
}

@Component({
  selector: 'ee-table',
  host: {
    class: 'flex min-h-0 min-w-0 flex-1',
  },
  imports: [
    FlexRenderDirective,
    NgTemplateOutlet,
    ScrollingModule,
    PaginationComponent,
    SymbolIconComponent,
  ],
  template: `
    <section
      class="ee-glass flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden rounded-lg"
      [attr.aria-label]="sectionAriaLabel()"
    >
      <div class="min-h-0 flex-1 overflow-x-auto">
        <div class="flex h-full min-w-0 flex-col" [class]="contentMinWidthClass()">
          @if (cardView()) {
            @if (mobileSortOptions().length > 0) {
              <div
                class="flex items-center gap-2 border-b border-white/10 bg-surface-container-high px-container-padding py-3"
              >
                <label class="min-w-0 flex-1">
                  <span class="sr-only">Sort column</span>
                  <select
                    class="w-full rounded border border-white/10 bg-surface-container px-3 py-2 ee-label text-on-surface outline-none focus-visible:ring-2 focus-visible:ring-primary/60 disabled:opacity-50"
                    [disabled]="loading()"
                    [value]="currentSortColumnId()"
                    (change)="onMobileSortColumnChange($event)"
                  >
                    @for (option of mobileSortOptions(); track option.id) {
                      <option [value]="option.id">{{ option.label }}</option>
                    }
                  </select>
                </label>
                <button
                  type="button"
                  class="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded border border-white/10 bg-surface-container-high text-on-surface transition hover:bg-surface-container-highest focus-visible:ring-2 focus-visible:ring-primary/60 disabled:opacity-50"
                  [attr.aria-label]="mobileSortDirectionLabel()"
                  [disabled]="loading()"
                  (click)="toggleMobileSortDirection()"
                >
                  <ee-symbol-icon [name]="mobileSortDirectionIcon()" />
                </button>
              </div>
            }
          } @else {
            @for (headerGroup of table.getHeaderGroups(); track headerGroup.id) {
              <div
                role="row"
                [class]="headerRowClass()"
                [style.grid-template-columns]="rowGridTemplateStyle()"
              >
                @for (header of headerGroup.headers; track header.id) {
                  <div
                    role="columnheader"
                    [attr.aria-sort]="headerAriaSort(header)"
                    [class]="headerColumnClass(header.column.columnDef.meta)"
                  >
                    @if (!header.isPlaceholder) {
                      @if (sortableHeader(header)) {
                        <button
                          type="button"
                          [class]="sortHeaderButtonClass(header.column.columnDef.meta)"
                          [disabled]="loading()"
                          (click)="header.column.getToggleSortingHandler()?.($event)"
                        >
                          <span class="min-w-0 truncate">
                            <ng-container
                              *flexRender="
                                header.column.columnDef.header;
                                props: header.getContext();
                                let rendered
                              "
                            >
                              {{ rendered }}
                            </ng-container>
                          </span>
                          <ee-symbol-icon
                            [class]="sortIconClass(header)"
                            [name]="sortIconName(header)"
                          />
                        </button>
                      } @else {
                        <ng-container
                          *flexRender="
                            header.column.columnDef.header;
                            props: header.getContext();
                            let rendered
                          "
                        >
                          {{ rendered }}
                        </ng-container>
                      }
                    }
                  </div>
                }
              </div>
            }
          }
          <div
            cdkScrollable
            [class]="
              cardView()
                ? 'min-h-0 flex-1 overflow-y-auto space-y-3 p-3'
                : 'min-h-0 flex-1 overflow-y-auto divide-y divide-white/5'
            "
          >
            @if (showSkeleton()) {
              @if (cardView()) {
                @for (r of skeletonRowIndices(); track r) {
                  <div
                    aria-hidden="true"
                    class="rounded-lg border border-white/10 bg-surface-container/70 p-4"
                  >
                    <div class="h-5 w-3/4 rounded bg-white/10 animate-pulse"></div>
                    <div class="mt-4 grid grid-cols-2 gap-3">
                      <div class="h-4 rounded bg-white/10 animate-pulse"></div>
                      <div class="h-4 rounded bg-white/10 animate-pulse"></div>
                    </div>
                  </div>
                }
              } @else {
                @for (r of skeletonRowIndices(); track r) {
                  <div
                    role="row"
                    aria-hidden="true"
                    [class]="skeletonRowClass()"
                    [style.grid-template-columns]="rowGridTemplateStyle()"
                  >
                    @for (meta of skeletonColumnMetas(); track $index) {
                      <div [class]="bodyCellClass(meta)">
                        <div
                          class="h-4 max-w-full rounded bg-white/10 animate-pulse"
                          [class]="skeletonBarWidthClass($index)"
                        ></div>
                      </div>
                    }
                  </div>
                }
              }
            } @else if (cardView()) {
              @for (row of table.getRowModel().rows; track row.id) {
                @if (clickableRows()) {
                  <button
                    type="button"
                    [class]="cardRowClass(row.original)"
                    (click)="rowClick.emit(row.original)"
                  >
                    <ng-container
                      [ngTemplateOutlet]="cardContent"
                      [ngTemplateOutletContext]="{ row }"
                    />
                  </button>
                } @else {
                  <article [class]="cardRowClass(row.original)">
                    <ng-container
                      [ngTemplateOutlet]="cardContent"
                      [ngTemplateOutletContext]="{ row }"
                    />
                  </article>
                }
              } @empty {
                <div class="p-8 text-center text-on-surface-variant" i18n="@@table.empty">
                  {{ emptyMessage() }}
                </div>
              }
              <ng-template #cardContent let-row="row">
                @for (cell of cardPrimaryCells(row); track cell.id) {
                  <div class="min-w-0">
                    <ng-container
                      *flexRender="
                        cell.column.columnDef.cell;
                        props: cell.getContext();
                        let rendered
                      "
                    >
                      {{ rendered }}
                    </ng-container>
                  </div>
                }
                @if (cardMetricCells(row).length > 0) {
                  <div class="mt-4 grid grid-cols-2 gap-3">
                    @for (cell of cardMetricCells(row); track cell.id) {
                      <div [class]="cardGridCellClass($index)">
                        <div class="ee-label text-outline">{{ cardLabel(cell.column) }}</div>
                        <div class="mt-1 min-w-0">
                          <ng-container
                            *flexRender="
                              cell.column.columnDef.cell;
                              props: cell.getContext();
                              let rendered
                            "
                          >
                            {{ rendered }}
                          </ng-container>
                        </div>
                      </div>
                    }
                  </div>
                }
                @if (cardDetailCells(row).length > 0) {
                  <dl class="mt-4 grid grid-cols-2 gap-x-3 gap-y-2">
                    @for (cell of cardDetailCells(row); track cell.id) {
                      <div [class]="cardGridCellClass($index)">
                        <dt class="ee-label text-outline">{{ cardLabel(cell.column) }}</dt>
                        <dd class="mt-0.5 min-w-0 truncate text-sm text-on-surface">
                          <ng-container
                            *flexRender="
                              cell.column.columnDef.cell;
                              props: cell.getContext();
                              let rendered
                            "
                          >
                            {{ rendered }}
                          </ng-container>
                        </dd>
                      </div>
                    }
                  </dl>
                }
              </ng-template>
            } @else {
              @for (row of table.getRowModel().rows; track row.id) {
                @if (clickableRows()) {
                  <button
                    type="button"
                    [class]="bodyRowClass(row.original)"
                    [style.grid-template-columns]="rowGridTemplateStyle()"
                    (click)="rowClick.emit(row.original)"
                  >
                    @for (cell of row.getVisibleCells(); track cell.id) {
                      <div [class]="bodyCellClass(cell.column.columnDef.meta)">
                        <ng-container
                          *flexRender="
                            cell.column.columnDef.cell;
                            props: cell.getContext();
                            let rendered
                          "
                        >
                          {{ rendered }}
                        </ng-container>
                      </div>
                    }
                  </button>
                } @else {
                  <div
                    role="row"
                    [class]="bodyRowClass(row.original)"
                    [style.grid-template-columns]="rowGridTemplateStyle()"
                  >
                    @for (cell of row.getVisibleCells(); track cell.id) {
                      <div [class]="bodyCellClass(cell.column.columnDef.meta)">
                        <ng-container
                          *flexRender="
                            cell.column.columnDef.cell;
                            props: cell.getContext();
                            let rendered
                          "
                        >
                          {{ rendered }}
                        </ng-container>
                      </div>
                    }
                  </div>
                }
              } @empty {
                <div class="p-8 text-center text-on-surface-variant" i18n="@@table.empty">
                  {{ emptyMessage() }}
                </div>
              }
            }
          </div>
        </div>
      </div>
      @if (showFooter()) {
        <footer
          class="flex w-full items-center border-t border-white/10 bg-surface-container-high p-4 ee-data text-outline"
        >
          @if (showPagination()) {
            <ee-pagination
              class="w-full"
              [emptySummary]="emptyPaginationSummary()"
              [loading]="loading()"
              [loadingSummary]="loadingPaginationSummary()"
              [pageState]="paginationState()"
              [rowLabel]="paginationRowLabel()"
              [windowSize]="paginationWindowSize()"
              (pageChange)="pageChange.emit($event)"
            />
          } @else {
            <span>{{ footerSummary() }}</span>
          }
        </footer>
      }
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TableComponent<TData extends RowData> {
  /** Defaults avoid NG0950 during SSR when TanStack reads inputs before parent bindings run. */
  readonly data = input<readonly TData[]>([]);
  readonly columns = input<ColumnDef<TData, unknown>[]>([]);

  readonly sectionAriaLabel = input<string>('Data table');
  readonly emptyMessage = input<string>('No rows to display.');
  readonly contentMinWidthClass = input<string>(DEFAULT_CONTENT_MIN_WIDTH_CLASS);
  readonly headerRowClass = input<string>(DEFAULT_HEADER_ROW_CLASS);
  readonly bodyRowClassFn = input<((row: TData) => string) | undefined>(undefined);
  readonly showFooter = input(false, { transform: booleanAttribute });
  readonly footerSummary = input<string>('');
  readonly paginationState = input<PaginationState | undefined>(undefined);
  readonly loadingPaginationSummary = input<string>('Loading rows...');
  readonly emptyPaginationSummary = input<string>('No rows available.');
  readonly paginationRowLabel = input<string>('rows');
  readonly paginationWindowSize = input(5, { transform: Number });
  readonly showPagination = input(false, { transform: booleanAttribute });
  readonly clickableRows = input(false, { transform: booleanAttribute });
  readonly getRowId = input<(row: TData, index: number) => string>();
  readonly cardView = input(false, { transform: booleanAttribute });
  readonly mobileSortOptions = input<readonly MobileSortOption[]>([]);

  readonly manualSorting = input(false, { transform: booleanAttribute });
  readonly sorting = input<SortingState>([]);
  readonly loading = input(false, { transform: booleanAttribute });
  readonly skeletonRowCount = input(0, { transform: Number });
  readonly skeletonRowClass = input<string>(DEFAULT_SKELETON_ROW_CLASS);
  /** When set, drives `grid-template-columns` so layouts match visible column count (avoids brittle Tailwind arbitrary grids). */
  readonly rowGridTemplateColumns = input<string | undefined>(undefined);

  readonly rowClick = output<TData>();
  readonly pageChange = output<number>();
  readonly sortingChange = output<SortingState>();
  private readonly coreRowModel = getCoreRowModel<TData>();
  private readonly sortedRowModel = getSortedRowModel<TData>();
  private readonly localSorting = signal<SortingState>([]);

  /**
   * Cast for ng-packagr `.d.ts` emit: the real value is a TanStack `Table` plus an Angular `Signal` proxy.
   */
  protected readonly table = createAngularTable<TData>(() => {
    const base = {
      data: this.data() as TData[],
      columns: this.columns(),
      getCoreRowModel: this.coreRowModel,
      enableSortingRemoval: false,
      getRowId: (originalRow: TData, index: number) => {
        const fn = this.getRowId();
        return fn ? fn(originalRow as TData, index) : String(index);
      },
    };
    if (this.manualSorting()) {
      return {
        ...base,
        manualSorting: true,
        state: {
          sorting: this.sorting(),
        },
        onSortingChange: (updater) => {
          const next = functionalUpdate(updater, this.sorting());
          this.sortingChange.emit(next);
        },
      };
    }
    return {
      ...base,
      getSortedRowModel: this.sortedRowModel,
      state: {
        sorting: this.localSorting(),
      },
      onSortingChange: (updater) => {
        const next = functionalUpdate(updater, this.localSorting());
        this.localSorting.set(next);
        this.sortingChange.emit(next);
      },
    };
  }) as unknown as Table<TData>;

  protected showSkeleton = computed(
    () => this.loading() && this.skeletonRowCount() > 0 && this.skeletonRowClass() !== '',
  );

  protected skeletonRowIndices(): number[] {
    return Array.from({ length: this.skeletonRowCount() }, (_, i) => i);
  }

  protected skeletonColumnMetas(): ReadonlyArray<{ align?: 'left' | 'right' } | undefined> {
    return this.table
      .getVisibleLeafColumns()
      .map((c: Column<TData, unknown>) => c.columnDef.meta as { align?: 'left' | 'right' });
  }

  protected skeletonBarWidthClass(index: number): string {
    return index === 0 ? 'w-full' : 'w-full max-w-[5rem]';
  }

  protected bodyRowClass(row: TData): string {
    return this.bodyRowClassFn()?.(row) ?? DEFAULT_BODY_ROW_CLASS;
  }

  protected cardRowClass(row: TData): string {
    const selected = this.bodyRowClass(row).includes('border-l-2')
      ? 'border-l-2 border-primary bg-primary/10'
      : '';
    const interactive = this.clickableRows()
      ? 'w-full cursor-pointer text-left transition hover:bg-surface-container-highest focus-visible:ring-2 focus-visible:ring-primary/60'
      : '';
    return `block min-w-0 rounded-lg border border-white/10 bg-surface-container/70 p-4 ${interactive} ${selected}`;
  }

  protected cardPrimaryCells(row: Row<TData>): Cell<TData, unknown>[] {
    return this.cardCellsByRole(row, 'primary');
  }

  protected cardMetricCells(row: Row<TData>): Cell<TData, unknown>[] {
    return this.cardCellsByRole(row, 'metric');
  }

  protected cardDetailCells(row: Row<TData>): Cell<TData, unknown>[] {
    return this.cardCellsByRole(row, 'detail');
  }

  protected cardLabel(column: Column<TData, unknown>): string {
    const meta = column.columnDef.meta as TableColumnMeta | undefined;
    return meta?.cardLabel ?? String(column.columnDef.header ?? column.id);
  }

  protected cardGridCellClass(index: number): string {
    return index % 2 === 0 ? 'min-w-0 text-left' : 'min-w-0 text-right';
  }

  private cardCellsByRole(
    row: Row<TData>,
    role: NonNullable<TableColumnMeta['cardRole']>,
  ): Cell<TData, unknown>[] {
    return row
      .getVisibleCells()
      .filter(
        (cell) =>
          ((cell.column.columnDef.meta as TableColumnMeta | undefined)?.cardRole ?? 'detail') ===
          role,
      )
      .sort((a, b) => this.cardPriority(a.column) - this.cardPriority(b.column));
  }

  private cardPriority(column: Column<TData, unknown>): number {
    return (column.columnDef.meta as TableColumnMeta | undefined)?.cardPriority ?? 100;
  }

  protected rowGridTemplateStyle(): string | undefined {
    const raw = this.rowGridTemplateColumns();
    const trimmed = raw?.trim();
    if (trimmed && trimmed.length > 0) return trimmed;

    const columns = this.table.getVisibleLeafColumns();
    if (columns.length === 0) return undefined;
    return columns
      .map((column) => {
        const meta = column.columnDef.meta as TableColumnMeta | undefined;
        const gridTrack = meta?.gridTrack?.trim();
        return gridTrack && gridTrack.length > 0 ? gridTrack : DEFAULT_GRID_TRACK;
      })
      .join(' ');
  }

  protected sortableHeader(header: Header<TData, unknown>): boolean {
    return header.column.getCanSort();
  }

  protected headerAriaSort(
    header: Header<TData, unknown>,
  ): 'ascending' | 'descending' | 'none' | null {
    if (!this.sortableHeader(header)) return null;
    const s = header.column.getIsSorted();
    if (s === 'asc') return 'ascending';
    if (s === 'desc') return 'descending';
    return 'none';
  }

  protected sortHeaderButtonClass(meta: unknown): string {
    const m = meta as TableColumnMeta | undefined;
    const align = m?.align === 'right' ? 'justify-end text-right' : 'justify-start text-left';
    return `flex w-full min-w-0 cursor-pointer items-center gap-1 rounded px-0.5 py-0.5 ee-label outline-none transition hover:text-primary focus-visible:ring-2 focus-visible:ring-primary/60 disabled:cursor-not-allowed disabled:opacity-50 ${align}`;
  }

  protected sortIconName(header: Header<TData, unknown>): string {
    const s = header.column.getIsSorted();
    if (s === 'asc') return 'keyboard_arrow_up';
    if (s === 'desc') return 'keyboard_arrow_down';
    return 'import_export';
  }

  protected sortIconClass(header: Header<TData, unknown>): string {
    const s = header.column.getIsSorted();
    return s ? 'shrink-0 text-primary' : 'shrink-0 opacity-50';
  }

  protected headerColumnClass(meta: unknown): string {
    const m = meta as TableColumnMeta | undefined;
    return m?.align === 'right' ? 'min-w-0 text-right' : 'min-w-0 text-left';
  }

  protected bodyCellClass(meta: unknown): string {
    const m = meta as TableColumnMeta | undefined;
    return m?.align === 'right'
      ? 'min-w-0 justify-self-end text-right'
      : 'min-w-0 overflow-hidden text-ellipsis text-left';
  }

  protected currentSortColumnId(): string {
    return this.currentSorting()[0]?.id ?? this.mobileSortOptions()[0]?.id ?? '';
  }

  protected mobileSortDirectionIcon(): string {
    return this.currentSorting()[0]?.desc ? 'keyboard_arrow_down' : 'keyboard_arrow_up';
  }

  protected mobileSortDirectionLabel(): string {
    return this.currentSorting()[0]?.desc ? 'Sort descending' : 'Sort ascending';
  }

  protected onMobileSortColumnChange(event: Event): void {
    const select = event.target as HTMLSelectElement | null;
    const id = select?.value;
    if (!id) return;
    this.setSorting([{ id, desc: this.currentSorting()[0]?.desc ?? false }]);
  }

  protected toggleMobileSortDirection(): void {
    const id = this.currentSortColumnId();
    if (!id) return;
    this.setSorting([{ id, desc: !(this.currentSorting()[0]?.desc ?? false) }]);
  }

  private currentSorting(): SortingState {
    return this.manualSorting() ? this.sorting() : this.localSorting();
  }

  private setSorting(next: SortingState): void {
    if (!this.manualSorting()) this.localSorting.set(next);
    this.sortingChange.emit(next);
  }
}
