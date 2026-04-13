#!/usr/bin/env node

import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const BASE_URL = process.env.BLIZZARD_BASE_URL ?? 'https://us.api.blizzard.com/data/wow';
const TOKEN_URL = process.env.BLIZZARD_TOKEN_URL ?? 'https://eu.battle.net/oauth/token';
const NAMESPACE = process.env.BLIZZARD_NAMESPACE ?? 'static-us';
const SAMPLE_PER_TIER = parseInt(process.env.PROFESSION_FIXTURE_SAMPLE_SIZE ?? '6', 10);

const ROOT_SELECTIONS = {
  profession: {
    sampleSize: SAMPLE_PER_TIER,
    professions: [
      { id: 164, name: 'Blacksmithing', skillTierIds: [2907, 2751] },
      { id: 333, name: 'Enchanting', skillTierIds: [2909, 2753] },
      { id: 182, name: 'Herbalism', skillTierIds: [2912, 2550] },
      { id: 356, name: 'Fishing', skillTierIds: [2911, 2826] },
    ],
  },
};

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
      if (!Number.isFinite(value) || value < 1) {
        throw new Error('--sample-size must be a positive integer');
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
    '  --sample-size <n>          Recipes to sample per skill tier (default from config)',
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
  return {
    baseResources: path.join(repoRoot, 'src/test/resources/blizzard'),
    manifestFile: path.join(repoRoot, 'src/test/resources/blizzard/profession-recipe-sample-manifest.json'),
  };
}

function resolveSelectedProfessions(args, selectionConfig = ROOT_SELECTIONS.profession) {
  const selectedProfessions = selectionConfig.professions.filter(
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

export function normalizeEndpointPath(input) {
  const url = typeof input === 'string' ? new URL(input) : input;
  const prefix = '/data/wow/';

  if (!url.pathname.startsWith(prefix)) {
    return null;
  }

  return url.pathname.slice(prefix.length).replace(/^\/+|\/+$/g, '');
}

export function endpointPathToFixturePath(endpointPath, baseResources) {
  const parts = endpointPath.split('/').filter(Boolean);
  if (parts.length === 0) {
    throw new Error(`Cannot map endpoint path: ${endpointPath}`);
  }
  const fileName = `${parts.at(-1)}-response.json`;
  return path.join(baseResources, ...parts.slice(0, -1), fileName);
}

function shouldDiscoverHref(pathParts) {
  if (pathParts.length < 2) {
    return false;
  }
  const parent = pathParts.at(-2);
  const current = pathParts.at(-1);
  return parent === 'key' && current === 'href';
}

function isExcludedEndpoint(endpointPath) {
  return endpointPath.startsWith('media/');
}

export function collectLinkedEndpointPaths(payload) {
  const discovered = new Set();

  function visit(value, pathParts) {
    if (Array.isArray(value)) {
      value.forEach((entry, index) => visit(entry, pathParts.concat(String(index))));
      return;
    }

    if (!value || typeof value !== 'object') {
      return;
    }

    for (const [key, nested] of Object.entries(value)) {
      const nextPath = pathParts.concat(key);

      if (typeof nested === 'string' && shouldDiscoverHref(nextPath) && !nextPath.includes('_links')) {
        const endpointPath = normalizeEndpointPath(nested);
        if (endpointPath && !isExcludedEndpoint(endpointPath)) {
          discovered.add(endpointPath);
        }
      }

      visit(nested, nextPath);
    }
  }

  visit(payload, []);
  return [...discovered].sort();
}

function listJsonFilesRecursive(rootDir) {
  return fs.readdir(rootDir, { withFileTypes: true })
    .then((entries) => Promise.all(entries.map(async (entry) => {
      const fullPath = path.join(rootDir, entry.name);
      if (entry.isDirectory()) {
        return listJsonFilesRecursive(fullPath);
      }
      return entry.isFile() && entry.name.endsWith('.json') ? [fullPath] : [];
    })))
    .then((results) => results.flat())
    .catch((error) => {
      if (error.code === 'ENOENT') {
        return [];
      }
      throw error;
    });
}

export async function planManagedFilePrunes({ managedRoots, desiredFiles, enablePrune }) {
  if (!enablePrune) {
    return [];
  }

  const desiredSet = new Set(desiredFiles);
  const existingByRoot = await Promise.all(managedRoots.map((root) => listJsonFilesRecursive(root)));

  return existingByRoot
    .flat()
    .filter((filePath) => !desiredSet.has(filePath))
    .sort()
    .map((filePath) => ({ filePath, kind: 'delete' }));
}

function describeWriteOperation(filePath, payload) {
  return { filePath, kind: 'write', payload };
}

function addManagedWrite(writesByFile, desiredFiles, filePath, payload) {
  writesByFile.set(filePath, describeWriteOperation(filePath, payload));
  desiredFiles.add(filePath);
}

function addPayloadToTraversal(traversalState, endpointPath, payload) {
  if (traversalState.payloads.has(endpointPath)) {
    return;
  }
  traversalState.payloads.set(endpointPath, payload);
  traversalState.queue.push(endpointPath);
}

async function buildRecursiveEndpointWrites({
  apiClient,
  baseResources,
  rootPayloads,
  shouldFollowEndpoint,
  writesByFile,
  desiredFiles,
}) {
  const traversalState = {
    payloads: new Map(rootPayloads),
    queue: [...rootPayloads.keys()],
    seen: new Set(),
  };
  const discoveredEndpointPaths = new Set(rootPayloads.keys());
  const skippedEndpointPaths = new Set();

  while (traversalState.queue.length > 0) {
    const endpointPath = traversalState.queue.shift();
    if (traversalState.seen.has(endpointPath)) {
      continue;
    }
    traversalState.seen.add(endpointPath);

    let payload = traversalState.payloads.get(endpointPath);
    if (!payload) {
      payload = await apiClient.fetchJson(endpointPath);
      traversalState.payloads.set(endpointPath, payload);
    }

    addManagedWrite(
      writesByFile,
      desiredFiles,
      endpointPathToFixturePath(endpointPath, baseResources),
      payload,
    );

    for (const linkedEndpointPath of collectLinkedEndpointPaths(payload)) {
      if (!shouldFollowEndpoint(linkedEndpointPath)) {
        continue;
      }
      if (traversalState.seen.has(linkedEndpointPath) || traversalState.payloads.has(linkedEndpointPath)) {
        continue;
      }
      try {
        const linkedPayload = await apiClient.fetchJson(linkedEndpointPath);
        addPayloadToTraversal(traversalState, linkedEndpointPath, linkedPayload);
        discoveredEndpointPaths.add(linkedEndpointPath);
      } catch {
        skippedEndpointPaths.add(linkedEndpointPath);
      }
    }
  }

  return {
    discoveredEndpointPaths: [...discoveredEndpointPaths].sort(),
    skippedEndpointPaths: [...skippedEndpointPaths].sort(),
  };
}

export async function buildProfessionFixturePlan({
  apiClient,
  args,
  paths,
  selectionConfig = ROOT_SELECTIONS.profession,
}) {
  const sampleSize = args.sampleSize ?? selectionConfig.sampleSize;
  const selectedProfessions = resolveSelectedProfessions(args, selectionConfig);
  const allowlistedProfessionIds = new Set(selectedProfessions.map((profession) => profession.id));
  const writesByFile = new Map();
  const desiredFiles = new Set([paths.manifestFile]);
  const rootPayloads = new Map();
  const sampledRecipeIds = new Set();
  const sampleManifest = [];

  const professionIndex = await apiClient.fetchJson('profession/index');
  const filteredIndex = {
    ...professionIndex,
    professions: (professionIndex.professions ?? []).filter((profession) =>
      allowlistedProfessionIds.has(profession.id),
    ),
  };
  rootPayloads.set('profession/index', filteredIndex);

  for (const profession of selectedProfessions) {
    const professionPayload = await apiClient.fetchJson(`profession/${profession.id}`);
    const downloadedTierIds = [];

    for (const skillTierId of profession.skillTierIds) {
      const skillTierEndpointPath = `profession/${profession.id}/skill-tier/${skillTierId}`;
      const tierPayload = await apiClient.fetchJson(skillTierEndpointPath);
      const chosenRecipes = pickRecipeIds(tierPayload, sampleSize);
      const trimmedTierPayload = trimSkillTierToRecipes(
        tierPayload,
        chosenRecipes.map((recipe) => recipe.id),
      );

      downloadedTierIds.push(skillTierId);
      rootPayloads.set(skillTierEndpointPath, trimmedTierPayload);

      sampleManifest.push({
        professionId: profession.id,
        professionName: professionPayload.name ?? 'unknown',
        skillTierId,
        skillTierName: tierPayload.name ?? 'unknown',
        recipes: chosenRecipes,
      });

      for (const recipe of chosenRecipes) {
        sampledRecipeIds.add(recipe.id);
      }
    }

    const trimmedProfessionPayload = trimProfessionToSkillTiers(professionPayload, downloadedTierIds);
    rootPayloads.set(`profession/${profession.id}`, trimmedProfessionPayload);
  }

  for (const recipeId of [...sampledRecipeIds].sort((left, right) => left - right)) {
    const recipeEndpointPath = `recipe/${recipeId}`;
    const recipePayload = await apiClient.fetchJson(recipeEndpointPath);
    rootPayloads.set(recipeEndpointPath, recipePayload);
  }

  const boundedRootEndpointPaths = new Set(
    [...rootPayloads.keys()].filter((endpointPath) => {
      const family = endpointPath.split('/')[0];
      return family === 'profession' || family === 'recipe';
    }),
  );

  const { discoveredEndpointPaths, skippedEndpointPaths } = await buildRecursiveEndpointWrites({
    apiClient,
    baseResources: paths.baseResources,
    desiredFiles,
    rootPayloads,
    shouldFollowEndpoint: (endpointPath) => {
      if (isExcludedEndpoint(endpointPath)) {
        return false;
      }
      const family = endpointPath.split('/')[0];
      if (family === 'profession' || family === 'recipe') {
        return boundedRootEndpointPaths.has(endpointPath);
      }
      return true;
    },
    writesByFile,
  });

  addManagedWrite(writesByFile, desiredFiles, paths.manifestFile, sampleManifest);

  const fullSelection = !args.professionIds;
  const managedRootDirs = [...new Set(discoveredEndpointPaths.map((endpointPath) =>
    path.join(paths.baseResources, endpointPath.split('/')[0]),
  ))];

  const deletes = await planManagedFilePrunes({
    managedRoots: managedRootDirs,
    desiredFiles: [...desiredFiles],
    enablePrune: fullSelection,
  });

  const familyCounts = discoveredEndpointPaths.reduce((counts, endpointPath) => {
    const family = endpointPath.split('/')[0];
    counts[family] = (counts[family] ?? 0) + 1;
    return counts;
  }, {});

  return {
    deletes,
    meta: {
      discoveredEndpointPaths,
      fullSelection,
      sampledRecipeIds: [...sampledRecipeIds].sort((left, right) => left - right),
      selectedProfessions,
      skippedEndpointPaths,
    },
    summary: {
      families: familyCounts,
      professions: selectedProfessions.length,
      recipes: sampledRecipeIds.size,
      resources: discoveredEndpointPaths.length,
      skipped: skippedEndpointPaths.length,
      skillTiers: sampleManifest.length,
    },
    writes: [...writesByFile.values()],
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
  const dirs = new Set(plan.writes.map((operation) => path.dirname(operation.filePath)));

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
  const familySummary = Object.entries(plan.summary.families)
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([family, count]) => `${family}=${count}`)
    .join(', ');

  const summary =
    `completed: professions=${plan.summary.professions}, tiers=${plan.summary.skillTiers}, ` +
    `recipes=${plan.summary.recipes}, resources=${plan.summary.resources}, skipped=${plan.summary.skipped}`;

  if (!plan.meta.fullSelection) {
    return `${summary}, prune=skipped-for-filtered-selection, families=[${familySummary}]`;
  }

  return `${summary}, families=[${familySummary}]`;
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
