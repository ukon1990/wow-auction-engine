import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

export interface TabOption {
  readonly id: string;
  readonly label: string;
}

/**
 * Two tab styles.
 * `segmented`: picks one value that filters a list (pressed buttons).
 * `underline`: switches between panels on one page (ARIA tabs; give each panel `aria-labelledby` = `{idPrefix}-{tab id}`).
 */
@Component({
  selector: 'ee-tabs',
  host: { class: 'block max-w-full overflow-x-auto' },
  template: `
    @if (variant() === 'segmented') {
      <div
        class="inline-flex gap-1 rounded-lg border border-white/10 bg-surface-container-low p-1"
        role="group"
        [attr.aria-label]="label()"
      >
        @for (tab of tabs(); track tab.id) {
          <button
            type="button"
            class="h-9 whitespace-nowrap rounded-md px-3.5 text-sm font-medium transition"
            [class]="
              tab.id === activeId()
                ? 'bg-surface-container-highest text-primary'
                : 'text-on-surface-variant hover:bg-white/5 hover:text-on-surface'
            "
            [attr.aria-pressed]="tab.id === activeId()"
            (click)="selected.emit(tab.id)"
          >
            {{ tab.label }}
          </button>
        }
      </div>
    } @else {
      <div class="flex gap-4 border-b border-white/10" role="tablist" [attr.aria-label]="label()">
        @for (tab of tabs(); track tab.id) {
          <button
            type="button"
            role="tab"
            [id]="idPrefix() + '-' + tab.id"
            class="-mb-px whitespace-nowrap border-b-2 px-1 pb-2.5 pt-1 text-sm font-medium transition"
            [class]="
              tab.id === activeId()
                ? 'border-primary text-primary'
                : 'border-transparent text-on-surface-variant hover:text-on-surface'
            "
            [attr.aria-selected]="tab.id === activeId()"
            [attr.tabindex]="tab.id === activeId() ? 0 : -1"
            (click)="selected.emit(tab.id)"
            (keydown.arrowRight)="move(tab.id, 1)"
            (keydown.arrowLeft)="move(tab.id, -1)"
          >
            {{ tab.label }}
          </button>
        }
      </div>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TabsComponent {
  readonly tabs = input.required<readonly TabOption[]>();
  readonly activeId = input.required<string>();
  readonly label = input.required<string>();
  readonly variant = input<'segmented' | 'underline'>('segmented');
  readonly idPrefix = input('ee-tab');
  readonly selected = output<string>();

  protected move(fromId: string, step: number): void {
    const tabs = this.tabs();
    const index = tabs.findIndex((tab) => tab.id === fromId);
    const next = tabs[(index + step + tabs.length) % tabs.length];
    if (!next) return;
    this.selected.emit(next.id);
    document.getElementById(`${this.idPrefix()}-${next.id}`)?.focus();
  }
}
