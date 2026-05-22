import { provideRouter } from '@angular/router';
import { applicationConfig, type Meta, moduleMetadata, type StoryObj } from '@storybook/angular';

import {
  TopNavigationChildRoutesStoryHostComponent,
  TopNavigationStoryHostComponent,
} from '../../support/story-hosts';

const meta: Meta<TopNavigationStoryHostComponent> = {
  title: 'Ethereal UI/Navigation',
  parameters: { layout: 'fullscreen' },
  decorators: [
    applicationConfig({ providers: [provideRouter([])] }),
    moduleMetadata({
      imports: [TopNavigationChildRoutesStoryHostComponent, TopNavigationStoryHostComponent],
    }),
  ],
};

export default meta;

export const TopNavigation: StoryObj<TopNavigationStoryHostComponent> = {
  render: () => ({
    template: '<story-top-navigation-host />',
  }),
};

export const TopNavigationWithChildRoutes: StoryObj<TopNavigationChildRoutesStoryHostComponent> = {
  render: () => ({
    template: '<story-top-navigation-child-routes-host />',
  }),
};
