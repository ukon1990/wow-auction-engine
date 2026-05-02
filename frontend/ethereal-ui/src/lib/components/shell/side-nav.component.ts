import { CdkTrapFocus } from '@angular/cdk/a11y';
import { isPlatformBrowser, NgTemplateOutlet } from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  DOCUMENT,
  effect,
  ElementRef,
  inject,
  input,
  output,
  PLATFORM_ID,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { fromEvent } from 'rxjs';

import { CharacterSummary, NavItem } from '../../models/ui-models';
import { SymbolIconComponent } from '../primitives/symbol-icon.component';

@Component({
  selector: 'ee-side-nav',
  imports: [SymbolIconComponent, RouterLink, RouterLinkActive, NgTemplateOutlet, CdkTrapFocus],
  template: `
    @if (mobileOpen() && !isDesktop()) {
      <div
        class="fixed inset-0 z-40 bg-black/50 md:hidden"
        aria-hidden="true"
        (click)="closeMobile()"
      ></div>
    }

    @if (mobileOpen() && !isDesktop()) {
      <div
        id="ee-side-nav-drawer"
        class="fixed top-16 left-0 z-50 flex h-[calc(100vh-4rem)] w-[min(20rem,85vw)] flex-col border-r border-white/10 bg-slate-950/95 shadow-xl backdrop-blur-2xl md:hidden"
        role="dialog"
        aria-modal="true"
        aria-labelledby="ee-side-nav-drawer-title"
        cdkTrapFocus
        [cdkTrapFocusAutoCapture]="true"
      >
        <div class="flex items-center justify-between border-b border-white/10 px-4 py-3">
          <span id="ee-side-nav-drawer-title" class="ee-label text-on-surface">Menu</span>
          <button
            #drawerCloseBtn
            type="button"
            class="inline-flex h-10 w-10 items-center justify-center rounded-full border border-white/10 text-on-surface transition hover:bg-white/5"
            aria-label="Close menu"
            (click)="closeMobile()"
          >
            <ee-symbol-icon class="text-[22px]" name="close" />
          </button>
        </div>
        <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
          <ng-container *ngTemplateOutlet="railBody" />
        </div>
      </div>
    }

    <aside [class]="asideClass()" aria-label="Profession navigation">
      <ng-container *ngTemplateOutlet="railBody" />
    </aside>

    <ng-template #railBody>
      <div class="border-b border-white/10 p-6" [class]="headerPaddingClass()">
        @if (primaryNavItems().length) {
          <nav class="mb-4 border-b border-white/10 pb-4 md:hidden" aria-label="Primary navigation">
            @for (item of primaryNavItems(); track item.id) {
              <ng-container *ngTemplateOutlet="primaryRow; context: { $implicit: item }" />
            }
          </nav>
        }
        <div [class]="characterRowClass()">
          <div
            class="flex h-12 w-12 shrink-0 items-center justify-center rounded-full border border-outline/30 bg-surface-container-high"
          >
            <ee-symbol-icon class="text-2xl text-primary" name="swords" />
          </div>
          @if (!collapsed() || !isDesktop()) {
            <div class="min-w-0">
              <h2 class="ee-section-heading text-on-surface">Professions</h2>
              <p class="ee-data mt-1 text-outline">{{ character().skill }}</p>
            </div>
          }
        </div>
        @if (collapsed() && isDesktop()) {
          <div class="group relative mt-3 flex justify-center">
            <button
              type="button"
              class="inline-flex h-10 w-10 items-center justify-center rounded border border-outline/30 bg-surface-container text-on-surface transition hover:bg-surface-container-high"
              [attr.aria-label]="'Switch character'"
              (click)="switchCharacter.emit()"
            >
              <ee-symbol-icon class="text-[20px]" name="person" />
            </button>
            <span
              class="pointer-events-none absolute top-1/2 left-full z-[60] ml-2 -translate-y-1/2 rounded border border-white/10 bg-slate-900/95 px-2 py-1 ee-label text-on-surface opacity-0 shadow-lg transition group-hover:opacity-100 group-focus-within:opacity-100"
              role="tooltip"
            >
              Switch Character
            </span>
          </div>
        } @else {
          <button
            type="button"
            class="mt-4 w-full rounded border border-outline/30 bg-surface-container px-4 py-2 ee-label text-on-surface transition hover:bg-surface-container-high"
            (click)="switchCharacter.emit()"
          >
            Switch Character
          </button>
        }
        <button
          type="button"
          class="mt-3 hidden w-full items-center justify-center rounded border border-outline/30 bg-surface-container/50 px-2 py-2 text-on-surface transition hover:bg-surface-container md:flex"
          [attr.aria-label]="collapsed() ? 'Expand sidebar' : 'Collapse sidebar'"
          (click)="toggleCollapsed()"
        >
          <ee-symbol-icon
            class="text-[20px]"
            [name]="collapsed() ? 'chevron_right' : 'chevron_left'"
          />
        </button>
      </div>
      <nav class="flex-1 overflow-y-auto py-4" aria-label="Professions">
        @for (item of items(); track item.id) {
          <ng-container *ngTemplateOutlet="professionRow; context: { $implicit: item }" />
        }
      </nav>
      <div class="border-t border-white/10 p-4" [class]="footerPaddingClass()">
        <ng-container *ngTemplateOutlet="footerAction; context: { $implicit: settingsCtx }" />
        <ng-container *ngTemplateOutlet="footerAction; context: { $implicit: supportCtx }" />
      </div>
    </ng-template>

    <ng-template #primaryRow let-item>
      @if (item.routerLink) {
        <a
          [routerLink]="item.routerLink"
          [class]="primaryRowClass(item.id, true)"
          routerLinkActive="border-l-4 border-primary-container bg-yellow-500/10 text-primary-container ee-arcane-glow"
          [routerLinkActiveOptions]="{ exact: true }"
          [attr.aria-current]="item.id === activePrimaryId() ? 'page' : null"
          (click)="onMobileNavInteract()"
        >
          <ee-symbol-icon class="text-[20px]" [name]="item.icon" />
          <span>{{ item.label }}</span>
        </a>
      } @else {
        <button
          type="button"
          [class]="primaryRowClass(item.id, false)"
          (click)="onPrimaryButton(item.id)"
        >
          <ee-symbol-icon class="text-[20px]" [name]="item.icon" />
          <span>{{ item.label }}</span>
        </button>
      }
    </ng-template>

    <ng-template #professionRow let-item>
      <div class="group relative px-0">
        @if (item.routerLink) {
          <a
            [routerLink]="item.routerLink"
            [class]="professionRowClass(item.id, true)"
            routerLinkActive="border-r-4 border-primary-container bg-yellow-500/10 text-primary-container ee-arcane-glow"
            [routerLinkActiveOptions]="{ exact: true }"
            [attr.aria-current]="item.id === activeId() ? 'page' : null"
            [attr.aria-label]="professionLinkAriaLabel(item)"
            (click)="onMobileNavInteract()"
          >
            <ee-symbol-icon class="text-[20px]" [name]="item.icon" />
            @if (!collapsed() || !isDesktop()) {
              <span>{{ item.label }}</span>
            }
          </a>
        } @else {
          <button
            type="button"
            [class]="professionRowClass(item.id, false)"
            [attr.aria-label]="professionButtonAriaLabel(item)"
            (click)="onProfessionButton(item.id)"
          >
            <ee-symbol-icon class="text-[20px]" [name]="item.icon" />
            @if (!collapsed() || !isDesktop()) {
              <span>{{ item.label }}</span>
            }
          </button>
        }
        @if (collapsed() && isDesktop()) {
          <span
            class="pointer-events-none absolute top-1/2 left-full z-[60] ml-2 -translate-y-1/2 whitespace-nowrap rounded border border-white/10 bg-slate-900/95 px-2 py-1 ee-label text-on-surface opacity-0 shadow-lg transition group-hover:opacity-100 group-focus-within:opacity-100"
            role="tooltip"
          >
            {{ item.label }}
          </span>
        }
      </div>
    </ng-template>

    <ng-template #footerAction let-ctx>
      <div class="group relative mb-1 last:mb-0">
        @if (collapsed() && isDesktop()) {
          <button
            type="button"
            [class]="footerBtnClass(true)"
            [attr.aria-label]="ctx.label"
            (click)="ctx.onClick?.()"
          >
            <ee-symbol-icon class="text-[18px]" [name]="ctx.icon" />
          </button>
          <span
            class="pointer-events-none absolute top-1/2 left-full z-[60] ml-2 -translate-y-1/2 whitespace-nowrap rounded border border-white/10 bg-slate-900/95 px-2 py-1 font-space-mono text-xs uppercase text-on-surface opacity-0 shadow-lg transition group-hover:opacity-100 group-focus-within:opacity-100"
            role="tooltip"
          >
            {{ ctx.label }}
          </span>
        } @else {
          <button type="button" [class]="footerBtnClass(false)" (click)="ctx.onClick?.()">
            <ee-symbol-icon class="text-[18px]" [name]="ctx.icon" />
            {{ ctx.label }}
          </button>
        }
      </div>
    </ng-template>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SideNavComponent {
  private readonly document = inject(DOCUMENT);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly destroyRef = inject(DestroyRef);

  readonly items = input.required<readonly NavItem[]>();
  readonly activeId = input.required<string>();
  readonly character = input.required<CharacterSummary>();
  readonly primaryNavItems = input<readonly NavItem[]>([]);
  readonly activePrimaryId = input('');
  readonly mobileOpen = input(false);
  readonly mobileOpenChange = output<boolean>();

  readonly selected = output<string>();
  readonly primarySelected = output<string>();
  readonly switchCharacter = output<void>();

  protected readonly collapsed = signal(false);
  protected readonly isDesktop = signal(true);

  private readonly drawerCloseBtn = viewChild<ElementRef<HTMLButtonElement>>('drawerCloseBtn');

  protected readonly settingsCtx = {
    icon: 'settings' as const,
    label: 'Settings',
    onClick: (): void => undefined,
  };

  protected readonly supportCtx = {
    icon: 'help' as const,
    label: 'Support',
    onClick: (): void => undefined,
  };

  protected readonly asideClass = computed(() => {
    const narrow = this.collapsed() && this.isDesktop();
    const width = narrow ? 'w-[4.5rem]' : 'w-64';
    return `hidden h-[calc(100vh-64px)] ${width} shrink-0 flex-col border-r border-white/10 bg-slate-950/40 backdrop-blur-2xl transition-[width] duration-200 ease-out md:flex`;
  });

  constructor() {
    if (isPlatformBrowser(this.platformId) && typeof window.matchMedia === 'function') {
      const mq = window.matchMedia('(min-width: 768px)');
      this.isDesktop.set(mq.matches);
      const onMq = () => {
        const desktop = mq.matches;
        this.isDesktop.set(desktop);
        if (desktop && this.mobileOpen()) {
          this.mobileOpenChange.emit(false);
        }
      };
      mq.addEventListener('change', onMq);
      this.destroyRef.onDestroy(() => mq.removeEventListener('change', onMq));
    } else {
      this.isDesktop.set(true);
    }

    effect(() => {
      const open = this.mobileOpen();
      const desktop = this.isDesktop();
      untracked(() => {
        if (open && !desktop) {
          afterNextRender(() => this.drawerCloseBtn()?.nativeElement.focus());
        }
      });
    });

    effect(() => {
      const lock = this.mobileOpen() && !this.isDesktop();
      const body = this.document.body;
      if (lock) {
        body.classList.add('overflow-hidden');
      } else {
        body.classList.remove('overflow-hidden');
      }
    });

    fromEvent<KeyboardEvent>(this.document, 'keydown')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((event) => {
        if (event.key === 'Escape' && this.mobileOpen() && !this.isDesktop()) {
          event.preventDefault();
          this.closeMobile();
        }
      });
  }

  protected headerPaddingClass(): string {
    return this.collapsed() && this.isDesktop() ? 'p-3' : 'p-6';
  }

  protected footerPaddingClass(): string {
    return this.collapsed() && this.isDesktop() ? 'p-2' : 'p-4';
  }

  protected characterRowClass(): string {
    const collapsed = this.collapsed() && this.isDesktop();
    const base = 'flex items-center gap-4';
    return collapsed ? `${base} justify-center` : base;
  }

  protected primaryRowClass(id: string, isLink: boolean): string {
    const active = id === this.activePrimaryId();
    const base =
      'flex w-full items-center gap-3 px-4 py-3 text-left font-cinzel text-sm font-bold uppercase tracking-wide transition';
    const activeCls = isLink
      ? ''
      : active
        ? `${base} border-l-4 border-primary-container bg-yellow-500/10 text-primary-container ee-arcane-glow`
        : '';
    const idle = `${base} text-slate-400 hover:bg-white/5 hover:text-on-surface`;
    return isLink ? idle : active ? activeCls : idle;
  }

  protected professionRowClass(id: string, isLink: boolean): string {
    const active = id === this.activeId();
    const narrow = this.collapsed() && this.isDesktop();
    const layout = narrow ? 'justify-center px-2' : 'gap-3 px-6';
    const base = `flex w-full items-center ${layout} py-3 text-left font-space-mono text-xs uppercase transition`;
    const activeCls = isLink
      ? narrow
        ? `${base} justify-center border-r-4 border-primary-container bg-yellow-500/10 text-primary-container ee-arcane-glow`
        : `${base} border-r-4 border-primary-container bg-yellow-500/10 text-primary-container ee-arcane-glow`
      : active
        ? `${base} border-r-4 border-primary-container bg-yellow-500/10 text-primary-container ee-arcane-glow`
        : '';
    const idle = `${base} text-slate-400 hover:bg-white/5 hover:text-purple-300`;
    if (isLink) {
      return narrow ? `${idle} justify-center` : idle;
    }
    return active ? activeCls : idle;
  }

  protected footerBtnClass(collapsed: boolean): string {
    const layout = collapsed ? 'justify-center px-2' : 'gap-3 px-4';
    return `flex w-full items-center ${layout} rounded py-2 font-space-mono text-xs uppercase text-slate-400 transition hover:bg-white/5 hover:text-purple-300`;
  }

  protected professionLinkAriaLabel(item: NavItem): string | null {
    return this.collapsed() && this.isDesktop() ? item.label : null;
  }

  protected professionButtonAriaLabel(item: NavItem): string | null {
    return this.collapsed() && this.isDesktop() ? item.label : null;
  }

  protected toggleCollapsed(): void {
    if (!this.isDesktop()) {
      return;
    }
    this.collapsed.update((c) => !c);
  }

  protected closeMobile(): void {
    this.mobileOpenChange.emit(false);
  }

  protected onMobileNavInteract(): void {
    if (this.mobileOpen() && !this.isDesktop()) {
      this.mobileOpenChange.emit(false);
    }
  }

  protected onPrimaryButton(id: string): void {
    this.primarySelected.emit(id);
    this.onMobileNavInteract();
  }

  protected onProfessionButton(id: string): void {
    this.selected.emit(id);
    this.onMobileNavInteract();
  }
}
