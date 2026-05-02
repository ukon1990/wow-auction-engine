import type { Meta, StoryObj } from '@storybook/angular';

import { ReactiveFormStoryHostComponent } from '../../support/story-hosts';

const meta: Meta<ReactiveFormStoryHostComponent> = {
  title: 'Ethereal UI/Form',
  component: ReactiveFormStoryHostComponent,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component:
          'Reactive forms composition showing text, search, select, checkbox, segmented toggle, and compact numeric cell controls bound with formControlName.',
      },
    },
  },
};

export default meta;

export const ReactiveForms: StoryObj<ReactiveFormStoryHostComponent> = {};
