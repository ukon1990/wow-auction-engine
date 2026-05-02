import type { Meta, StoryObj } from '@storybook/angular';

import { GlassPanelStoryHostComponent } from './story-hosts';

const meta: Meta<GlassPanelStoryHostComponent> = {
  title: 'Ethereal UI/Primitives',
  component: GlassPanelStoryHostComponent,
  parameters: { layout: 'centered' },
};

export default meta;

export const GlassPanel: StoryObj<GlassPanelStoryHostComponent> = {};
