import type { Meta, StoryObj } from '@storybook/angular';

import { QualityBadgeStoryHostComponent } from '../../support/story-hosts';

const meta: Meta<QualityBadgeStoryHostComponent> = {
  title: 'Ethereal UI/Primitives',
  component: QualityBadgeStoryHostComponent,
  parameters: { layout: 'centered' },
};

export default meta;

export const QualityBadge: StoryObj<QualityBadgeStoryHostComponent> = {};
