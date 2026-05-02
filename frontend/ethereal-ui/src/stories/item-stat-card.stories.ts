import type { Meta, StoryObj } from '@storybook/angular';

import { ItemStatCardStoryHostComponent } from './story-hosts';

const meta: Meta<ItemStatCardStoryHostComponent> = {
  title: 'Ethereal UI/Market/Table',
  component: ItemStatCardStoryHostComponent,
  parameters: { layout: 'centered' },
};

export default meta;

export const ItemStatCard: StoryObj<ItemStatCardStoryHostComponent> = {};
