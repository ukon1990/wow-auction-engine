import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { FilterSection } from '../../models/ui-models';
import { QualityBadgeComponent } from '../primitives/quality-badge.component';
import { SymbolIconComponent } from '../primitives/symbol-icon.component';

@Component({
  selector: 'ee-filter-panel',
  imports: [QualityBadgeComponent, SymbolIconComponent],
  template: `
    <aside
      class="ee-glass hidden w-72 shrink-0 overflow-hidden rounded-lg lg:flex lg:flex-col"
      aria-label="Market filters"
    >
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
  readonly optionToggled = output<string>();
  readonly reset = output<void>();
}
