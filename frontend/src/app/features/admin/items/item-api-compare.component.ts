import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { AdminItemApiCompareResponse } from '@api/generated';

@Component({
  selector: 'app-item-api-compare',
  template: `
    @if (compare(); as result) {
      <section class="grid gap-3" aria-label="Blizzard API comparison">
        <h3 class="font-semibold text-on-surface">Blizzard API comparison</h3>
        <div class="overflow-x-auto rounded-md border border-white/10">
          <table class="min-w-full text-sm">
            <thead class="bg-white/5 text-left text-outline">
              <tr>
                <th class="px-3 py-2">Field</th>
                <th class="px-3 py-2">Base</th>
                <th class="px-3 py-2">Override</th>
                <th class="px-3 py-2">API</th>
                <th class="px-3 py-2">Effective</th>
              </tr>
            </thead>
            <tbody>
              @for (entry of entries(result); track entry.field) {
                <tr class="border-t border-white/10">
                  <td class="px-3 py-2 font-medium text-on-surface">{{ entry.field }}</td>
                  <td class="px-3 py-2 text-outline">{{ formatValue(entry.values.base) }}</td>
                  <td class="px-3 py-2 text-outline">{{ formatValue(entry.values.override) }}</td>
                  <td class="px-3 py-2 text-outline">{{ formatValue(entry.values.api) }}</td>
                  <td class="px-3 py-2 text-on-surface">{{ formatValue(entry.values.effective) }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ItemApiCompareComponent {
  readonly compare = input<AdminItemApiCompareResponse | null>(null);

  protected entries(result: AdminItemApiCompareResponse) {
    return Object.entries(result.fields ?? {}).map(([field, values]) => ({ field, values }));
  }

  protected formatValue(value: unknown): string {
    if (value === null || value === undefined) {
      return '—';
    }
    return String(value);
  }
}
