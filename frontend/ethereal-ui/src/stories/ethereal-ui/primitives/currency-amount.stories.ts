import type { Meta, StoryObj } from '@storybook/angular';

import { CurrencyAmountStoryHostComponent } from '../../support/story-hosts';

const meta: Meta<CurrencyAmountStoryHostComponent> = {
  title: 'Ethereal UI/Primitives',
  component: CurrencyAmountStoryHostComponent,
  parameters: { layout: 'centered' },
};

export default meta;

export const CurrencyAmount: StoryObj<CurrencyAmountStoryHostComponent> = {};
