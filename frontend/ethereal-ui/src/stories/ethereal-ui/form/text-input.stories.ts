import type { Meta, StoryObj } from '@storybook/angular';

import { TextInputComponent } from '../../../public-api';

const meta: Meta<TextInputComponent> = {
  title: 'Ethereal UI/Form',
  component: TextInputComponent,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component:
          'Single-line text input. Supports standard input/output binding and Angular reactive forms through ControlValueAccessor.',
      },
    },
  },
  argTypes: {
    type: {
      control: 'select',
      options: ['text', 'email', 'password', 'number', 'search', 'url'],
    },
    disabled: { control: 'boolean' },
    required: { control: 'boolean' },
    invalid: { control: 'boolean' },
    valueChanged: { action: 'valueChanged' },
  },
};

export default meta;

export const TextInput: StoryObj<TextInputComponent> = {
  args: {
    label: 'Item name',
    placeholder: 'Dracothyst',
    hint: 'Used for generic form text fields.',
    type: 'text',
    value: 'Awakened Order',
    disabled: false,
    required: false,
    invalid: false,
    error: '',
  },
};

export const TextInputErrorState: StoryObj<TextInputComponent> = {
  args: {
    label: 'Email address',
    placeholder: 'player@example.com',
    hint: 'Used by login and account forms.',
    type: 'email',
    value: '',
    disabled: false,
    required: true,
    invalid: true,
    error: 'Email address is required.',
  },
};
