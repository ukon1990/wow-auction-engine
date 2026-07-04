import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { AdminItemCompareField, AdminItemCompareResponse } from '@api/generated';

type CompareRow = {
  readonly key: string;
  readonly value: AdminItemCompareField;
};

@Component({
  selector: 'app-admin-item-compare-panel',
  template: `
    <div class="grid gap-4">
      @if (loading()) {
        <p class="ee-data text-outline" role="status">Comparing with Blizzard API...</p>
      } @else if (error()) {
        <p
          class="rounded-md border border-error/30 bg-error/10 px-3 py-2 text-sm text-error"
          role="alert"
        >
          {{ error() }}
        </p>
      } @else if (compare(); as result) {
        <p class="ee-data text-outline"><span>Item</span> #{{ result.itemId }}</p>
        <div class="overflow-x-auto rounded-md border border-white/10">
          <table class="min-w-full divide-y divide-white/10 text-sm">
            <thead class="bg-surface-container-high text-left ee-label text-outline">
              <tr>
                <th class="px-3 py-2">Field</th>
                <th class="px-3 py-2">Base</th>
                <th class="px-3 py-2">Override</th>
                <th class="px-3 py-2">API</th>
                <th class="px-3 py-2">Effective</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-white/5">
              @for (row of rows(); track row.key) {
                <tr>
                  <th class="px-3 py-2 text-left font-semibold text-on-surface">
                    {{ row.key }}
                  </th>
                  <td class="max-w-48 px-3 py-2 text-outline">{{ format(row.value.base) }}</td>
                  <td class="max-w-48 px-3 py-2 text-outline">
                    {{ format(row.value.override) }}
                  </td>
                  <td class="max-w-48 px-3 py-2 text-outline">{{ format(row.value.api) }}</td>
                  <td class="max-w-48 px-3 py-2 text-on-surface">
                    {{ format(row.value.effective) }}
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td class="px-3 py-6 text-center text-outline" colspan="5">
                    <span>No comparable fields returned.</span>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else {
        <p class="ee-data text-outline">Run compare to inspect Blizzard API differences.</p>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminItemComparePanelComponent {
  readonly compare = input<AdminItemCompareResponse | null>(null);
  readonly loading = input(false);
  readonly error = input<string | null>(null);

  protected readonly rows = computed<CompareRow[]>(() => {
    const fields = this.compare()?.fields ?? {};
    return Object.entries(fields)
      .map(([key, value]) => ({ key, value }))
      .sort((left, right) => left.key.localeCompare(right.key));
  });

  protected format(value: object | null | undefined): string {
    if (value === undefined || value === null) return '—';
    if (typeof value === 'string') return value;
    if (typeof value === 'number' || typeof value === 'boolean') return String(value);
    return JSON.stringify(value);
  }
}
