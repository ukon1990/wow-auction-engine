import type { Meta, StoryObj } from '@storybook/angular';
import { Component, signal } from '@angular/core';
import { SlideOverPanelComponent } from '../../../lib/components/primitives/slide-over-panel.component';

@Component({
  selector: 'story-slide-over-host',
  imports: [SlideOverPanelComponent],
  template: `
    <div class="p-6">
      <button
        type="button"
        class="rounded-md border border-white/10 px-4 py-2 text-on-surface"
        (click)="open.set(true)"
      >
        Open slide-over
      </button>
      <ee-slide-over [open]="open()" title="Example panel" (closed)="open.set(false)">
        <p class="ee-data text-outline">Panel body content goes here.</p>
      </ee-slide-over>
    </div>
  `,
})
class SlideOverStoryHostComponent {
  protected readonly open = signal(false);
}

const meta: Meta<SlideOverStoryHostComponent> = {
  title: 'Ethereal UI/Primitives',
  component: SlideOverStoryHostComponent,
  parameters: { layout: 'fullscreen' },
};

export default meta;

export const SlideOver: StoryObj<SlideOverStoryHostComponent> = {};
