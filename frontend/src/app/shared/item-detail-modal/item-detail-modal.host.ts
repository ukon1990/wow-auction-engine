import { CdkTrapFocus } from '@angular/cdk/a11y';
import { DomPortal, DomPortalOutlet } from '@angular/cdk/portal';
import { isPlatformBrowser } from '@angular/common';
import {
  afterNextRender,
  ApplicationRef,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  DOCUMENT,
  effect,
  ElementRef,
  inject,
  Injector,
  PLATFORM_ID,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { fromEvent } from 'rxjs';
import { SymbolIconComponent } from '@ui';

import { ItemDetailModalService } from '@core/services/item-detail-modal.service';
import {
  buildItemDetailUrl,
  itemDetailVariantFromOpenParams,
} from '@core/services/item-detail-url.helpers';
import { ToastService } from '@core/services/toast.service';
import { RealmSelectionService } from '@core/services/realm-selection.service';
import { MarketItemDetailPanelComponent } from '@features/market-browser/market-item-detail.panel';
import type { RegionCode } from '@features/market-browser/market-item-detail.helpers';

@Component({
  selector: 'app-item-detail-modal-host',
  imports: [CdkTrapFocus, MarketItemDetailPanelComponent, SymbolIconComponent],
  template: `
    @if (modal.state(); as open) {
      <div #portalRoot>
        <div
          class="fixed inset-0 z-[60] grid place-items-center bg-black/75 p-3 sm:p-5"
          role="dialog"
          aria-modal="true"
          [attr.aria-labelledby]="titleId"
          cdkTrapFocus
          [cdkTrapFocusAutoCapture]="true"
        >
          <div class="fixed inset-0" aria-hidden="true" (click)="close()"></div>
          <section
            class="ee-glass relative z-[1] grid max-h-[92vh] w-full max-w-6xl grid-rows-[auto_1fr] overflow-hidden rounded-lg"
            (click)="$event.stopPropagation()"
          >
            <header
              class="flex flex-wrap items-center justify-between gap-3 border-b border-white/10 px-5 py-4"
            >
              <h2
                [id]="titleId"
                class="min-w-0 truncate font-cinzel text-xl font-bold text-primary-container"
              >
                {{ modalTitle() }}
              </h2>
              <div class="flex flex-wrap items-center gap-2">
                <button
                  type="button"
                  class="inline-flex items-center gap-2 rounded border border-white/10 bg-surface-container-high px-3 py-2 ee-label text-on-surface transition hover:bg-surface-container-highest focus:outline-none focus-visible:ring-2 focus-visible:ring-primary/60"
                  (click)="copyShareLink()"
                >
                  <ee-symbol-icon class="text-base" name="link" aria-hidden="true" />
                  <ng-container i18n="@@itemDetail.copyLink">Copy link</ng-container>
                </button>
                <button
                  type="button"
                  class="inline-flex items-center gap-2 rounded border border-white/10 bg-surface-container-high px-3 py-2 ee-label text-on-surface transition hover:bg-surface-container-highest focus:outline-none focus-visible:ring-2 focus-visible:ring-primary/60"
                  (click)="openSharePage()"
                >
                  <ee-symbol-icon class="text-base" name="open_in_new" aria-hidden="true" />
                  <ng-container i18n="@@itemDetail.openPage">Open page</ng-container>
                </button>
                <button
                  #closeBtn
                  type="button"
                  class="inline-flex h-10 w-10 items-center justify-center rounded-full border border-white/10 text-on-surface transition hover:bg-white/5"
                  [attr.aria-label]="closeLabel"
                  (click)="close()"
                >
                  <ee-symbol-icon class="text-[22px]" name="close" />
                </button>
              </div>
            </header>
            <div class="min-h-0 overflow-y-auto px-5 py-5">
              @if (realmContext(); as realm) {
                <app-market-item-detail-panel
                  [region]="realm.region"
                  [realmSlug]="realm.slug"
                  [itemId]="open.itemId"
                  [variant]="variant()"
                  [initialScope]="open.scope"
                  [recipeId]="open.recipeId ?? null"
                  linkMode="modal"
                  (titleChange)="onTitleChange($event)"
                />
              }
            </div>
          </section>
        </div>
      </div>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ItemDetailModalHostComponent {
  protected readonly modal = inject(ItemDetailModalService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly realmSelection = inject(RealmSelectionService);
  private readonly appRef = inject(ApplicationRef);
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly platformId = inject(PLATFORM_ID);

  protected readonly closeLabel = $localize`:@@itemDetail.modal.close:Close item detail`;
  protected readonly titleId = `item-detail-modal-title-${crypto.randomUUID()}`;
  protected readonly modalTitle = signal($localize`:@@itemDetail.modal.loadingTitle:Item`);

  private readonly closeBtn = viewChild<ElementRef<HTMLButtonElement>>('closeBtn');
  private readonly portalRoot = viewChild<ElementRef<HTMLDivElement>>('portalRoot');

  private portalOutlet: DomPortalOutlet | null = null;
  private portalContainer: HTMLDivElement | null = null;

  protected readonly realmContext = computed((): { region: RegionCode; slug: string } | null => {
    const fromUrl = this.parseRealmFromUrl();
    if (fromUrl) return fromUrl;
    const selected = this.realmSelection.selected();
    if (!selected) return null;
    return { region: selected.region as RegionCode, slug: selected.slug };
  });

  protected readonly variant = computed(() => {
    const open = this.modal.state();
    if (!open) {
      return { bonusKey: '', modifierKey: '', petSpeciesId: 0 };
    }
    return itemDetailVariantFromOpenParams(open);
  });

  constructor() {
    effect(() => {
      const shouldFocus = this.modal.state() != null;
      untracked(() => {
        if (shouldFocus && isPlatformBrowser(this.platformId)) {
          afterNextRender(() => this.closeBtn()?.nativeElement.focus(), {
            injector: this.injector,
          });
        }
      });
    });

    effect(() => {
      const isOpen = this.modal.state() != null;
      untracked(() => {
        if (!isPlatformBrowser(this.platformId)) return;
        if (isOpen) {
          afterNextRender(() => this.attachPortal(), { injector: this.injector });
        } else {
          this.detachPortal();
        }
      });
    });

    effect(() => {
      const lock = this.modal.state() != null;
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
          if (event.key === 'Escape' && this.modal.state()) {
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

  protected onTitleChange(title: string): void {
    this.modalTitle.set(title);
  }

  protected close(): void {
    this.modal.close();
    this.modalTitle.set($localize`:@@itemDetail.modal.loadingTitle:Item`);
  }

  protected copyShareLink(): void {
    const url = this.shareUrl();
    if (!url) return;
    const absolute = new URL(url, this.document.defaultView?.location.origin ?? '').toString();
    void navigator.clipboard?.writeText(absolute).then(
      () => {
        this.toast.success($localize`:@@itemDetail.linkCopied:Item link copied to clipboard.`);
      },
      () => {
        this.toast.error($localize`:@@itemDetail.linkCopyFailed:Could not copy link.`);
      },
    );
  }

  protected openSharePage(): void {
    const url = this.shareUrl();
    if (!url) return;
    this.close();
    void this.router.navigateByUrl(url);
  }

  private shareUrl(): string | null {
    const open = this.modal.state();
    const realm = this.realmContext();
    if (!open || !realm) return null;
    return buildItemDetailUrl(this.router, realm.region, realm.slug, open);
  }

  private parseRealmFromUrl(): { region: RegionCode; slug: string } | null {
    const segments = this.router.url.split('?')[0].split('/').filter(Boolean);
    const region = segments[0]?.toLowerCase();
    const slug = segments[1];
    if (!region || !slug) return null;
    if (!['us', 'eu', 'kr', 'tw'].includes(region)) return null;
    return { region: region as RegionCode, slug };
  }

  private attachPortal(): void {
    const root = this.portalRoot()?.nativeElement;
    if (!root || this.portalOutlet) return;

    this.portalContainer = this.document.createElement('div');
    this.document.body.appendChild(this.portalContainer);
    this.portalOutlet = new DomPortalOutlet(this.portalContainer, this.appRef, this.injector);
    this.portalOutlet.attach(new DomPortal(root));
  }

  private detachPortal(): void {
    if (!this.portalOutlet) return;
    this.portalOutlet.detach();
    this.portalOutlet.dispose();
    this.portalOutlet = null;
    this.portalContainer?.remove();
    this.portalContainer = null;
  }
}
