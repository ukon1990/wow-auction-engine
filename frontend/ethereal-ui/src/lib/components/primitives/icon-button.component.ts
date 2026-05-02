import { A11yModule } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { SymbolIconComponent, SymbolIconName } from './symbol-icon.component';

@Component({
  selector: 'ee-icon-button',
  imports: [A11yModule, SymbolIconComponent],
  template: `
    <button
      cdkMonitorElementFocus
      type="button"
      class="inline-flex h-10 w-10 items-center justify-center rounded-full border border-white/10 bg-white/0 text-primary transition hover:bg-white/5 hover:text-on-surface disabled:cursor-not-allowed disabled:opacity-45"
      [attr.aria-label]="label()"
      [disabled]="disabled()"
      (click)="pressed.emit()"
    >
      <ee-symbol-icon class="text-[22px]" [name]="icon()" />
    </button>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IconButtonComponent {
  readonly icon = input.required<SymbolIconName | string>();
  readonly label = input.required<string>();
  readonly disabled = input(false);
  readonly pressed = output<void>();
}
