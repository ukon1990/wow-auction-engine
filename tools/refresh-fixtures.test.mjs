import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';

import {
  buildProfessionFixturePlan,
  parseArgs,
  pickRecipeIds,
  planManagedFilePrunes,
  trimProfessionToSkillTiers,
  trimSkillTierToRecipes,
} from './refresh-fixtures.mjs';

function createLocale(value) {
  return { en_US: value };
}

function createReference(id, name, type = 'recipe') {
  return {
    id,
    key: { href: `https://example.test/${type}/${id}` },
    name: createLocale(name),
  };
}

test('parseArgs keeps the current flags and supports resource selection', () => {
  const parsed = parseArgs([
    '--dry-run',
    '--resource',
    'profession',
    '--profession-id',
    '164,333',
    '--sample-size',
    '8',
  ]);

  assert.equal(parsed.dryRun, true);
  assert.equal(parsed.resource, 'profession');
  assert.deepEqual(parsed.professionIds, [164, 333]);
  assert.equal(parsed.sampleSize, 8);
});

test('pickRecipeIds rotates across categories to keep fixture samples varied', () => {
  const selected = pickRecipeIds(
    {
      categories: [
        { name: createLocale('Armor'), recipes: [createReference(1, 'One'), createReference(2, 'Two')] },
        { name: createLocale('Weapons'), recipes: [createReference(3, 'Three'), createReference(4, 'Four')] },
      ],
    },
    3,
  );

  assert.deepEqual(selected.map((recipe) => recipe.id), [1, 3, 2]);
});

test('trimProfessionToSkillTiers keeps only downloaded tier references', () => {
  const trimmed = trimProfessionToSkillTiers(
    {
      id: 164,
      skill_tiers: [
        createReference(2437, 'Kul Tiras', 'skill-tier'),
        createReference(2907, 'Midnight', 'skill-tier'),
        createReference(2751, 'Shadowlands', 'skill-tier'),
      ],
    },
    [2907, 2751],
  );

  assert.deepEqual(
    trimmed.skill_tiers.map((tier) => tier.id),
    [2907, 2751],
  );
});

test('trimSkillTierToRecipes removes unselected recipes and empty categories', () => {
  const trimmed = trimSkillTierToRecipes(
    {
      categories: [
        { name: createLocale('Armor'), recipes: [createReference(10, 'Ten'), createReference(11, 'Eleven')] },
        { name: createLocale('Unused'), recipes: [createReference(12, 'Twelve')] },
      ],
    },
    [11],
  );

  assert.deepEqual(trimmed.categories.map((category) => category.name.en_US), ['Armor']);
  assert.deepEqual(trimmed.categories[0].recipes.map((recipe) => recipe.id), [11]);
});

test('planManagedFilePrunes deletes unmanaged json files during a full refresh', async () => {
  const tempRoot = await fs.mkdtemp(path.join(os.tmpdir(), 'fixture-prune-'));
  const managedDir = path.join(tempRoot, 'details');
  await fs.mkdir(managedDir, { recursive: true });

  const keepFile = path.join(managedDir, 'keep.json');
  const staleFile = path.join(managedDir, 'stale.json');
  await fs.writeFile(keepFile, '{}\n', 'utf8');
  await fs.writeFile(staleFile, '{}\n', 'utf8');

  const deletes = await planManagedFilePrunes({
    managedDirs: [managedDir],
    desiredFiles: [keepFile],
    enablePrune: true,
  });

  assert.deepEqual(deletes, [{ filePath: staleFile, kind: 'delete' }]);
});

test('buildProfessionFixturePlan trims profession tiers, trims skill-tier recipes, and prunes stale files', async () => {
  const tempRoot = await fs.mkdtemp(path.join(os.tmpdir(), 'profession-refresh-'));
  const paths = {
    baseResources: path.join(tempRoot, 'src/test/resources/blizzard'),
    professionRoot: path.join(tempRoot, 'src/test/resources/blizzard/profession'),
    professionDetailsDir: path.join(tempRoot, 'src/test/resources/blizzard/profession/details'),
    skillTierDir: path.join(tempRoot, 'src/test/resources/blizzard/profession/skill-tier'),
    recipeRoot: path.join(tempRoot, 'src/test/resources/blizzard/recipe'),
    recipeDetailsDir: path.join(tempRoot, 'src/test/resources/blizzard/recipe/details'),
    professionIndexFile: path.join(tempRoot, 'src/test/resources/blizzard/profession/index-response.json'),
    manifestFile: path.join(tempRoot, 'src/test/resources/blizzard/profession-recipe-sample-manifest.json'),
  };

  await fs.mkdir(paths.professionDetailsDir, { recursive: true });
  await fs.mkdir(paths.skillTierDir, { recursive: true });
  await fs.mkdir(paths.recipeDetailsDir, { recursive: true });
  await fs.writeFile(path.join(paths.professionDetailsDir, '999-response.json'), '{}\n', 'utf8');
  await fs.writeFile(path.join(paths.skillTierDir, '9999-response.json'), '{}\n', 'utf8');
  await fs.writeFile(path.join(paths.recipeDetailsDir, '99999-response.json'), '{}\n', 'utf8');

  const payloads = new Map([
    [
      'profession/index',
      {
        professions: [
          createReference(164, 'Blacksmithing', 'profession'),
          createReference(333, 'Enchanting', 'profession'),
        ],
      },
    ],
    [
      'profession/164',
      {
        id: 164,
        name: createLocale('Blacksmithing'),
        skill_tiers: [
          createReference(2437, 'Kul Tiran Blacksmithing', 'skill-tier'),
          createReference(2751, 'Shadowlands Blacksmithing', 'skill-tier'),
          createReference(2907, 'Midnight Blacksmithing', 'skill-tier'),
        ],
      },
    ],
    [
      'profession/164/skill-tier/2907',
      {
        id: 2907,
        name: createLocale('Midnight Blacksmithing'),
        categories: [
          {
            name: createLocale('Armor'),
            recipes: [createReference(5001, 'Helm'), createReference(5002, 'Boots')],
          },
          {
            name: createLocale('Weapons'),
            recipes: [createReference(5003, 'Sword')],
          },
        ],
      },
    ],
    [
      'profession/164/skill-tier/2751',
      {
        id: 2751,
        name: createLocale('Shadowlands Blacksmithing'),
        categories: [
          {
            name: createLocale('Optional'),
            recipes: [createReference(5010, 'Optional A'), createReference(5011, 'Optional B')],
          },
        ],
      },
    ],
    ['recipe/5001', { id: 5001, name: createLocale('Helm') }],
    ['recipe/5002', { id: 5002, name: createLocale('Boots') }],
    ['recipe/5003', { id: 5003, name: createLocale('Sword') }],
    ['recipe/5010', { id: 5010, name: createLocale('Optional A') }],
    ['recipe/5011', { id: 5011, name: createLocale('Optional B') }],
  ]);

  const apiClient = {
    async fetchJson(endpointPath) {
      if (!payloads.has(endpointPath)) {
        throw new Error(`Unexpected endpoint: ${endpointPath}`);
      }
      return payloads.get(endpointPath);
    },
  };

  const plan = await buildProfessionFixturePlan({
    apiClient,
    args: {
      dryRun: false,
      professionIds: null,
      resource: 'profession',
      sampleSize: 5,
    },
    definitions: [{ id: 164, name: 'Blacksmithing', tiers: [2907, 2751] }],
    paths,
  });

  const professionWrite = plan.writes.find((operation) => operation.filePath.endsWith('164-response.json'));
  const midnightWrite = plan.writes.find((operation) => operation.filePath.endsWith('2907-response.json'));
  const manifestWrite = plan.writes.find((operation) => operation.filePath.endsWith('profession-recipe-sample-manifest.json'));

  assert.deepEqual(
    professionWrite.payload.skill_tiers.map((tier) => tier.id),
    [2751, 2907],
  );
  assert.deepEqual(
    midnightWrite.payload.categories.map((category) => category.recipes.map((recipe) => recipe.id)),
    [[5001, 5002], [5003]],
  );
  assert.equal(manifestWrite.payload.length, 2);
  assert.deepEqual(
    plan.deletes.map((operation) => path.basename(operation.filePath)).sort(),
    ['999-response.json', '9999-response.json', '99999-response.json'],
  );
  assert.deepEqual(plan.summary, {
    professions: 1,
    recipes: 5,
    skillTiers: 2,
  });
});
