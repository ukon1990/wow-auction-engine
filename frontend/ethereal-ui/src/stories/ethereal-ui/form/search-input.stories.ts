import type { Meta, StoryObj } from '@storybook/angular';

import { SearchInputComponent } from '../../../public-api';

const meta: Meta<SearchInputComponent> = {
  title: 'Ethereal UI/Form',
  component: SearchInputComponent,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component:
          'Search field with inline symbol icon. Supports input/output binding and Angular reactive forms through ControlValueAccessor.',
      },
    },
  },
  argTypes: {
    disabled: { control: 'boolean' },
    required: { control: 'boolean' },
    invalid: { control: 'boolean' },
    valueChanged: { action: 'valueChanged' },
  },
};

export default meta;

export const SearchInput: StoryObj<SearchInputComponent> = {
  args: {
    label: 'Search market',
    placeholder: 'Search items, reagents, or recipes...',
    value: '',
    disabled: false,
    required: false,
    invalid: false,
    error: '',
  },
};

export const SearchInputErrorState: StoryObj<SearchInputComponent> = {
  args: {
    label: 'Search market',
    placeholder: 'Search items, reagents, or recipes...',
    value: '',
    disabled: false,
    required: true,
    invalid: true,
    error: 'Enter at least 3 characters.',
  },
};

export const SearchInputWithVisibleLabel: StoryObj<SearchInputComponent> = {
  args: {
    label: 'Item ID',
    showLabel: true,
    placeholder: 'Contains item ID',
    value: '',
    disabled: false,
    required: false,
    invalid: false,
    error: '',
  },
};
