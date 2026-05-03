# Testing

This workspace uses Angular's Vitest-based unit-test builder for Angular specs.

## Commands

- `bun run test:ci` runs the Angular/Vitest test suite in CI mode.
- `bun test` is supported for convenience, but delegates to `bun run test:ci` because Bun's native test runner does not initialize Angular TestBed.
- `bunx vitest run --config vitest.config.mts` runs Vitest directly using the explicit Vite/Vitest config used by IDEs.

## IntelliJ IDEA

IntelliJ's Vitest integration invokes Angular tests through `ng test`, but it passes a Vitest-style `--config` argument:

```sh
ng test --config /path/to/vite.config.mts --reporters ...
```

Angular's built-in `@angular/build:unit-test` builder does not accept `--config`; its equivalent option is `runnerConfig`. To keep IntelliJ run configurations working, the Angular `test` targets use a local compatibility builder:

```json
"builder": "@ethereal/angular-unit-test-alias:unit-test"
```

The builder lives in `tools/angular-unit-test-alias`. It delegates to Angular's real unit-test builder after:

1. accepting and dropping IntelliJ's `--config` alias,
2. normalizing absolute/workspace-relative `--include` paths,
3. skipping the wrong Angular project for single-file test runs.

This wrapper is only IDE compatibility glue; Angular's own test builder still compiles and runs the tests.

## Application and UI library tests

The application Angular project is named `EE` to keep the test-project label short in IDE output. Its build output still goes to `dist/The-Ethereal-Exchange` for Docker/runtime compatibility.

The frontend workspace has application specs under `src/` and UI library specs under `ethereal-ui/src/`. IntelliJ's “all frontend tests” command runs the default Angular test target, so the application test target includes both locations:

```json
"include": ["**/*.spec.ts", "../ethereal-ui/src/**/*.spec.ts"]
```

`tsconfig.spec.json` also includes both spec trees so Angular component metadata in UI library specs is part of the TypeScript program. When all tests run through IntelliJ, UI tests are therefore shown under the short `EE` project label and are executed together with app tests.
