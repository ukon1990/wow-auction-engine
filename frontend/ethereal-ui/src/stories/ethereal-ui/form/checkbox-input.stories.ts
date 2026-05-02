import type { Meta, StoryObj } from '@storybook/angular';

import { CheckboxInputComponent } from '../../../public-api';

const meta: Meta<CheckboxInputComponent> = {
  title: 'Ethereal UI/Form',
  component: CheckboxInputComponent,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component:
          'Boolean checkbox input for filters and settings. Supports Angular reactive forms through ControlValueAccessor.',
      },
    },
  },
  argTypes: {
    checked: { control: 'boolean' },
    disabled: { control: 'boolean' },
    required: { control: 'boolean' },
    invalid: { control: 'boolean' },
    checkedChanged: { action: 'checkedChanged' },
  },
};

export default meta;

export const CheckboxInput: StoryObj<CheckboxInputComponent> = {
  args: {
    label: 'Only show profitable crafts',
    hint: 'Include items where expected profit is positive.',
    checked: true,
    disabled: false,
    required: false,
    invalid: false,
    error: '',
  },
};

export const CheckboxInputErrorState: StoryObj<CheckboxInputComponent> = {
  args: {
    label: 'Accept terms',
    hint: 'Required before creating an account.',
    checked: false,
    disabled: false,
    required: true,
    invalid: true,
    error: 'This checkbox must be selected.',
  },
};
