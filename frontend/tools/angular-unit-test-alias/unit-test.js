import { createBuilder } from '@angular-devkit/architect';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { pathToFileURL } from 'node:url';

const require = createRequire(import.meta.url);
const angularBuildRoot = dirname(require.resolve('@angular/build/package.json'));
const unitTestBuilderUrl = pathToFileURL(join(angularBuildRoot, 'src/builders/unit-test/index.js'));
const { execute } = await import(unitTestBuilderUrl.href);

export default createBuilder((options, context) => {
  const { config: _config, ...angularOptions } = options;
  return execute(angularOptions, context);
});
