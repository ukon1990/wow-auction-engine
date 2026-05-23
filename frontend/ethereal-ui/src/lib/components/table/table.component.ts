import { ScrollingModule } from '@angular/cdk/scrolling';
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
import type { Column, Header, SortingState, Table } from '@tanstack/table-core';

import { SymbolIconComponent } from '../primitives/symbol-icon.component';

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
};

@Component({
  selector: 'ee-table',
  host: {
    class: 'flex min-h-0 min-w-0 flex-1',
  },
  imports: [FlexRenderDirective, ScrollingModule, SymbolIconComponent],
  template: `
    <section
      class="ee-glass flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden rounded-lg"
      [attr.aria-label]="sectionAriaLabel()"
    >
      <div class="min-h-0 flex-1 overflow-x-auto">
        <div class="flex h-full min-w-0 flex-col" [class]="contentMinWidthClass()">
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
          <div cdkScrollable class="min-h-0 flex-1 overflow-y-auto divide-y divide-white/5">
            @if (showSkeleton()) {
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
          class="flex items-center justify-between border-t border-white/10 bg-surface-container-high p-4 ee-data text-outline"
        >
          <span>{{ footerSummary() }}</span>
          @if (showPagination()) {
            <div class="flex gap-2">
              <button
                type="button"
                class="rounded p-1 transition hover:text-primary enabled:cursor-pointer disabled:cursor-not-allowed disabled:opacity-40"
                [attr.aria-label]="previousPageAriaLabel()"
                [disabled]="loading()"
                (click)="previousPage.emit()"
              >
                <ee-symbol-icon name="chevron_left" />
              </button>
              <button
                type="button"
                class="rounded p-1 transition hover:text-primary enabled:cursor-pointer disabled:cursor-not-allowed disabled:opacity-40"
                [attr.aria-label]="nextPageAriaLabel()"
                [disabled]="loading()"
                (click)="nextPage.emit()"
              >
                <ee-symbol-icon name="chevron_right" />
              </button>
            </div>
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
  readonly showPagination = input(false, { transform: booleanAttribute });
  readonly previousPageAriaLabel = input<string>('Previous page');
  readonly nextPageAriaLabel = input<string>('Next page');
  readonly clickableRows = input(false, { transform: booleanAttribute });
  readonly getRowId = input<(row: TData, index: number) => string>();

  readonly manualSorting = input(false, { transform: booleanAttribute });
  readonly sorting = input<SortingState>([]);
  readonly loading = input(false, { transform: booleanAttribute });
  readonly skeletonRowCount = input(0, { transform: Number });
  readonly skeletonRowClass = input<string>(DEFAULT_SKELETON_ROW_CLASS);
  /** When set, drives `grid-template-columns` so layouts match visible column count (avoids brittle Tailwind arbitrary grids). */
  readonly rowGridTemplateColumns = input<string | undefined>(undefined);

  readonly rowClick = output<TData>();
  readonly previousPage = output<void>();
  readonly nextPage = output<void>();
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
}
