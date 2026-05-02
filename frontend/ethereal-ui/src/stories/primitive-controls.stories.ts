import type { Meta, StoryObj } from '@storybook/angular';

import { PrimitiveControlsStoryHostComponent } from './story-hosts';

const meta: Meta<PrimitiveControlsStoryHostComponent> = {
  title: 'Ethereal UI/Primitives',
  component: PrimitiveControlsStoryHostComponent,
  parameters: { layout: 'centered' },
};

export default meta;

export const PrimitiveControls: StoryObj<PrimitiveControlsStoryHostComponent> = {};
