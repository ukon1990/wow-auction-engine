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
import { SymbolIconComponent } from '@ui';

@Component({
  selector: 'app-admin-modal',
  imports: [CdkTrapFocus, SymbolIconComponent],
  template: `
    @if (open()) {
      <div #portalRoot>
        <div
          class="fixed inset-0 z-[60] grid place-items-center bg-black/75 p-3 sm:p-5"
          role="dialog"
          aria-modal="true"
          [attr.aria-labelledby]="resolvedTitleId()"
          cdkTrapFocus
          [cdkTrapFocusAutoCapture]="true"
        >
          <div class="fixed inset-0" aria-hidden="true" (click)="close()"></div>
          <section
            class="ee-glass relative z-[1] grid max-h-[92vh] w-full max-w-4xl grid-rows-[auto_1fr] overflow-hidden rounded-lg"
            (click)="$event.stopPropagation()"
          >
            <header
              class="flex items-center justify-between gap-3 border-b border-white/10 px-5 py-4"
            >
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
                [attr.aria-label]="closeLabel"
                (click)="close()"
              >
                <ee-symbol-icon class="text-[22px]" name="close" />
              </button>
            </header>
            <div class="min-h-0 overflow-y-auto px-5 py-5">
              <ng-content />
            </div>
          </section>
        </div>
      </div>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminModalComponent {
  readonly open = input(false);
  readonly title = input.required<string>();
  readonly titleId = input<string | undefined>(undefined);

  readonly closed = output<void>();

  protected readonly closeLabel = $localize`:@@admin.modal.close:Close dialog`;

  private readonly appRef = inject(ApplicationRef);
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly platformId = inject(PLATFORM_ID);

  private readonly closeBtn = viewChild<ElementRef<HTMLButtonElement>>('closeBtn');
  private readonly portalRoot = viewChild<ElementRef<HTMLDivElement>>('portalRoot');
  private readonly generatedTitleId = `admin-modal-title-${crypto.randomUUID()}`;

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

    this.destroyRef.onDestroy(() => {
      this.detachPortal();
      this.document.body.classList.remove('overflow-hidden');
    });
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
