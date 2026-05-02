import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'ee-page-frame',
  template: `
    <main class="flex-1 overflow-hidden p-container-padding">
      <div class="mx-auto flex h-full max-w-[1500px] flex-col gap-element-gap">
        <header class="flex flex-col gap-2">
          <p class="ee-label text-outline">{{ eyebrow() }}</p>
          <h1 class="font-cinzel text-3xl font-bold text-primary">{{ title() }}</h1>
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
}
