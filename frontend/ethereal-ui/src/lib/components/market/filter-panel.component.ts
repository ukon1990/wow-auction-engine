import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { qualityDotClass } from '../../helpers/quality';
import { FilterOption, FilterSection, ItemQuality } from '../../models/ui-models';

export type FilterRangeChanged = {
  sectionId: string;
  bound: 'min' | 'max';
  value: number | null;
};
export type FilterOptionChanged = {
  sectionId: string;
  optionId: string | null;
};

@Component({
  selector: 'ee-filter-panel',
  template: `
    <aside [class]="panelClass()" aria-label="Filters" i18n-aria-label="@@filters.aria">
      <div class="flex items-center justify-between gap-3 px-inner-padding pb-2 pt-inner-padding">
        <h2 class="text-base font-semibold text-on-surface" i18n="@@filters.title">Filters</h2>
        <button
          type="button"
          class="rounded px-1 text-sm font-medium text-primary transition hover:text-on-surface disabled:text-outline"
          [disabled]="!hasActiveFilters()"
          (click)="reset.emit()"
          i18n="@@filters.clearAll"
        >
          Clear all
        </button>
      </div>
      <div
        class="flex min-h-0 flex-1 flex-col gap-5 overflow-y-auto px-inner-padding pb-inner-padding"
      >
        @for (section of orderedSections(); track section.id) {
          <section>
            @if (section.type === 'boolean') {
              @for (option of section.options; track option.id) {
                <label class="flex cursor-pointer items-center justify-between gap-3 py-1">
                  <span class="text-sm text-on-surface">{{ section.label }}</span>
                  <input
                    type="checkbox"
                    class="h-4 w-4 accent-primary"
                    [checked]="option.selected"
                    (change)="optionToggled.emit(option.id)"
                  />
                </label>
              }
            } @else if (section.type === 'range') {
              <fieldset>
                <legend class="mb-1.5 ee-label text-outline">{{ section.label }}</legend>
                <div class="grid grid-cols-2 gap-2">
                  <label class="flex min-w-0 flex-col gap-1 text-xs text-outline">
                    <span i18n="@@filters.from">From</span>
                    <input
                      type="number"
                      class="h-10 min-w-0 rounded-md border border-white/10 bg-surface-container-lowest px-3 text-sm text-on-surface"
                      [value]="section.selectedMin ?? ''"
                      [attr.min]="section.min ?? null"
                      [attr.max]="section.max ?? null"
                      (change)="
                        rangeChanged.emit({
                          sectionId: section.id,
                          bound: 'min',
                          value: rangeValue($event),
                        })
                      "
                    />
                  </label>
                  <label class="flex min-w-0 flex-col gap-1 text-xs text-outline">
                    <span i18n="@@filters.to">To</span>
                    <input
                      type="number"
                      class="h-10 min-w-0 rounded-md border border-white/10 bg-surface-container-lowest px-3 text-sm text-on-surface"
                      [value]="section.selectedMax ?? ''"
                      [attr.min]="section.min ?? null"
                      [attr.max]="section.max ?? null"
                      (change)="
                        rangeChanged.emit({
                          sectionId: section.id,
                          bound: 'max',
                          value: rangeValue($event),
                        })
                      "
                    />
                  </label>
                </div>
              </fieldset>
            } @else if (section.type === 'select') {
              <label class="flex flex-col gap-1.5">
                <span class="ee-label text-outline">{{ section.label }}</span>
                <select
                  class="h-10 w-full rounded-md border border-white/10 bg-surface-container-lowest px-3 text-sm text-on-surface"
                  [value]="selectedOptionId(section)"
                  (change)="
                    optionSelected.emit({ sectionId: section.id, optionId: selectedValue($event) })
                  "
                >
                  <option value="" i18n="@@filters.anyOption">Any</option>
                  @for (option of section.options; track option.id) {
                    <option [value]="option.id" [selected]="option.selected">
                      {{ option.label }}
                    </option>
                  }
                </select>
              </label>
            } @else {
              <fieldset>
                <legend class="mb-1.5 ee-label text-outline">{{ section.label }}</legend>
                <div class="flex flex-col">
                  @for (option of section.options; track option.id) {
                    <label class="flex cursor-pointer items-center gap-3 py-1">
                      <input
                        type="checkbox"
                        class="h-4 w-4 shrink-0 accent-primary"
                        [checked]="option.selected"
                        (change)="optionToggled.emit(option.id)"
                      />
                      @if (option.quality) {
                        <span
                          class="h-2 w-2 shrink-0 rounded-full"
                          [class]="dotClass(option.quality)"
                          aria-hidden="true"
                        ></span>
                      }
                      <span class="text-sm text-on-surface">{{ option.label }}</span>
                    </label>
                  }
                </div>
              </fieldset>
            }
          </section>
        }
      </div>
    </aside>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FilterPanelComponent {
  readonly sections = input.required<readonly FilterSection[]>();
  readonly panelClass = input(
    'flex w-64 shrink-0 flex-col overflow-hidden rounded-lg border border-white/10 bg-surface-container-low',
  );
  readonly optionToggled = output<string>();
  readonly optionSelected = output<FilterOptionChanged>();
  readonly rangeChanged = output<FilterRangeChanged>();
  readonly reset = output<void>();

  /** Lists first, then ranges, then yes/no switches. */
  protected readonly orderedSections = computed(() => {
    const rank = (section: FilterSection) =>
      section.type === 'range' ? 1 : section.type === 'boolean' ? 2 : 0;
    return [...this.sections()].sort((left, right) => rank(left) - rank(right));
  });

  protected hasActiveFilters(): boolean {
    return this.sections().some(
      (section) =>
        section.options.some((option) => option.selected) ||
        section.selectedMin != null ||
        section.selectedMax != null,
    );
  }

  protected dotClass(quality: ItemQuality): string {
    return `h-2 w-2 shrink-0 rounded-full ${qualityDotClass(quality)}`;
  }

  protected selectedOptionId(section: FilterSection): string {
    return section.options.find((option: FilterOption) => option.selected)?.id ?? '';
  }

  protected selectedValue(event: Event): string | null {
    const value = (event.target as HTMLSelectElement).value;
    return value === '' ? null : value;
  }

  protected rangeValue(event: Event): number | null {
    const value = (event.target as HTMLInputElement).value;
    return value === '' ? null : Number(value);
  }
}
