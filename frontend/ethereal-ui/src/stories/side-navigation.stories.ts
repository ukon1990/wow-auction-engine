import type { Meta, StoryObj } from '@storybook/angular';

import { SideNavigationStoryHostComponent } from './story-hosts';

const meta: Meta<SideNavigationStoryHostComponent> = {
  title: 'Ethereal UI/Navigation',
  component: SideNavigationStoryHostComponent,
  parameters: { layout: 'fullscreen' },
};

export default meta;

export const SideNavigation: StoryObj<SideNavigationStoryHostComponent> = {};
