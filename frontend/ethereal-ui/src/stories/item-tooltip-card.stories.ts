import type { Meta, StoryObj } from '@storybook/angular';

import { ItemTooltipCardStoryHostComponent } from './story-hosts';

const meta: Meta<ItemTooltipCardStoryHostComponent> = {
  title: 'Ethereal UI/Market/Table',
  component: ItemTooltipCardStoryHostComponent,
  parameters: { layout: 'centered' },
};

export default meta;

export const ItemTooltipCard: StoryObj<ItemTooltipCardStoryHostComponent> = {};
