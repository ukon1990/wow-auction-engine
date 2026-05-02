import type { Meta, StoryObj } from '@storybook/angular';

import { PillToggleStoryHostComponent } from './story-hosts';

const meta: Meta<PillToggleStoryHostComponent> = {
  title: 'Ethereal UI/Primitives',
  component: PillToggleStoryHostComponent,
  parameters: { layout: 'centered' },
};

export default meta;

export const PillToggle: StoryObj<PillToggleStoryHostComponent> = {};
