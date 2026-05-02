import type { Meta, StoryObj } from '@storybook/angular';

import { MarketBrowserStoryHostComponent } from '../../support/story-hosts';

const meta: Meta<MarketBrowserStoryHostComponent> = {
  title: 'Ethereal UI/Compositions/Market Browser',
  component: MarketBrowserStoryHostComponent,
  parameters: {
    layout: 'fullscreen',
  },
};

export default meta;

export const Default: StoryObj<MarketBrowserStoryHostComponent> = {};

export const Mobile: StoryObj<MarketBrowserStoryHostComponent> = {
  parameters: {
    viewport: {
      defaultViewport: 'mobile1',
    },
  },
};
