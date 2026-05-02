import type { Meta, StoryObj } from '@storybook/angular';

import { AdminEditableCellComponent } from '../../../public-api';

const meta: Meta<AdminEditableCellComponent> = {
  title: 'Ethereal UI/Form',
  component: AdminEditableCellComponent,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component:
          'Compact numeric cell input for dense admin tables. Supports Angular reactive forms through ControlValueAccessor.',
      },
    },
  },
  argTypes: {
    highlighted: { control: 'boolean' },
    disabled: { control: 'boolean' },
    required: { control: 'boolean' },
    invalid: { control: 'boolean' },
    valueChanged: { action: 'valueChanged' },
  },
};

export default meta;

export const AdminEditableCell: StoryObj<AdminEditableCellComponent> = {
  args: {
    label: 'Yield override',
    value: '1.25',
    placeholder: '1.00',
    highlighted: true,
    disabled: false,
    required: false,
    invalid: false,
    error: '',
  },
};

export const AdminEditableCellErrorState: StoryObj<AdminEditableCellComponent> = {
  args: {
    label: 'Yield override',
    value: '',
    placeholder: '1.00',
    highlighted: false,
    disabled: false,
    required: true,
    invalid: true,
    error: 'Required.',
  },
};
