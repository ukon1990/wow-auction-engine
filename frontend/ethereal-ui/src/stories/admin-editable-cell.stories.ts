import type { Meta, StoryObj } from '@storybook/angular';

import { AdminEditableCellStoryHostComponent } from './story-hosts';

const meta: Meta<AdminEditableCellStoryHostComponent> = {
  title: 'Ethereal UI/Market/Table',
  component: AdminEditableCellStoryHostComponent,
  parameters: { layout: 'centered' },
};

export default meta;

export const AdminEditableCell: StoryObj<AdminEditableCellStoryHostComponent> = {};
