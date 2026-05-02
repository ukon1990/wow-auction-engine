import type { Meta, StoryObj } from '@storybook/angular';

import { FilterPanelStoryHostComponent } from '../../../support/story-hosts';

const meta: Meta<FilterPanelStoryHostComponent> = {
  title: 'Ethereal UI/Market/Table',
  component: FilterPanelStoryHostComponent,
  parameters: { layout: 'fullscreen' },
};

export default meta;

export const FilterPanel: StoryObj<FilterPanelStoryHostComponent> = {};
