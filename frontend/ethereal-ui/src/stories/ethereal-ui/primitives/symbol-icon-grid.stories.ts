import type { Meta, StoryObj } from '@storybook/angular';

import { SymbolIconGridStoryHostComponent } from '../../support/story-hosts';

const meta: Meta<SymbolIconGridStoryHostComponent> = {
  title: 'Ethereal UI/Primitives',
  component: SymbolIconGridStoryHostComponent,
  parameters: { layout: 'centered' },
};

export default meta;

export const SymbolIconGrid: StoryObj<SymbolIconGridStoryHostComponent> = {};
