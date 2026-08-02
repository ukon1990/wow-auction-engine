import assert from 'node:assert/strict';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { ESLint } from 'eslint';

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const eslint = new ESLint({ cwd: frontendRoot });

function sequentialConditions(conditionCount) {
  const conditions = Array.from(
    { length: conditionCount },
    (_, index) => `  if (inputValue === ${index}) resultValue += 1;`,
  ).join('\n');
  return `export function calculateValue(inputValue: number) {
  let resultValue = 0;
${conditions}
  return resultValue;
}`;
}

async function ruleMessages(code, filePath, ruleId) {
  const [result] = await eslint.lintText(code, { filePath: path.join(frontendRoot, filePath) });
  return result.messages.filter((message) => message.ruleId === ruleId);
}

async function assertRuleBoundary(
  ruleId,
  atLimitCode,
  overLimitCode,
  filePath = 'src/lint-boundary.ts',
) {
  assert.equal((await ruleMessages(atLimitCode, filePath, ruleId)).length, 0);
  assert.ok((await ruleMessages(overLimitCode, filePath, ruleId)).length > 0);
}

async function run() {
  const productionConfig = await eslint.calculateConfigForFile('src/lint-boundary.ts');
  const testConfig = await eslint.calculateConfigForFile('src/lint-boundary.spec.ts');
  const storyConfig = await eslint.calculateConfigForFile(
    'ethereal-ui/src/stories/support/lint-boundary.ts',
  );

  assert.deepEqual(productionConfig.rules.complexity, [2, { max: 15 }]);
  assert.deepEqual(productionConfig.rules['sonarjs/cognitive-complexity'], [2, 15]);
  assert.equal(productionConfig.rules['max-lines'][1].max, 600);
  assert.equal(testConfig.rules['max-lines'][1].max, 1000);
  assert.equal(storyConfig.rules['max-lines'][1].max, 1000);
  assert.equal(await eslint.isPathIgnored('src/app/api/generated/model/generated.ts'), true);

  await assertRuleBoundary('complexity', sequentialConditions(14), sequentialConditions(15));
  await assertRuleBoundary(
    'sonarjs/cognitive-complexity',
    sequentialConditions(15),
    sequentialConditions(16),
  );
  await assertRuleBoundary(
    'max-lines',
    Array.from({ length: 600 }, (_, index) => `const meaningfulName${index} = ${index};`).join(
      '\n',
    ),
    Array.from({ length: 601 }, (_, index) => `const meaningfulName${index} = ${index};`).join(
      '\n',
    ),
  );
  await assertRuleBoundary(
    'max-lines',
    Array.from({ length: 1000 }, (_, index) => `const meaningfulName${index} = ${index};`).join(
      '\n',
    ),
    Array.from({ length: 1001 }, (_, index) => `const meaningfulName${index} = ${index};`).join(
      '\n',
    ),
    'src/lint-boundary.spec.ts',
  );

  assert.equal(
    (await ruleMessages('const _ = 1;\nconsole.log(_);', 'src/naming.ts', 'id-length')).length,
    0,
  );
  assert.ok(
    (await ruleMessages('const x = 1;\nconsole.log(x);', 'src/naming.ts', 'id-length')).length > 0,
  );
  assert.equal(
    (
      await ruleMessages(
        'const meaningfulObject = { x: 1 };\nconsole.log(meaningfulObject.x);',
        'src/property-name.ts',
        'id-length',
      )
    ).length,
    0,
  );

  console.log('ESLint complexity, file-size, naming, and exclusion self-tests passed.');
}

await run();
