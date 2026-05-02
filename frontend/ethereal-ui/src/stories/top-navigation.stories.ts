import type { Meta, StoryObj } from '@storybook/angular';

import { TopNavigationStoryHostComponent } from './story-hosts';

const meta: Meta<TopNavigationStoryHostComponent> = {
  title: 'Ethereal UI/Navigation',
  component: TopNavigationStoryHostComponent,
  parameters: { layout: 'fullscreen' },
};

export default meta;

export const TopNavigation: StoryObj<TopNavigationStoryHostComponent> = {};
