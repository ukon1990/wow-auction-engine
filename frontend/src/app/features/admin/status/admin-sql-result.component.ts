import { ChangeDetectionStrategy, Component, computed, effect, input, signal } from '@angular/core';
import { AdminSqlResult } from '@api/generated';
import { PaginationComponent, PaginationState } from '@ui';

const pageSizes = [25, 50, 100, 250] as const;

@Component({
  selector: 'app-admin-sql-result',
  imports: [PaginationComponent],
  template: `
    @if (result(); as result) {
      <section class="grid gap-3" aria-label="SQL result">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <p class="ee-data text-outline">
            {{ result.mode }} · {{ result.rowCount }} rows · {{ result.durationMs }} ms
            @if (result.truncated) {
              · truncated
            }
          </p>
        </div>

        @if (showEffectiveSql()) {
          <div class="grid gap-2">
            <p class="ee-label text-outline">Effective SQL</p>
            <pre
              class="max-h-40 overflow-auto whitespace-pre-wrap rounded-md border border-white/10 bg-surface-container p-3 font-mono text-xs text-on-surface"
              >{{ result.effectiveSql }}</pre
            >
          </div>
        }

        @if (jsonText(); as json) {
          <pre
            class="max-h-[28rem] overflow-auto whitespace-pre-wrap rounded-md border border-white/10 bg-surface-container p-3 font-mono text-xs text-on-surface"
            >{{ json }}</pre
          >
        } @else if (result.columns.length > 0) {
          <div class="grid gap-3">
            <div class="overflow-auto rounded-md border border-white/10">
              <table class="min-w-full border-collapse text-left text-xs text-on-surface">
                <thead class="bg-surface-container-high ee-label text-outline">
                  <tr>
                    @for (column of result.columns; track column; let i = $index) {
                      <th class="border-b border-white/10 px-3 py-2">
                        {{ column || 'Column ' + (i + 1) }}
                      </th>
                    }
                  </tr>
                </thead>
                <tbody class="divide-y divide-white/5">
                  @for (row of pagedRows(); track pageRowKey($index)) {
                    <tr class="align-top">
                      @for (value of row; track $index) {
                        <td class="max-w-[28rem] whitespace-pre-wrap px-3 py-2 font-mono">
                          {{ value ?? 'NULL' }}
                        </td>
                      }
                    </tr>
                  } @empty {
                    <tr>
                      <td
                        class="px-3 py-6 text-center text-on-surface-variant"
                        [attr.colspan]="result.columns.length"
                      >
                        No rows returned.
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>

            @if (result.rows.length > 0) {
              <div
                class="flex flex-wrap items-center justify-between gap-3 rounded-md border border-white/10 bg-surface-container px-3 py-2 text-sm text-on-surface"
              >
                <label class="ee-label flex items-center gap-2 text-outline">
                  Rows per page
                  <select
                    class="h-9 rounded-md border border-white/10 bg-surface-container-high px-2 text-sm text-on-surface outline-none focus:border-primary-container focus:ring-2 focus:ring-primary-container/40"
                    [value]="pageSize()"
                    (change)="updatePageSize($event)"
                  >
                    @for (size of pageSizeOptions; track size) {
                      <option [value]="size">{{ size }}</option>
                    }
                  </select>
                </label>
                <div class="min-w-[18rem] flex-1">
                  <ee-pagination
                    [pageState]="paginationState()"
                    rowLabel="rows"
                    (pageChange)="page.set($event)"
                  />
                </div>
              </div>
            }
          </div>
        } @else {
          <p
            class="rounded-md border border-white/10 bg-surface-container px-3 py-2 text-sm text-on-surface"
          >
            No result set returned.
          </p>
        }
      </section>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminSqlResultComponent {
  readonly result = input<AdminSqlResult | null>(null);
  readonly submittedSql = input('');

  protected readonly pageSizeOptions = pageSizes;
  protected readonly page = signal(0);
  protected readonly pageSize = signal<number>(50);

  constructor() {
    effect(() => {
      this.result();
      this.page.set(0);
    });
  }

  protected readonly showEffectiveSql = computed(() => {
    const result = this.result();
    return !!result && result.effectiveSql.trim() !== this.submittedSql().trim();
  });

  protected readonly paginationState = computed<PaginationState>(() => {
    const totalItems = this.result()?.rows.length ?? 0;
    const pageSize = this.pageSize();
    const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
    const page = Math.min(this.page(), totalPages - 1);
    return {
      page,
      pageSize,
      totalItems,
      totalPages,
    };
  });

  protected readonly pagedRows = computed(() => {
    const rows = this.result()?.rows ?? [];
    const state = this.paginationState();
    const start = state.page * state.pageSize;
    return rows.slice(start, start + state.pageSize);
  });

  protected readonly jsonText = computed(() => {
    const result = this.result();
    if (!result || result.columns.length !== 1 || result.rows.length !== 1) {
      return null;
    }
    const value = result.rows[0]?.[0]?.trim();
    if (!value || (!value.startsWith('{') && !value.startsWith('['))) {
      return null;
    }
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  });

  protected pageRowKey(index: number): number {
    return this.paginationState().page * this.paginationState().pageSize + index;
  }

  protected updatePageSize(event: Event): void {
    const input = event.target as HTMLSelectElement;
    this.pageSize.set(Number(input.value));
    this.page.set(0);
  }
}
