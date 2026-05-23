import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { NavItem } from '../../models/ui-models';
import { SymbolIconComponent } from '../primitives/symbol-icon.component';

export type TopNavItemVariant = 'top' | 'dropdown';

@Component({
  selector: 'ee-top-nav-item',
  imports: [RouterLink, RouterLinkActive, SymbolIconComponent],
  template: `
    @if (item().routerLink) {
      <a
        [routerLink]="item().routerLink"
        routerLinkActive
        [routerLinkActiveOptions]="{ exact: true }"
        #routeActive="routerLinkActive"
        [class]="itemClass(routeActive.isActive)"
        [attr.role]="variant() === 'dropdown' ? 'menuitem' : null"
        [attr.aria-current]="isActive(routeActive.isActive) ? 'page' : null"
        (click)="linkSelected.emit()"
      >
        @if (showIcon()) {
          <ee-symbol-icon class="text-[18px]" [name]="item().icon" />
        }
        <span [class.whitespace-nowrap]="variant() === 'dropdown'">{{ item().label }}</span>
      </a>
    } @else {
      <button
        type="button"
        [class]="itemClass(false)"
        [attr.role]="variant() === 'dropdown' ? 'menuitem' : null"
        (click)="selected.emit(item().id)"
      >
        @if (showIcon()) {
          <ee-symbol-icon class="text-[18px]" [name]="item().icon" />
        }
        <span [class.whitespace-nowrap]="variant() === 'dropdown'">{{ item().label }}</span>
      </button>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TopNavItemComponent {
  readonly item = input.required<NavItem>();
  readonly activeId = input.required<string>();
  readonly variant = input<TopNavItemVariant>('top');
  readonly selected = output<string>();
  readonly linkSelected = output<void>();

  protected showIcon(): boolean {
    return this.variant() === 'dropdown' || !this.item().routerLink;
  }

  protected isActive(routerActive: boolean): boolean {
    return this.item().id === this.activeId() || routerActive;
  }

  protected itemClass(routerActive: boolean): string {
    return this.variant() === 'dropdown'
      ? this.dropdownItemClass(routerActive)
      : this.topItemClass(routerActive);
  }

  private topItemClass(routerActive: boolean): string {
    const base =
      'inline-flex items-center gap-1.5 rounded px-1 py-2 font-cinzel text-sm font-bold uppercase tracking-wide transition hover:bg-white/5';
    return this.isActive(routerActive)
      ? `${base} border-b-2 border-primary-container text-primary-container`
      : `${base} text-slate-400 hover:text-on-surface`;
  }

  private dropdownItemClass(routerActive: boolean): string {
    const base =
      'flex w-full items-center gap-3 px-4 py-3 text-left font-cinzel text-sm font-bold uppercase tracking-wide transition hover:bg-white/5 hover:text-on-surface';
    return this.isActive(routerActive)
      ? `${base} bg-yellow-500/10 text-primary-container`
      : `${base} text-slate-300`;
  }
}
