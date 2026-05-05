import type { StorybookConfig } from '@analogjs/storybook-angular';
import { mergeConfig, type UserConfig } from 'vite';

const config: StorybookConfig = {
  stories: ['../ethereal-ui/src/**/*.stories.ts'],
  addons: ['@storybook/addon-docs'],
  previewHead: (head) => `${head}
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@24,400,0,0" />
<style>
  /* Desktop workshop: keep inline rail + primary tabs visible; mobile viewports keep real responsive behavior */
  @media (min-width: 768px) {
    ee-side-nav aside,
    ee-top-nav nav { display: flex !important; }
  }
</style>`,
  framework: {
    name: '@analogjs/storybook-angular',
    options: {},
  },
  staticDirs: ['../public'],
  async viteFinal(userConfig: UserConfig) {
    return mergeConfig(userConfig, {
      resolve: {
        dedupe: [
          '@angular/animations',
          '@angular/cdk',
          '@angular/common',
          '@angular/core',
          '@angular/forms',
          '@angular/platform-browser',
          '@angular/router',
        ],
        tsconfigPaths: true,
      },
      optimizeDeps: {
        exclude: [
          '@angular/animations',
          '@angular/cdk',
          '@angular/common',
          '@angular/core',
          '@angular/forms',
          '@angular/platform-browser',
          '@angular/router',
        ],
      },
    });
  },
};

export default config;
