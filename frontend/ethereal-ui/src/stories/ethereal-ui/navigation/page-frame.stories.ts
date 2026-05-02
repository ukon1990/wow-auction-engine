import type { Meta, StoryObj } from '@storybook/angular';

import { PageFrameStoryHostComponent } from '../../support/story-hosts';

const meta: Meta<PageFrameStoryHostComponent> = {
  title: 'Ethereal UI/Navigation',
  component: PageFrameStoryHostComponent,
  parameters: { layout: 'fullscreen' },
};

export default meta;

export const PageFrame: StoryObj<PageFrameStoryHostComponent> = {};
