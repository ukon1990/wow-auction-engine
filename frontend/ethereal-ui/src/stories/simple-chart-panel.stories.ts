import type { Meta, StoryObj } from '@storybook/angular';

import { SimpleChartPanelStoryHostComponent } from './story-hosts';

const meta: Meta<SimpleChartPanelStoryHostComponent> = {
  title: 'Ethereal UI/Market/Table',
  component: SimpleChartPanelStoryHostComponent,
  parameters: { layout: 'centered' },
};

export default meta;

export const SimpleChartPanel: StoryObj<SimpleChartPanelStoryHostComponent> = {};
