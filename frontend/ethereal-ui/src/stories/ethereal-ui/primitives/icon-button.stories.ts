import type { Meta, StoryObj } from '@storybook/angular';

import { IconButtonStoryHostComponent } from '../../support/story-hosts';

const meta: Meta<IconButtonStoryHostComponent> = {
  title: 'Ethereal UI/Primitives',
  component: IconButtonStoryHostComponent,
  parameters: { layout: 'centered' },
};

export default meta;

export const IconButton: StoryObj<IconButtonStoryHostComponent> = {};
