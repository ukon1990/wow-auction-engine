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
        <header class="flex flex-col gap-2" [eeSkeleton]="loading()">
          <p
            class="ee-label text-outline"
            [attr.id]="titleId() ? titleId() + '-eyebrow' : null"
            i18n="@@pageFrame.eyebrow"
          >
            {{ eyebrow() }}
          </p>
          <h1
            class="font-cinzel text-3xl font-bold text-primary sm:text-4xl"
            [attr.id]="titleId()"
            i18n="@@pageFrame.title"
          >
            {{ title() }}
          </h1>
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
