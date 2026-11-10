import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { CdkMenu, CdkMenuItem, CdkMenuTrigger } from '@angular/cdk/menu';

import { ButtonDirective } from './button.directive';
import { SymbolIconComponent } from './symbol-icon.component';

export interface RowAction {
  readonly id: string;
  readonly label: string;
  readonly disabled?: boolean;
  /** Shown in red and placed last, after a divider. */
  readonly danger?: boolean;
}

/** The main row action as a visible button, the rest in a "More actions" menu. */
@Component({
  selector: 'ee-row-actions-menu',
  imports: [ButtonDirective, CdkMenu, CdkMenuItem, CdkMenuTrigger, SymbolIconComponent],
  host: { class: 'flex items-center justify-end gap-1' },
  template: `
    @if (primaryLabel()) {
      <button
        eeButton
        size="sm"
        type="button"
        [attr.aria-label]="primaryAriaLabel() || null"
        (click)="primary.emit()"
      >
        {{ primaryLabel() }}
      </button>
    }
    @if (actions().length) {
      <button
        type="button"
        class="inline-grid h-8 w-8 place-items-center rounded-md border border-white/10 text-on-surface-variant transition hover:bg-surface-container-high hover:text-on-surface"
        [cdkMenuTriggerFor]="menu"
        [attr.aria-label]="menuLabel()"
      >
        <ee-symbol-icon class="text-[18px]" name="more_horiz" />
      </button>
    }
    <ng-template #menu>
      <div
        cdkMenu
        class="min-w-52 rounded-lg border border-white/10 bg-surface-container-high p-1 text-sm text-on-surface shadow-[0_12px_32px_rgba(0,0,0,0.55)]"
      >
        @for (action of actions(); track action.id) {
          @if (action.danger && !$first) {
            <div class="my-1 border-t border-white/10" role="separator"></div>
          }
          <button
            cdkMenuItem
            type="button"
            class="block w-full rounded-md px-3 py-2 text-left transition hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-40"
            [class.text-error]="action.danger"
            [disabled]="action.disabled ?? false"
            (cdkMenuItemTriggered)="actionSelected.emit(action.id)"
          >
            {{ action.label }}
          </button>
        }
      </div>
    </ng-template>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RowActionsMenuComponent {
  readonly primaryLabel = input('');
  readonly primaryAriaLabel = input('');
  readonly actions = input<readonly RowAction[]>([]);
  readonly menuLabel = input('More actions');
  readonly primary = output<void>();
  readonly actionSelected = output<string>();
}
