import type { Meta, StoryObj } from '@storybook/angular';

import { PillToggleComponent } from '../../../public-api';

const meta: Meta<PillToggleComponent> = {
  title: 'Ethereal UI/Form',
  component: PillToggleComponent,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component:
          'Segmented pill control for choosing one value from a small option set. Supports Angular reactive forms through ControlValueAccessor.',
      },
    },
  },
  argTypes: {
    activeId: {
      control: 'select',
      options: ['realm', 'region'],
    },
    disabled: { control: 'boolean' },
    required: { control: 'boolean' },
    invalid: { control: 'boolean' },
    options: { control: 'object' },
    selected: { action: 'selected' },
  },
};

export default meta;

export const PillToggle: StoryObj<PillToggleComponent> = {
  args: {
    label: 'Market scope',
    activeId: 'realm',
    disabled: false,
    required: false,
    invalid: false,
    error: '',
    options: [
      { id: 'realm', label: 'Realm' },
      { id: 'region', label: 'Region' },
    ],
  },
};

export const PillToggleErrorState: StoryObj<PillToggleComponent> = {
  args: {
    label: 'Market scope',
    activeId: '',
    disabled: false,
    required: true,
    invalid: true,
    error: 'Choose a market scope.',
    options: [
      { id: 'realm', label: 'Realm' },
      { id: 'region', label: 'Region' },
    ],
  },
};
