import type { Meta, StoryObj } from '@storybook/angular';

import { ChartPanelStoryHostComponent } from '../../../support/story-hosts';

const meta: Meta<ChartPanelStoryHostComponent> = {
  title: 'Ethereal UI/Market/Table',
  component: ChartPanelStoryHostComponent,
  parameters: { layout: 'centered' },
};

export default meta;

export const ChartPanel: StoryObj<ChartPanelStoryHostComponent> = {};
