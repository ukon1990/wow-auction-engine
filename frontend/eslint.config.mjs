import tseslint from '@typescript-eslint/eslint-plugin';
import tsParser from '@typescript-eslint/parser';
import sonarjs from 'eslint-plugin-sonarjs';

const productionMaxLines = ['error', { max: 600, skipBlankLines: true, skipComments: true }];

const testMaxLines = ['error', { max: 1000, skipBlankLines: true, skipComments: true }];

export default [
  {
    ignores: [
      '.angular/**',
      'dist/**',
      'node_modules/**',
      'coverage/**',
      'storybook-static/**',
      'src/app/api/generated/**',
    ],
  },
  {
    files: ['**/*.ts'],
    languageOptions: {
      parser: tsParser,
    },
    plugins: {
      '@typescript-eslint': tseslint,
      sonarjs,
    },
    rules: {
      ...tseslint.configs.recommended.rules,
      complexity: ['error', { max: 15 }],
      'sonarjs/cognitive-complexity': ['error', 15],
      'max-lines': productionMaxLines,
      'id-length': [
        'error',
        {
          min: 2,
          exceptions: ['_'],
          properties: 'never',
        },
      ],
      '@typescript-eslint/no-unused-vars': [
        'error',
        {
          argsIgnorePattern: '^_',
          varsIgnorePattern: '^_',
          caughtErrorsIgnorePattern: '^_',
        },
      ],
    },
  },
  {
    files: [
      '**/*.{spec,test}.ts',
      '**/*.stories.ts',
      '**/*.story.ts',
      '**/src/stories/**/*.ts',
      '**/.storybook/**/*.ts',
    ],
    rules: {
      'max-lines': testMaxLines,
    },
  },
];
