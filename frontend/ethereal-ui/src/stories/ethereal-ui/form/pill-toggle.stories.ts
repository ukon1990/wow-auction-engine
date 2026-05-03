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
          'Segmented pill control for one value from a small set. Supports reactive forms (CVA) and signal forms (`FormValueControl`, `[formField]`). Initial selection uses the `value` model.',
      },
    },
  },
  argTypes: {
    value: {
      control: 'select',
      options: ['realm', 'region', ''],
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
    value: 'realm',
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
    value: '',
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
