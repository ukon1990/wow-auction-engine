import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'ee-glass-panel',
  template: `
    <section [class]="panelClass()" [attr.aria-label]="ariaLabel()">
      <ng-content />
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GlassPanelComponent {
  readonly ariaLabel = input<string | undefined>();
  readonly compact = input(false);

  protected panelClass(): string {
    const padding = this.compact() ? 'p-3' : 'p-inner-padding';
    return `ee-glass rounded-lg ${padding}`;
  }
}
