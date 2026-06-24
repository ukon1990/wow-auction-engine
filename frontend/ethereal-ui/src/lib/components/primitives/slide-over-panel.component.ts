import { CdkTrapFocus } from '@angular/cdk/a11y';
import { DomPortal, DomPortalOutlet } from '@angular/cdk/portal';
import { isPlatformBrowser } from '@angular/common';
import {
  afterNextRender,
  ApplicationRef,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  DOCUMENT,
  effect,
  ElementRef,
  inject,
  Injector,
  input,
  output,
  PLATFORM_ID,
  untracked,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { fromEvent } from 'rxjs';

import { SymbolIconComponent } from './symbol-icon.component';

@Component({
  selector: 'ee-slide-over',
  imports: [CdkTrapFocus, SymbolIconComponent],
  template: `
    @if (open()) {
      <div #portalRoot>
        <div class="fixed inset-0 z-[60] bg-black/50" aria-hidden="true" (click)="close()"></div>

        <div
          class="fixed top-16 right-0 z-[60] flex h-[calc(100vh-4rem)] w-[min(28rem,90vw)] translate-x-0 flex-col border-l border-white/10 bg-slate-950/95 shadow-xl backdrop-blur-2xl transition-transform duration-200 ease-out"
          role="dialog"
          aria-modal="true"
          [attr.aria-labelledby]="resolvedTitleId()"
          cdkTrapFocus
          [cdkTrapFocusAutoCapture]="true"
        >
          <header class="flex items-center justify-between border-b border-white/10 px-5 py-4">
            <h2
              [id]="resolvedTitleId()"
              class="font-cinzel text-xl font-bold text-primary-container"
            >
              {{ title() }}
            </h2>
            <button
              #closeBtn
              type="button"
              class="inline-flex h-10 w-10 items-center justify-center rounded-full border border-white/10 text-on-surface transition hover:bg-white/5"
              aria-label="Close panel"
              (click)="close()"
            >
              <ee-symbol-icon class="text-[22px]" name="close" />
            </button>
          </header>
          <div class="min-h-0 flex-1 overflow-y-auto px-5 py-5">
            <ng-content />
          </div>
        </div>
      </div>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SlideOverPanelComponent {
  readonly open = input(false);
  readonly title = input.required<string>();
  readonly titleId = input<string | undefined>(undefined);

  readonly closed = output<void>();

  private readonly appRef = inject(ApplicationRef);
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly platformId = inject(PLATFORM_ID);

  private readonly closeBtn = viewChild<ElementRef<HTMLButtonElement>>('closeBtn');
  private readonly portalRoot = viewChild<ElementRef<HTMLDivElement>>('portalRoot');
  private readonly generatedTitleId = `ee-slide-over-title-${crypto.randomUUID()}`;

  private portalOutlet: DomPortalOutlet | null = null;
  private portalContainer: HTMLDivElement | null = null;

  constructor() {
    effect(() => {
      const shouldFocus = this.open();
      untracked(() => {
        if (shouldFocus && isPlatformBrowser(this.platformId)) {
          afterNextRender(() => this.closeBtn()?.nativeElement.focus(), {
            injector: this.injector,
          });
        }
      });
    });

    effect(() => {
      const isOpen = this.open();
      untracked(() => {
        if (!isPlatformBrowser(this.platformId)) {
          return;
        }
        if (isOpen) {
          afterNextRender(() => this.attachPortal(), { injector: this.injector });
        } else {
          this.detachPortal();
        }
      });
    });

    effect(() => {
      const lock = this.open();
      const body = this.document.body;
      if (lock) {
        body.classList.add('overflow-hidden');
      } else {
        body.classList.remove('overflow-hidden');
      }
    });

    if (isPlatformBrowser(this.platformId)) {
      fromEvent<KeyboardEvent>(this.document, 'keydown')
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((event) => {
          if (event.key === 'Escape' && this.open()) {
            event.preventDefault();
            this.close();
          }
        });
    }

    this.destroyRef.onDestroy(() => this.detachPortal());
  }

  protected resolvedTitleId(): string {
    return this.titleId() ?? this.generatedTitleId;
  }

  protected close(): void {
    this.closed.emit();
  }

  private attachPortal(): void {
    const root = this.portalRoot()?.nativeElement;
    if (!root || this.portalOutlet) {
      return;
    }

    this.portalContainer = this.document.createElement('div');
    this.document.body.appendChild(this.portalContainer);
    this.portalOutlet = new DomPortalOutlet(this.portalContainer, this.appRef, this.injector);
    this.portalOutlet.attach(new DomPortal(root));
  }

  private detachPortal(): void {
    if (!this.portalOutlet) {
      return;
    }
    this.portalOutlet.detach();
    this.portalOutlet.dispose();
    this.portalOutlet = null;
    this.portalContainer?.remove();
    this.portalContainer = null;
  }
}
