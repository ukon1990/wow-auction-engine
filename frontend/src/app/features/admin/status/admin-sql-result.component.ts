import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { AdminSqlResult } from '@api/generated';

@Component({
  selector: 'app-admin-sql-result',
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
          <div class="overflow-auto rounded-md border border-white/10">
            <table class="min-w-full border-collapse text-left text-xs text-on-surface">
              <thead class="bg-surface-container-high ee-label text-outline">
                <tr>
                  @for (column of result.columns; track column; let i = $index) {
                    <th class="border-b border-white/10 px-3 py-2">{{ column || 'Column ' + (i + 1) }}</th>
                  }
                </tr>
              </thead>
              <tbody class="divide-y divide-white/5">
                @for (row of result.rows; track $index) {
                  <tr class="align-top">
                    @for (value of row; track $index) {
                      <td class="max-w-[28rem] whitespace-pre-wrap px-3 py-2 font-mono">
                        {{ value ?? 'NULL' }}
                      </td>
                    }
                  </tr>
                } @empty {
                  <tr>
                    <td class="px-3 py-6 text-center text-on-surface-variant" [attr.colspan]="result.columns.length">
                      No rows returned.
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        } @else {
          <p class="rounded-md border border-white/10 bg-surface-container px-3 py-2 text-sm text-on-surface">
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

  protected readonly showEffectiveSql = computed(() => {
    const result = this.result();
    return !!result && result.effectiveSql.trim() !== this.submittedSql().trim();
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
}
