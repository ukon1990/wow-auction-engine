import type { Meta, StoryObj } from '@storybook/angular';

import { SearchInputStoryHostComponent } from './story-hosts';

const meta: Meta<SearchInputStoryHostComponent> = {
  title: 'Ethereal UI/Primitives',
  component: SearchInputStoryHostComponent,
  parameters: { layout: 'centered' },
};

export default meta;

export const SearchInput: StoryObj<SearchInputStoryHostComponent> = {};
