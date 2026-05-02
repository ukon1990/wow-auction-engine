import type { Meta, StoryObj } from '@storybook/angular';

import { SideNavigationStoryHostComponent } from '../../support/story-hosts';

const meta: Meta<SideNavigationStoryHostComponent> = {
  title: 'Ethereal UI/Navigation',
  component: SideNavigationStoryHostComponent,
  parameters: { layout: 'fullscreen' },
};

export default meta;

export const SideNavigation: StoryObj<SideNavigationStoryHostComponent> = {};

/** Resize the preview to &lt;768px width or use this preset to exercise the drawer. */
export const SideNavigationMobile: StoryObj<SideNavigationStoryHostComponent> = {
  parameters: {
    viewport: {
      defaultViewport: 'mobile1',
    },
  },
};
