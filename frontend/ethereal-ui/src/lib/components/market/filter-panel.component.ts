import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { FilterOption, FilterSection } from '../../models/ui-models';
import { QualityBadgeComponent } from '../primitives/quality-badge.component';
import { SymbolIconComponent } from '../primitives/symbol-icon.component';

@Component({
  selector: 'ee-filter-panel',
  imports: [QualityBadgeComponent, SymbolIconComponent],
  template: `
    <aside [class]="panelClass()" aria-label="Market filters">
      <div class="border-b border-white/10 bg-surface-container-high p-inner-padding">
        <h2 class="ee-section-heading flex items-center gap-2 text-primary">
          <ee-symbol-icon class="text-lg" name="filter_alt" />
          Deep Filters
        </h2>
      </div>
      <div class="flex flex-col gap-6 overflow-y-auto p-inner-padding">
        @for (section of sections(); track section.id) {
          <section>
            <h3 class="mb-3 ee-label text-on-surface-variant">{{ section.label }}</h3>
            @if (section.type === 'range') {
              <div class="grid grid-cols-2 gap-2 pl-2">
                <input
                  type="number"
                  class="min-w-0 rounded border border-white/10 bg-surface px-2 py-2 text-sm text-on-surface"
                  placeholder="Min"
                  [value]="section.selectedMin ?? ''"
                  [attr.min]="section.min ?? null"
                  [attr.max]="section.max ?? null"
                  (change)="
                    rangeChanged.emit({ id: section.id, bound: 'min', value: rangeValue($event) })
                  "
                />
                <input
                  type="number"
                  class="min-w-0 rounded border border-white/10 bg-surface px-2 py-2 text-sm text-on-surface"
                  placeholder="Max"
                  [value]="section.selectedMax ?? ''"
                  [attr.min]="section.min ?? null"
                  [attr.max]="section.max ?? null"
                  (change)="
                    rangeChanged.emit({ id: section.id, bound: 'max', value: rangeValue($event) })
                  "
                />
              </div>
            } @else {
              @if (section.type === 'select') {
                <select
                  class="w-full rounded-lg border border-white/10 bg-surface-container-highest px-3 py-2 font-inter text-sm text-on-surface transition focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                  [attr.aria-label]="section.label"
                  [value]="selectedOptionId(section)"
                  (change)="
                    optionSelected.emit({ sectionId: section.id, optionId: selectedValue($event) })
                  "
                >
                  <option value="">All {{ section.label }}</option>
                  @for (option of section.options; track option.id) {
                    <option [value]="option.id">{{ option.label }}</option>
                  }
                </select>
              } @else {
                <div class="flex flex-col gap-2 pl-2">
                  @for (option of section.options; track option.id) {
                    <label class="flex cursor-pointer items-center gap-3">
                      <input
                        type="checkbox"
                        class="h-4 w-4 rounded border-white/20 bg-surface text-primary accent-primary"
                        [checked]="option.selected"
                        (change)="optionToggled.emit(option.id)"
                      />
                      @if (option.quality) {
                        <ee-quality-badge [quality]="option.quality" />
                      } @else {
                        <span class="text-sm text-outline transition hover:text-on-surface">{{
                          option.label
                        }}</span>
                      }
                    </label>
                  }
                </div>
              }
            }
          </section>
        }
      </div>
      <div class="mt-auto border-t border-white/10 bg-surface-container-low p-inner-padding">
        <button
          type="button"
          class="w-full rounded border border-white/10 bg-surface py-2 ee-label text-outline transition hover:bg-surface-container-high hover:text-on-surface"
          (click)="reset.emit()"
        >
          Reset Filters
        </button>
      </div>
    </aside>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FilterPanelComponent {
  readonly sections = input.required<readonly FilterSection[]>();
  readonly panelClass = input('ee-glass flex w-72 shrink-0 flex-col overflow-hidden rounded-lg');
  readonly optionToggled = output<string>();
  readonly optionSelected = output<{ sectionId: string; optionId: string | null }>();
  readonly rangeChanged = output<{ id: string; bound: 'min' | 'max'; value: number | null }>();
  readonly reset = output<void>();

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
