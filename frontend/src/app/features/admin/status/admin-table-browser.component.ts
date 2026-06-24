import { NestedTreeControl } from '@angular/cdk/tree';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatTreeModule, MatTreeNestedDataSource } from '@angular/material/tree';
import { AdminSqlColumn, AdminSqlIndex, AdminSqlMetadata, AdminSqlTable } from '@api/generated';
import { SlideOverPanelComponent, SymbolIconComponent } from '@ui';
import { AdminSqlService, readAdminSqlError } from './admin-sql.service';

type TableTreeNode = TableNode | GroupNode | DetailNode;

interface TableNode {
  readonly kind: 'table';
  readonly label: string;
  readonly table: AdminSqlTable;
  readonly children: readonly TableTreeNode[];
}

interface GroupNode {
  readonly kind: 'group';
  readonly label: string;
  readonly children: readonly TableTreeNode[];
}

interface DetailNode {
  readonly kind: 'detail';
  readonly label: string;
  readonly description: string;
}

@Component({
  selector: 'app-admin-table-browser',
  imports: [MatTreeModule, SlideOverPanelComponent, SymbolIconComponent],
  template: `
    <button
      type="button"
      class="h-10 rounded-md border border-white/10 px-4 font-semibold text-on-surface transition hover:bg-white/5 focus:outline-none focus:ring-2 focus:ring-primary-container"
      (click)="openPanel()"
    >
      Tables
    </button>

    <ee-slide-over [open]="open()" title="Tables" (closed)="open.set(false)">
      @if (loading()) {
        <p class="ee-data text-outline">Loading tables...</p>
      } @else if (error()) {
        <p class="rounded-md border border-error/30 bg-error/10 px-3 py-2 text-sm text-error" role="alert">
          {{ error() }}
        </p>
      } @else {
        <mat-tree [dataSource]="dataSource" [treeControl]="treeControl" class="grid gap-1 bg-transparent">
          <mat-nested-tree-node *matTreeNodeDef="let node">
            <div class="rounded-md border border-white/5 bg-surface-container px-3 py-2">
              <p class="font-mono text-sm text-on-surface">{{ node.label }}</p>
              @if (node.description) {
                <p class="mt-1 text-xs text-outline">{{ node.description }}</p>
              }
            </div>
          </mat-nested-tree-node>

          <mat-nested-tree-node *matTreeNodeDef="let node; when: hasChild">
            <div class="grid gap-1">
              <button
                type="button"
                class="flex min-h-10 w-full items-center gap-2 rounded-md border border-white/10 bg-surface-container px-3 py-2 text-left text-on-surface transition hover:bg-white/5"
                matTreeNodeToggle
                [attr.aria-label]="'Toggle ' + node.label"
              >
                <ee-symbol-icon
                  class="text-[20px]"
                  [name]="treeControl.isExpanded(node) ? 'expand_more' : 'chevron_right'"
                />
                <span class="min-w-0 flex-1 truncate font-mono text-sm">{{ node.label }}</span>
                @if (node.kind === 'table') {
                  <span class="ee-data shrink-0 text-outline">{{ tableMeta(node.table) }}</span>
                }
              </button>
              @if (treeControl.isExpanded(node)) {
                <div class="ml-5 grid gap-1 border-l border-white/10 pl-3">
                  <ng-container matTreeNodeOutlet></ng-container>
                </div>
              }
            </div>
          </mat-nested-tree-node>
        </mat-tree>

        @if (treeNodes().length === 0) {
          <p class="rounded-md border border-white/10 bg-surface-container px-3 py-2 text-sm text-on-surface-variant">
            No tables found.
          </p>
        }
      }
    </ee-slide-over>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminTableBrowserComponent {
  protected readonly open = signal(false);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly metadata = signal<AdminSqlMetadata | null>(null);
  protected readonly treeControl = new NestedTreeControl<TableTreeNode>((node) => childrenOf(node));
  protected readonly dataSource = new MatTreeNestedDataSource<TableTreeNode>();
  protected readonly treeNodes = computed(() => this.toTree(this.metadata()?.tables ?? []));

  private readonly service = inject(AdminSqlService);
  private readonly destroyRef = inject(DestroyRef);
  private loaded = false;

  protected readonly hasChild = (_: number, node: TableTreeNode): boolean =>
    childrenOf(node).length > 0;

  protected openPanel(): void {
    this.open.set(true);
    if (!this.loaded) {
      this.load();
    }
  }

  protected tableMeta(table: AdminSqlTable): string {
    const rows = table.tableRows === null || table.tableRows === undefined ? '-' : new Intl.NumberFormat().format(table.tableRows);
    return `${table.engine ?? '-'} · ${rows} rows`;
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service
      .getMetadata()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (metadata) => {
          this.loaded = true;
          this.metadata.set(metadata);
          this.dataSource.data = this.treeNodes();
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.error.set(readAdminSqlError(error));
          this.loading.set(false);
        },
      });
  }

  private toTree(tables: readonly AdminSqlTable[]): TableTreeNode[] {
    return tables.map((table) => ({
      kind: 'table',
      label: table.name,
      table,
      children: [
        {
          kind: 'group',
          label: `Columns (${table.columns.length})`,
          children: table.columns.map((column) => this.columnNode(column)),
        },
        {
          kind: 'group',
          label: `Indexes (${table.indexes.length})`,
          children: table.indexes.map((index) => this.indexNode(index)),
        },
      ],
    }));
  }

  private columnNode(column: AdminSqlColumn): DetailNode {
    const nullable = column.nullable ? 'nullable' : 'not null';
    const extras = [nullable, column.defaultValue ? `default ${column.defaultValue}` : null, column.extra || null]
      .filter(Boolean)
      .join(' · ');
    return {
      kind: 'detail',
      label: column.name,
      description: `${column.columnType || column.dataType}${extras ? ` · ${extras}` : ''}`,
    };
  }

  private indexNode(index: AdminSqlIndex): DetailNode {
    return {
      kind: 'detail',
      label: index.name,
      description: `${index.unique ? 'unique' : 'non-unique'} · ${index.columns.join(', ')}`,
    };
  }
}

function childrenOf(node: TableTreeNode): TableTreeNode[] {
  return 'children' in node ? [...node.children] : [];
}
