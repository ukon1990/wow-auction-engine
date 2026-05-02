import { applicationConfig } from '@storybook/angular';
import { provideRouter } from '@angular/router';
import type { Preview } from '@storybook/angular';

import { storybookRoutes } from './story-router';

const preview: Preview = {
  decorators: [
    applicationConfig({
      providers: [provideRouter(storybookRoutes)],
    }),
  ],
  parameters: {
    backgrounds: {
      default: 'Ethereal Arcana',
      values: [{ name: 'Ethereal Arcana', value: '#17130a' }],
    },
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i,
      },
    },
  },
};

export default preview;
