import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { Router } from '@angular/router';

import { NavItem } from '../../models/ui-models';
import { DropdownComponent } from '../primitives/dropdown.component';
import { SymbolIconComponent } from '../primitives/symbol-icon.component';
import { TopNavItemComponent } from './top-nav-item.component';

@Component({
  selector: 'ee-top-nav-dropdown-item',
  imports: [DropdownComponent, SymbolIconComponent, TopNavItemComponent],
  template: `
    <div class="relative">
      <ee-dropdown
        [open]="open()"
        [buttonClass]="triggerClass()"
        [ariaLabel]="item().label"
        (toggle)="toggle.emit()"
        (close)="close.emit()"
      >
        <ng-container eeDropdownTrigger>
          <ee-symbol-icon class="text-[18px]" [name]="item().icon" />
          <span>{{ item().label }}</span>
        </ng-container>
        <ng-container eeDropdownPanel>
          @for (child of item().children ?? []; track child.id) {
            <ee-top-nav-item
              [item]="child"
              [activeId]="activeId()"
              variant="dropdown"
              (selected)="onChildSelected($event)"
              (linkSelected)="close.emit()"
            />
          }
        </ng-container>
      </ee-dropdown>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TopNavDropdownItemComponent {
  private readonly router = inject(Router);

  readonly item = input.required<NavItem>();
  readonly activeId = input.required<string>();
  readonly open = input(false);
  readonly toggle = output<void>();
  readonly close = output<void>();
  readonly selected = output<string>();

  protected triggerClass(): string {
    const base =
      'inline-flex items-center gap-1.5 rounded px-1 py-2 font-cinzel text-sm font-bold uppercase tracking-wide transition hover:bg-white/5';
    return this.isActive()
      ? `${base} border-b-2 border-primary-container text-primary-container`
      : `${base} text-slate-400 hover:text-on-surface`;
  }

  protected onChildSelected(id: string): void {
    this.selected.emit(id);
    this.close.emit();
  }

  private isActive(): boolean {
    return this.item().id === this.activeId() || this.hasActiveChild() || this.hasActiveChildLink();
  }

  private hasActiveChild(): boolean {
    return Boolean(this.item().children?.some((child) => child.id === this.activeId()));
  }

  private hasActiveChildLink(): boolean {
    return Boolean(
      this.item().children?.some((child) => {
        const routerLink = child.routerLink;
        if (!routerLink) {
          return false;
        }
        const urlTree =
          typeof routerLink === 'string'
            ? this.router.parseUrl(routerLink)
            : this.router.createUrlTree([...routerLink]);
        return this.router.isActive(urlTree, {
          paths: 'exact',
          queryParams: 'ignored',
          fragment: 'ignored',
          matrixParams: 'ignored',
        });
      }),
    );
  }
}
