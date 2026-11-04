import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { SymbolIconComponent } from '../primitives/symbol-icon.component';

export interface ActiveFilterChip {
  readonly id: string;
  readonly label: string;
}

/** One removable chip per active filter, with the result summary on the right. */
@Component({
  selector: 'ee-active-filter-chips',
  imports: [SymbolIconComponent],
  host: { class: 'flex flex-wrap items-center justify-between gap-x-4 gap-y-2' },
  template: `
    @if (chips().length) {
      <ul class="flex flex-wrap items-center gap-2" [attr.aria-label]="label()">
        @for (chip of chips(); track chip.id) {
          <li
            class="inline-flex h-8 items-center gap-1 rounded-full border border-white/10 bg-surface-container pl-3 pr-1 text-[13px] text-on-surface"
          >
            <span>{{ chip.label }}</span>
            <button
              type="button"
              class="inline-grid h-6 w-6 place-items-center rounded-full text-on-surface-variant transition hover:bg-surface-container-highest hover:text-on-surface"
              [attr.aria-label]="removeLabel() + ' ' + chip.label"
              (click)="removed.emit(chip.id)"
            >
              <ee-symbol-icon class="text-[14px]" name="close" />
            </button>
          </li>
        }
        @if (chips().length > 1) {
          <li>
            <button
              type="button"
              class="h-8 rounded-md px-2 text-[13px] font-medium text-primary transition hover:text-on-surface"
              (click)="clearAll.emit()"
              i18n="@@filters.clearAll"
            >
              Clear all
            </button>
          </li>
        }
      </ul>
    } @else {
      <span></span>
    }
    <p class="text-[13px] text-on-surface-variant" aria-live="polite">
      <ng-content />
    </p>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActiveFilterChipsComponent {
  readonly chips = input.required<readonly ActiveFilterChip[]>();
  readonly label = input('Active filters');
  readonly removeLabel = input('Remove filter');
  readonly removed = output<string>();
  readonly clearAll = output<void>();
}
