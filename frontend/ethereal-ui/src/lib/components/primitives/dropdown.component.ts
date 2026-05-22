import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  effect,
  ElementRef,
  HostListener,
  inject,
  Injector,
  input,
  output,
} from '@angular/core';

import { SymbolIconComponent } from './symbol-icon.component';

@Component({
  selector: 'ee-dropdown',
  imports: [SymbolIconComponent],
  template: `
    <button
      #triggerButton
      type="button"
      [class]="buttonClass()"
      [attr.aria-label]="ariaLabel() || null"
      [attr.aria-expanded]="open()"
      [attr.aria-haspopup]="ariaHasPopup()"
      (click)="toggle.emit()"
      (keydown)="onTriggerKeydown($event)"
      (focusout)="onFocusOut($event)"
    >
      <ng-content select="[eeDropdownTrigger]" />
      <ee-symbol-icon
        class="text-[18px]"
        [name]="open() ? 'keyboard_arrow_up' : 'keyboard_arrow_down'"
      />
    </button>
    <div
      #menuPanel
      [class]="panelClass()"
      [class.hidden]="!open()"
      [attr.aria-hidden]="!open()"
      role="menu"
      (keydown)="onPanelKeydown($event)"
      (focusout)="onFocusOut($event)"
    >
      <ng-content select="[eeDropdownPanel]" />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DropdownComponent {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  readonly open = input(false);
  readonly buttonClass = input(
    'inline-flex items-center gap-1.5 rounded px-1 py-2 font-cinzel text-sm font-bold uppercase tracking-wide transition hover:bg-white/5',
  );
  readonly panelClass = input(
    'absolute left-0 top-full z-[70] mt-2 min-w-52 overflow-hidden rounded border border-white/10 bg-slate-950/95 py-1 shadow-[0_12px_32px_rgba(0,0,0,0.55)] backdrop-blur-xl',
  );
  readonly ariaLabel = input('');
  readonly ariaHasPopup = input('menu');
  readonly toggle = output<void>();
  readonly close = output<void>();

  constructor() {
    effect(() => {
      if (this.open()) {
        return;
      }
      const panel = this.host.nativeElement.querySelector<HTMLElement>('[role="menu"]');
      const active = this.host.nativeElement.ownerDocument.activeElement;
      if (panel && active instanceof Node && panel.contains(active)) {
        this.focusTrigger();
      }
    });
  }

  @HostListener('document:click', ['$event'])
  protected onDocumentClick(event: MouseEvent): void {
    if (!this.open()) {
      return;
    }

    const target = event.target;
    if (target instanceof Node && this.host.nativeElement.contains(target)) {
      return;
    }

    this.close.emit();
  }

  protected onTriggerKeydown(event: KeyboardEvent): void {
    if (event.key === 'ArrowDown' || event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      if (!this.open()) {
        this.toggle.emit();
      }
      this.focusMenuItem('first');
      return;
    }

    if (event.key === 'ArrowUp') {
      event.preventDefault();
      if (!this.open()) {
        this.toggle.emit();
      }
      this.focusMenuItem('last');
      return;
    }

    if (event.key === 'Escape' && this.open()) {
      event.preventDefault();
      this.close.emit();
    }
  }

  protected onPanelKeydown(event: KeyboardEvent): void {
    if (!this.open()) {
      return;
    }

    if (event.key === 'Escape') {
      event.preventDefault();
      this.close.emit();
      this.focusTrigger();
      return;
    }

    if (event.key === 'Tab') {
      this.close.emit();
      return;
    }

    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.focusAdjacentMenuItem(1);
      return;
    }

    if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.focusAdjacentMenuItem(-1);
      return;
    }

    if (event.key === 'Home') {
      event.preventDefault();
      this.focusMenuItem('first');
      return;
    }

    if (event.key === 'End') {
      event.preventDefault();
      this.focusMenuItem('last');
    }
  }

  protected onFocusOut(event: FocusEvent): void {
    if (!this.open()) {
      return;
    }

    const next = event.relatedTarget;
    if (next instanceof Node && this.host.nativeElement.contains(next)) {
      return;
    }

    this.close.emit();
  }

  private focusMenuItem(position: 'first' | 'last'): void {
    afterNextRender(
      () => {
        const items = this.menuItems();
        const next = position === 'first' ? items[0] : items[items.length - 1];
        next?.focus();
      },
      { injector: this.injector },
    );
  }

  private focusAdjacentMenuItem(offset: 1 | -1): void {
    const items = this.menuItems();
    if (!items.length) {
      return;
    }

    const active = this.host.nativeElement.ownerDocument.activeElement;
    const index = active instanceof HTMLElement ? items.indexOf(active) : -1;
    const nextIndex = index === -1 ? 0 : (index + offset + items.length) % items.length;
    items[nextIndex]?.focus();
  }

  private focusTrigger(): void {
    const trigger = this.host.nativeElement.querySelector<HTMLButtonElement>(':scope > button');
    trigger?.focus();
  }

  private menuItems(): HTMLElement[] {
    const panel = this.host.nativeElement.querySelector<HTMLElement>('[role="menu"]');
    if (!panel) {
      return [];
    }

    return Array.from(
      panel.querySelectorAll<HTMLElement>(
        '[role="menuitem"]:not([disabled]), a[href]:not([disabled]), button:not([disabled])',
      ),
    ).filter((item) => item.offsetParent !== null && item.getAttribute('aria-hidden') !== 'true');
  }
}
