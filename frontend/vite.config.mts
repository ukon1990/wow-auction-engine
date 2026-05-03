/// <reference types="vitest" />
import angular from '@analogjs/vite-plugin-angular';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [
    angular({
      tsconfig: './tsconfig.vitest.json',
    }),
  ],
  resolve: {
    alias: {
      '@ui': new URL('./ethereal-ui/src/public-api.ts', import.meta.url).pathname,
    },
  },
  optimizeDeps: {
    noDiscovery: true,
    entries: ['src/**/*.spec.ts', 'ethereal-ui/src/**/*.spec.ts'],
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['tools/vitest.setup.ts'],
    include: ['src/**/*.spec.ts', 'ethereal-ui/src/**/*.spec.ts'],
    reporters: ['default'],
  },
});
