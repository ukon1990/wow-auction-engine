import { provideRouter } from '@angular/router';
import { applicationConfig, type Meta, type StoryObj } from '@storybook/angular';

import {
  TopNavigationChildRoutesStoryHostComponent,
  TopNavigationStoryHostComponent,
} from '../../support/story-hosts';

const meta: Meta<TopNavigationStoryHostComponent> = {
  title: 'Ethereal UI/Navigation',
  component: TopNavigationStoryHostComponent,
  parameters: { layout: 'fullscreen' },
  decorators: [applicationConfig({ providers: [provideRouter([])] })],
};

export default meta;

export const TopNavigation: StoryObj<TopNavigationStoryHostComponent> = {};

export const TopNavigationWithChildRoutes: StoryObj<TopNavigationChildRoutesStoryHostComponent> = {
  component: TopNavigationChildRoutesStoryHostComponent,
};
