import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';

import {
  buildProfessionFixturePlan,
  collectLinkedEndpointPaths,
  endpointPathToFixturePath,
  normalizeEndpointPath,
  parseArgs,
  pickRecipeIds,
  planManagedFilePrunes,
  trimProfessionToSkillTiers,
  trimSkillTierToRecipes,
} from './refresh-fixtures.mjs';

function createLocale(value) {
  return { en_US: value };
}

function createReference(id, name, endpointPath) {
  return {
    id,
    key: { href: `https://us.api.blizzard.com/data/wow/${endpointPath}?namespace=static-us` },
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

test('normalizeEndpointPath strips the Blizzard base path and query string', () => {
  assert.equal(
    normalizeEndpointPath('https://us.api.blizzard.com/data/wow/profession/164/skill-tier/2907?namespace=static-us'),
    'profession/164/skill-tier/2907',
  );
  assert.equal(normalizeEndpointPath('https://example.test/other/164'), null);
});

test('endpointPathToFixturePath mirrors endpoint segments under blizzard resources', () => {
  const base = 'E:/repo/src/test/resources/blizzard';

  assert.equal(
    endpointPathToFixturePath('profession/index', base),
    path.join(base, 'profession', 'index-response.json'),
  );
  assert.equal(
    endpointPathToFixturePath('profession/164/skill-tier/2907', base),
    path.join(base, 'profession', '164', 'skill-tier', '2907-response.json'),
  );
});

test('pickRecipeIds rotates across categories to keep fixture samples varied', () => {
  const selected = pickRecipeIds(
    {
      categories: [
        { name: createLocale('Armor'), recipes: [createReference(1, 'One', 'recipe/1'), createReference(2, 'Two', 'recipe/2')] },
        { name: createLocale('Weapons'), recipes: [createReference(3, 'Three', 'recipe/3'), createReference(4, 'Four', 'recipe/4')] },
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
        createReference(2437, 'Kul Tiras', 'profession/164/skill-tier/2437'),
        createReference(2907, 'Midnight', 'profession/164/skill-tier/2907'),
        createReference(2751, 'Shadowlands', 'profession/164/skill-tier/2751'),
      ],
    },
    [2907, 2751],
  );

  assert.deepEqual(trimmed.skill_tiers.map((tier) => tier.id), [2907, 2751]);
});

test('trimSkillTierToRecipes removes unselected recipes and empty categories', () => {
  const trimmed = trimSkillTierToRecipes(
    {
      categories: [
        { name: createLocale('Armor'), recipes: [createReference(10, 'Ten', 'recipe/10'), createReference(11, 'Eleven', 'recipe/11')] },
        { name: createLocale('Unused'), recipes: [createReference(12, 'Twelve', 'recipe/12')] },
      ],
    },
    [11],
  );

  assert.deepEqual(trimmed.categories.map((category) => category.name.en_US), ['Armor']);
  assert.deepEqual(trimmed.categories[0].recipes.map((recipe) => recipe.id), [11]);
});

test('collectLinkedEndpointPaths discovers non-media key href links recursively', () => {
  const payload = {
    _links: {
      self: {
        href: 'https://us.api.blizzard.com/data/wow/recipe/5001?namespace=static-us',
      },
    },
    crafted_item: createReference(9001, 'Crafted', 'item/9001'),
    reagents: [
      {
        reagent: createReference(9002, 'Dust', 'item/9002'),
      },
    ],
    modified_crafting_slots: [
      {
        slot_type: createReference(404, 'Eversinging Dust', 'modified-crafting/reagent-slot-type/404'),
      },
    ],
    media: {
      key: {
        href: 'https://us.api.blizzard.com/data/wow/media/recipe/5001?namespace=static-us',
      },
    },
  };

  assert.deepEqual(collectLinkedEndpointPaths(payload), [
    'item/9001',
    'item/9002',
    'modified-crafting/reagent-slot-type/404',
  ]);
});

test('planManagedFilePrunes deletes unmanaged json files recursively during a full refresh', async () => {
  const tempRoot = await fs.mkdtemp(path.join(os.tmpdir(), 'fixture-prune-'));
  const managedDir = path.join(tempRoot, 'profession');
  await fs.mkdir(path.join(managedDir, 'details'), { recursive: true });
  await fs.mkdir(path.join(managedDir, '164', 'skill-tier'), { recursive: true });

  const keepFile = path.join(managedDir, '164', 'skill-tier', '2907-response.json');
  const staleFile = path.join(managedDir, 'details', '164-response.json');
  await fs.writeFile(keepFile, '{}\n', 'utf8');
  await fs.writeFile(staleFile, '{}\n', 'utf8');

  const deletes = await planManagedFilePrunes({
    managedRoots: [managedDir],
    desiredFiles: [keepFile],
    enablePrune: true,
  });

  assert.deepEqual(deletes, [{ filePath: staleFile, kind: 'delete' }]);
});

test('buildProfessionFixturePlan mirrors paths and discovers linked item and slot type resources', async () => {
  const tempRoot = await fs.mkdtemp(path.join(os.tmpdir(), 'profession-refresh-'));
  const paths = {
    baseResources: path.join(tempRoot, 'src/test/resources/blizzard'),
    manifestFile: path.join(tempRoot, 'src/test/resources/blizzard/profession-recipe-sample-manifest.json'),
  };

  await fs.mkdir(path.join(paths.baseResources, 'profession', 'details'), { recursive: true });
  await fs.mkdir(path.join(paths.baseResources, 'recipe', 'details'), { recursive: true });
  await fs.writeFile(path.join(paths.baseResources, 'profession', 'details', '999-response.json'), '{}\n', 'utf8');
  await fs.writeFile(path.join(paths.baseResources, 'recipe', 'details', '999-response.json'), '{}\n', 'utf8');

  const payloads = new Map([
    [
      'profession/index',
      {
        professions: [
          createReference(164, 'Blacksmithing', 'profession/164'),
          createReference(333, 'Enchanting', 'profession/333'),
        ],
      },
    ],
    [
      'profession/164',
      {
        id: 164,
        name: createLocale('Blacksmithing'),
        skill_tiers: [
          createReference(2437, 'Kul Tiran Blacksmithing', 'profession/164/skill-tier/2437'),
          createReference(2751, 'Shadowlands Blacksmithing', 'profession/164/skill-tier/2751'),
          createReference(2907, 'Midnight Blacksmithing', 'profession/164/skill-tier/2907'),
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
            recipes: [createReference(5001, 'Helm', 'recipe/5001'), createReference(5002, 'Boots', 'recipe/5002')],
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
            name: createLocale('Weapons'),
            recipes: [createReference(5003, 'Sword', 'recipe/5003')],
          },
        ],
      },
    ],
    [
      'recipe/5001',
      {
        id: 5001,
        crafted_item: createReference(9001, 'Crafted Helm', 'item/9001'),
        reagents: [{ reagent: createReference(9002, 'Dust', 'item/9002'), quantity: 2 }],
        modified_crafting_slots: [{ slot_type: createReference(404, 'Dust Slot', 'modified-crafting/reagent-slot-type/404') }],
        media: { key: { href: 'https://us.api.blizzard.com/data/wow/media/recipe/5001?namespace=static-us' } },
      },
    ],
    ['recipe/5002', { id: 5002 }],
    ['recipe/5003', { id: 5003 }],
    ['item/9001', { id: 9001, item_class: createReference(2, 'Armor', 'item-class/2') }],
    ['item/9002', { id: 9002 }],
    ['item-class/2', { id: 2 }],
    ['modified-crafting/reagent-slot-type/404', { id: 404 }],
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
      sampleSize: 2,
    },
    paths,
    selectionConfig: {
      sampleSize: 2,
      professions: [{ id: 164, name: 'Blacksmithing', skillTierIds: [2907, 2751] }],
    },
  });

  const writtenFiles = new Set(plan.writes.map((operation) => operation.filePath));

  assert.ok(writtenFiles.has(path.join(paths.baseResources, 'profession', '164-response.json')));
  assert.ok(writtenFiles.has(path.join(paths.baseResources, 'profession', '164', 'skill-tier', '2907-response.json')));
  assert.ok(writtenFiles.has(path.join(paths.baseResources, 'recipe', '5001-response.json')));
  assert.ok(writtenFiles.has(path.join(paths.baseResources, 'item', '9001-response.json')));
  assert.ok(writtenFiles.has(path.join(paths.baseResources, 'item-class', '2-response.json')));
  assert.ok(writtenFiles.has(path.join(paths.baseResources, 'modified-crafting', 'reagent-slot-type', '404-response.json')));

  const professionWrite = plan.writes.find((operation) => operation.filePath.endsWith(path.join('profession', '164-response.json')));
  const skillTierWrite = plan.writes.find((operation) => operation.filePath.endsWith(path.join('profession', '164', 'skill-tier', '2907-response.json')));
  const manifestWrite = plan.writes.find((operation) => operation.filePath.endsWith('profession-recipe-sample-manifest.json'));

  assert.deepEqual(professionWrite.payload.skill_tiers.map((tier) => tier.id), [2751, 2907]);
  assert.deepEqual(skillTierWrite.payload.categories[0].recipes.map((recipe) => recipe.id), [5001, 5002]);
  assert.equal(manifestWrite.payload.length, 2);
  assert.deepEqual(
    plan.deletes.map((operation) => operation.filePath).sort(),
    [
      path.join(paths.baseResources, 'profession', 'details', '999-response.json'),
      path.join(paths.baseResources, 'recipe', 'details', '999-response.json'),
    ],
  );
  assert.equal(plan.summary.professions, 1);
  assert.equal(plan.summary.skillTiers, 2);
  assert.equal(plan.summary.recipes, 3);
  assert.equal(plan.summary.skipped, 0);
  assert.equal(plan.summary.families.item, 2);
  assert.equal(plan.summary.families['modified-crafting'], 1);
});
