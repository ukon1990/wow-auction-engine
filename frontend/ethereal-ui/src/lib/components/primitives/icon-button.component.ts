import { A11yModule } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';

import { SymbolIconComponent, SymbolIconName } from './symbol-icon.component';

@Component({
  selector: 'ee-icon-button',
  imports: [A11yModule, SymbolIconComponent],
  template: `
    <span
      class="inline-flex"
      [attr.title]="tooltip() || label()"
      (mouseenter)="showTooltip($event)"
      (mouseleave)="hideTooltip()"
    >
      <button
        cdkMonitorElementFocus
        type="button"
        class="inline-flex h-10 w-10 items-center justify-center rounded-full border border-white/10 bg-white/0 text-primary transition hover:bg-white/5 hover:text-on-surface disabled:cursor-not-allowed disabled:opacity-45"
        [attr.aria-label]="label()"
        [attr.aria-describedby]="tooltipText() ? tooltipId : null"
        [attr.title]="tooltip() || label()"
        [disabled]="disabled()"
        (blur)="hideTooltip()"
        (click)="pressed.emit()"
        (focus)="showTooltip($event)"
      >
        <ee-symbol-icon class="text-[22px]" [name]="icon()" />
      </button>
    </span>
    @if (visibleTooltip(); as tip) {
      <span
        [id]="tooltipId"
        role="tooltip"
        class="pointer-events-none fixed z-50 max-w-64 rounded-md border border-white/10 bg-surface-container-highest px-3 py-2 text-xs font-medium leading-snug text-on-surface shadow-xl"
        [style.left.px]="tip.left"
        [style.top.px]="tip.top"
      >
        {{ tip.text }}
      </span>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IconButtonComponent {
  readonly icon = input.required<SymbolIconName | string>();
  readonly label = input.required<string>();
  readonly tooltip = input('');
  readonly disabled = input(false);
  readonly pressed = output<void>();

  protected readonly tooltipId = `ee-icon-button-tooltip-${nextTooltipId++}`;
  protected readonly tooltipPosition = signal<{ left: number; top: number } | null>(null);
  protected readonly tooltipText = computed(() => this.tooltip() || this.label());
  protected readonly visibleTooltip = computed(() => {
    const position = this.tooltipPosition();
    const text = this.tooltipText();
    return position && text ? { ...position, text } : null;
  });

  protected showTooltip(event: Event): void {
    const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
    this.tooltipPosition.set({
      left: Math.min(window.innerWidth - 272, Math.max(8, rect.left + rect.width / 2 - 128)),
      top: Math.max(8, rect.top - 44),
    });
  }

  protected hideTooltip(): void {
    this.tooltipPosition.set(null);
  }
}

let nextTooltipId = 0;
