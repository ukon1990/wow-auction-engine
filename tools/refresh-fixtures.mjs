#!/usr/bin/env node

import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const BASE_URL = process.env.BLIZZARD_BASE_URL ?? 'https://us.api.blizzard.com/data/wow';
const TOKEN_URL = process.env.BLIZZARD_TOKEN_URL ?? 'https://eu.battle.net/oauth/token';
const NAMESPACE = process.env.BLIZZARD_NAMESPACE ?? 'static-us';
const SAMPLE_PER_TIER = parseInt(process.env.PROFESSION_FIXTURE_SAMPLE_SIZE ?? '6', 10);

const TARGET_PROFESSIONS = [
  { id: 164, name: 'Blacksmithing', tiers: [2907, 2751] },
  { id: 333, name: 'Enchanting', tiers: [2909, 2753] },
  { id: 182, name: 'Herbalism', tiers: [2912, 2550] },
  { id: 356, name: 'Fishing', tiers: [2911, 2826] },
];

const RESOURCE_DEFINITIONS = {
  profession: createProfessionResourceDefinition(),
};

export function parseArgs(argv) {
  const args = {
    dryRun: false,
    help: false,
    professionIds: null,
    resource: 'profession',
    sampleSize: SAMPLE_PER_TIER,
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];

    if (arg === '--dry-run') {
      args.dryRun = true;
    } else if (arg === '--help' || arg === '-h') {
      args.help = true;
    } else if (arg === '--profession-id') {
      const value = argv[index + 1];
      if (!value) {
        throw new Error('Missing value for --profession-id');
      }
      index += 1;
      args.professionIds = (args.professionIds ?? []).concat(
        value
          .split(',')
          .map((entry) => parseInt(entry.trim(), 10))
          .filter((entry) => Number.isFinite(entry)),
      );
    } else if (arg === '--resource') {
      const value = argv[index + 1];
      if (!value) {
        throw new Error('Missing value for --resource');
      }
      index += 1;
      args.resource = value.trim();
    } else if (arg === '--sample-size') {
      const value = parseInt(argv[index + 1] ?? '', 10);
      if (!Number.isFinite(value) || value < 5 || value > 10) {
        throw new Error('--sample-size must be an integer in range 5..10');
      }
      index += 1;
      args.sampleSize = value;
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }

  return args;
}

export function formatHelpText() {
  return [
    'Usage: node ./tools/refresh-fixtures.mjs [options]',
    '',
    'Options:',
    '  --dry-run                  Show planned writes/deletes without modifying files',
    '  --resource <name>          Resource definition to run (default: profession)',
    '  --profession-id <ids>      Comma-separated profession ids to refresh',
    '  --sample-size <5..10>      Recipes to sample per skill tier (default: 6)',
    '  --help, -h                 Show this help',
  ].join('\n');
}

function createProfessionResourceDefinition() {
  return {
    name: 'profession',
    async buildPlan(context) {
      return buildProfessionFixturePlan(context);
    },
  };
}

function createApiClient({ fetchImpl = fetch, tokenProvider = fetchAccessToken } = {}) {
  let cachedTokenPromise;

  return {
    async fetchJson(endpointPath) {
      cachedTokenPromise ??= tokenProvider({ fetchImpl });
      const token = await cachedTokenPromise;
      return getJson(endpointPath, token, { fetchImpl });
    },
  };
}

async function fetchAccessToken({ fetchImpl = fetch } = {}) {
  const directToken = process.env.BLIZZARD_ACCESS_TOKEN;
  if (directToken) {
    return directToken;
  }

  const clientId = process.env.BLIZZARD_CLIENT_ID;
  const clientSecret = process.env.BLIZZARD_CLIENT_SECRET;
  if (!clientId || !clientSecret) {
    throw new Error(
      'Missing Blizzard credentials. Set BLIZZARD_ACCESS_TOKEN or BLIZZARD_CLIENT_ID + BLIZZARD_CLIENT_SECRET.',
    );
  }

  const body = new URLSearchParams({
    grant_type: 'client_credentials',
    client_id: clientId,
    client_secret: clientSecret,
  });

  const response = await fetchImpl(TOKEN_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body,
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(`Failed to refresh token (${response.status}): ${text}`);
  }

  const payload = await response.json();
  if (!payload.access_token) {
    throw new Error('Token response did not include access_token');
  }
  return payload.access_token;
}

async function getJson(endpointPath, token, { fetchImpl = fetch } = {}) {
  const url = new URL(`${BASE_URL}/${endpointPath.replace(/^\//, '')}`);
  url.searchParams.set('namespace', NAMESPACE);

  const response = await fetchImpl(url, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(`GET ${url} failed (${response.status}): ${text}`);
  }

  return response.json();
}

function buildPaths(repoRoot) {
  const baseResources = path.join(repoRoot, 'src/test/resources/blizzard');
  const professionRoot = path.join(baseResources, 'profession');
  const professionDetailsDir = path.join(professionRoot, 'details');
  const skillTierDir = path.join(professionRoot, 'skill-tier');
  const recipeRoot = path.join(baseResources, 'recipe');
  const recipeDetailsDir = path.join(recipeRoot, 'details');

  return {
    baseResources,
    professionRoot,
    professionDetailsDir,
    skillTierDir,
    recipeRoot,
    recipeDetailsDir,
    professionIndexFile: path.join(professionRoot, 'index-response.json'),
    manifestFile: path.join(baseResources, 'profession-recipe-sample-manifest.json'),
  };
}

function resolveSelectedProfessions(args, definitions = TARGET_PROFESSIONS) {
  const selectedProfessions = definitions.filter(
    (profession) => !args.professionIds || args.professionIds.includes(profession.id),
  );
  if (selectedProfessions.length === 0) {
    throw new Error('No professions selected.');
  }
  return selectedProfessions;
}

export function pickRecipeIds(skillTierPayload, sampleSize) {
  const categories = (skillTierPayload.categories ?? [])
    .map((category) => ({
      categoryName: category.name ?? 'unknown',
      recipes: category.recipes ?? [],
    }))
    .filter((category) => category.recipes.length > 0);

  if (categories.length === 0) {
    return [];
  }

  const selected = [];
  const used = new Set();
  let index = 0;
  while (selected.length < sampleSize) {
    let addedInRound = false;
    for (const category of categories) {
      if (index >= category.recipes.length) {
        continue;
      }
      const recipe = category.recipes[index];
      if (!recipe || used.has(recipe.id)) {
        continue;
      }
      selected.push({
        id: recipe.id,
        name: recipe.name ?? 'unknown',
        category: category.categoryName,
      });
      used.add(recipe.id);
      addedInRound = true;
      if (selected.length >= sampleSize) {
        break;
      }
    }
    if (!addedInRound) {
      break;
    }
    index += 1;
  }

  return selected;
}

export function trimSkillTierToRecipes(skillTierPayload, selectedRecipeIds) {
  const selectedSet = new Set(selectedRecipeIds);
  const categories = (skillTierPayload.categories ?? [])
    .map((category) => {
      const recipes = (category.recipes ?? []).filter((recipe) => selectedSet.has(recipe.id));
      return {
        ...category,
        recipes,
      };
    })
    .filter((category) => (category.recipes ?? []).length > 0);

  return {
    ...skillTierPayload,
    categories,
  };
}

export function trimProfessionToSkillTiers(professionPayload, selectedTierIds) {
  const selectedSet = new Set(selectedTierIds);

  return {
    ...professionPayload,
    skill_tiers: (professionPayload.skill_tiers ?? []).filter((tier) => selectedSet.has(tier.id)),
  };
}

function describeDeleteOperation(filePath) {
  return { filePath, kind: 'delete' };
}

function describeWriteOperation(filePath, payload) {
  return { filePath, kind: 'write', payload };
}

async function listJsonFiles(dir) {
  try {
    const entries = await fs.readdir(dir, { withFileTypes: true });
    return entries
      .filter((entry) => entry.isFile() && entry.name.endsWith('.json'))
      .map((entry) => path.join(dir, entry.name));
  } catch (error) {
    if (error.code === 'ENOENT') {
      return [];
    }
    throw error;
  }
}

export async function planManagedFilePrunes({ managedDirs, desiredFiles, enablePrune }) {
  if (!enablePrune) {
    return [];
  }

  const desiredSet = new Set(desiredFiles);
  const existingByDir = await Promise.all(managedDirs.map((dir) => listJsonFiles(dir)));

  return existingByDir
    .flat()
    .filter((filePath) => !desiredSet.has(filePath))
    .sort()
    .map((filePath) => describeDeleteOperation(filePath));
}

export async function buildProfessionFixturePlan({
  apiClient,
  args,
  paths,
  definitions = TARGET_PROFESSIONS,
}) {
  const selectedProfessions = resolveSelectedProfessions(args, definitions);
  const allowlistedProfessionIds = new Set(selectedProfessions.map((profession) => profession.id));

  const professionIndex = await apiClient.fetchJson('profession/index');
  const filteredIndex = {
    ...professionIndex,
    professions: (professionIndex.professions ?? []).filter((profession) =>
      allowlistedProfessionIds.has(profession.id),
    ),
  };

  const writes = [describeWriteOperation(paths.professionIndexFile, filteredIndex)];
  const recipeIds = new Set();
  const sampleManifest = [];
  const skillTierByProfessionId = new Map();
  const desiredManagedFiles = new Set([paths.professionIndexFile, paths.manifestFile]);

  for (const profession of selectedProfessions) {
    const downloadedTierIds = [];
    const professionPayload = await apiClient.fetchJson(`profession/${profession.id}`);

    for (const tierId of profession.tiers) {
      const tierPayload = await apiClient.fetchJson(`profession/${profession.id}/skill-tier/${tierId}`);
      const chosenRecipes = pickRecipeIds(tierPayload, args.sampleSize);
      const trimmedTierPayload = trimSkillTierToRecipes(
        tierPayload,
        chosenRecipes.map((recipe) => recipe.id),
      );
      const tierFilePath = path.join(paths.skillTierDir, `${tierId}-response.json`);

      downloadedTierIds.push(tierId);
      writes.push(describeWriteOperation(tierFilePath, trimmedTierPayload));
      desiredManagedFiles.add(tierFilePath);

      sampleManifest.push({
        professionId: profession.id,
        professionName: professionPayload.name ?? 'unknown',
        skillTierId: tierId,
        skillTierName: tierPayload.name ?? 'unknown',
        recipes: chosenRecipes,
      });

      for (const recipe of chosenRecipes) {
        recipeIds.add(recipe.id);
      }
    }

    skillTierByProfessionId.set(profession.id, downloadedTierIds);
    const trimmedProfessionPayload = trimProfessionToSkillTiers(professionPayload, downloadedTierIds);
    const professionFilePath = path.join(paths.professionDetailsDir, `${profession.id}-response.json`);

    writes.push(describeWriteOperation(professionFilePath, trimmedProfessionPayload));
    desiredManagedFiles.add(professionFilePath);
  }

  const orderedRecipeIds = [...recipeIds].sort((left, right) => left - right);
  for (const recipeId of orderedRecipeIds) {
    const recipePayload = await apiClient.fetchJson(`recipe/${recipeId}`);
    const recipeFilePath = path.join(paths.recipeDetailsDir, `${recipeId}-response.json`);
    writes.push(describeWriteOperation(recipeFilePath, recipePayload));
    desiredManagedFiles.add(recipeFilePath);
  }

  writes.push(describeWriteOperation(paths.manifestFile, sampleManifest));

  const fullSelection = !args.professionIds;
  const deletes = await planManagedFilePrunes({
    managedDirs: [paths.professionDetailsDir, paths.skillTierDir, paths.recipeDetailsDir],
    desiredFiles: [...desiredManagedFiles],
    enablePrune: fullSelection,
  });

  return {
    deletes,
    meta: {
      fullSelection,
      orderedRecipeIds,
      selectedProfessions,
      skillTierByProfessionId,
    },
    summary: {
      professions: selectedProfessions.length,
      recipes: orderedRecipeIds.length,
      skillTiers: sampleManifest.length,
    },
    writes,
  };
}

export async function buildRefreshPlan({
  repoRoot = process.cwd(),
  args,
  apiClient = createApiClient(),
  definitions = RESOURCE_DEFINITIONS,
} = {}) {
  const definition = definitions[args.resource];
  if (!definition) {
    throw new Error(`Unknown resource: ${args.resource}`);
  }

  const paths = buildPaths(repoRoot);
  return definition.buildPlan({
    apiClient,
    args,
    paths,
  });
}

async function ensureDir(dirPath, dryRun) {
  if (dryRun) {
    return;
  }
  await fs.mkdir(dirPath, { recursive: true });
}

async function writeJson(filePath, payload, dryRun) {
  const body = `${JSON.stringify(payload, null, 4)}\n`;
  if (dryRun) {
    console.log(`[dry-run] write ${filePath}`);
    return;
  }
  await fs.writeFile(filePath, body, 'utf8');
  console.log(`wrote ${filePath}`);
}

async function deleteFile(filePath, dryRun) {
  if (dryRun) {
    console.log(`[dry-run] delete ${filePath}`);
    return;
  }
  await fs.rm(filePath, { force: true });
  console.log(`deleted ${filePath}`);
}

export async function applyPlan(plan, { dryRun }) {
  const dirs = new Set(
    plan.writes.map((operation) => path.dirname(operation.filePath)),
  );

  for (const dirPath of dirs) {
    await ensureDir(dirPath, dryRun);
  }

  for (const operation of plan.writes) {
    await writeJson(operation.filePath, operation.payload, dryRun);
  }

  for (const operation of plan.deletes) {
    await deleteFile(operation.filePath, dryRun);
  }
}

function formatCompletionSummary(plan) {
  const summary = `completed: professions=${plan.summary.professions}, tiers=${plan.summary.skillTiers}, recipes=${plan.summary.recipes}`;
  if (plan.meta.fullSelection) {
    return summary;
  }
  return `${summary}, prune=skipped-for-filtered-selection`;
}

export async function runCli(argv, { repoRoot = process.cwd(), apiClient = createApiClient() } = {}) {
  const args = parseArgs(argv);
  if (args.help) {
    console.log(formatHelpText());
    return;
  }

  const plan = await buildRefreshPlan({
    apiClient,
    args,
    repoRoot,
  });

  await applyPlan(plan, { dryRun: args.dryRun });
  console.log(formatCompletionSummary(plan));
}

const isDirectExecution = process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1]);

if (isDirectExecution) {
  runCli(process.argv.slice(2)).catch((error) => {
    console.error(error.message);
    process.exit(1);
  });
}
