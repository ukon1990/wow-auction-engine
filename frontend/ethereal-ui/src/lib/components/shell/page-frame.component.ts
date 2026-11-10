import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { SkeletonDirective } from '../../directives/skeleton.directive';

/** How the frame uses vertical space under the header. */
export type PageFrameBodyLayout = 'scroll' | 'fill';

@Component({
  selector: 'ee-page-frame',
  imports: [SkeletonDirective],
  host: {
    class: 'flex min-h-0 min-w-0 flex-1 flex-col',
  },
  template: `
    <main id="page-main" [class]="mainClass()" tabindex="-1">
      <div [class]="innerClass()">
        <header
          class="flex flex-wrap items-start justify-between gap-x-4 gap-y-3"
          [eeSkeleton]="loading()"
        >
          <div class="flex min-w-0 flex-col gap-1">
            @if (eyebrow()) {
              <p
                class="ee-label text-outline"
                [attr.id]="titleId() ? titleId() + '-eyebrow' : null"
                i18n="@@pageFrame.eyebrow"
              >
                {{ eyebrow() }}
              </p>
            }
            <h1
              class="font-cinzel text-2xl font-bold text-primary sm:text-3xl"
              [attr.id]="titleId()"
              i18n="@@pageFrame.title"
            >
              {{ title() }}
            </h1>
            @if (description()) {
              <p class="text-sm text-on-surface-variant">{{ description() }}</p>
            }
          </div>
          <ng-content select="[eePageActions]" />
        </header>
        <ng-content />
      </div>
    </main>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PageFrameComponent {
  readonly title = input.required<string>();
  readonly eyebrow = input('');
  /** One line under the title that says what the page is for. */
  readonly description = input('');
  readonly loading = input(false);
  /**
   * `scroll` (default): this frame is the vertical scroll container for long pages (Codex, docs).
   * `fill`: clip to the viewport height; use with nested `min-h-0` + internal scroll (e.g. data tables).
   */
  readonly bodyLayout = input<PageFrameBodyLayout>('scroll');

  /** Optional id for the `<h1>` (eyebrow gets `{id}-eyebrow`) — for `aria-labelledby` / anchors. */
  readonly titleId = input<string | undefined>(undefined);

  protected readonly mainClass = computed(() => {
    const base =
      'flex min-h-0 min-w-0 flex-1 flex-col scroll-mt-20 outline-none focus:outline-none p-container-padding';
    return this.bodyLayout() === 'fill'
      ? `${base} overflow-x-hidden overflow-y-hidden`
      : `${base} overflow-x-hidden overflow-y-auto overscroll-y-contain`;
  });

  protected readonly innerClass = computed(() => {
    const base = 'mx-auto flex w-full max-w-[1500px] flex-col gap-element-gap';
    return this.bodyLayout() === 'fill'
      ? `${base} h-full min-h-0 flex-1 pb-0 lg:pb-10`
      : `${base} min-h-min pb-8 sm:pb-10`;
  });
}
