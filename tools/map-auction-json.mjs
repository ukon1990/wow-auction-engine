#!/usr/bin/env node

import { gunzipSync } from 'node:zlib';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

export const DEFAULT_URLS = [
  'https://wah-data-eu.s3.eu-west-1.amazonaws.com/engine/auctions/europe/commodity/1773733732000.json.gz',
  'https://wah-data-eu.s3.eu-west-1.amazonaws.com/engine/auctions/europe/1403/1773733732000.json.gz',
];

export const DEFAULT_SAMPLE_SIZE = Number.POSITIVE_INFINITY;
export const DEFAULT_ENUM_THRESHOLD = 20;
export const DEFAULT_OUT_DIR = path.resolve(process.cwd(), 'target', 'auction-json-map');

export function parseCliArgs(argv) {
  const options = {
    bearerToken: undefined,
    urls: [],
    outDir: DEFAULT_OUT_DIR,
    enumThreshold: DEFAULT_ENUM_THRESHOLD,
    sampleSize: DEFAULT_SAMPLE_SIZE,
  };

  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];

    if (argument === '--url') {
      const value = argv[index + 1];
      if (!value) {
        throw new Error('Missing value for --url');
      }
      options.urls.push(value);
      index += 1;
      continue;
    }

    if (argument === '--bearer-token') {
      const value = argv[index + 1];
      if (!value) {
        throw new Error('Missing value for --bearer-token');
      }
      options.bearerToken = value;
      index += 1;
      continue;
    }

    if (argument === '--out-dir') {
      const value = argv[index + 1];
      if (!value) {
        throw new Error('Missing value for --out-dir');
      }
      options.outDir = path.resolve(value);
      index += 1;
      continue;
    }

    if (argument === '--sample-size') {
      const rawValue = argv[index + 1];
      if (!rawValue) {
        throw new Error('Missing value for --sample-size');
      }

      if (rawValue.toLowerCase() === 'all') {
        options.sampleSize = Number.POSITIVE_INFINITY;
        index += 1;
        continue;
      }

      const value = Number.parseInt(rawValue, 10);
      if (!Number.isInteger(value) || value < 1) {
        throw new Error('--sample-size must be a positive integer or "all"');
      }
      options.sampleSize = value;
      index += 1;
      continue;
    }

    if (argument === '--enum-threshold') {
      const value = Number.parseInt(argv[index + 1], 10);
      if (!Number.isInteger(value) || value < 1) {
        throw new Error('--enum-threshold must be a positive integer');
      }
      options.enumThreshold = value;
      index += 1;
      continue;
    }

    if (argument === '--help' || argument === '-h') {
      options.help = true;
      continue;
    }

    throw new Error(`Unknown argument: ${argument}`);
  }

  if (options.urls.length === 0) {
    options.urls = [...DEFAULT_URLS];
  }

  return options;
}

function printHelp() {
  console.log(`Download WoW auction JSON archives, decompress them, and map their structure.

Usage:
  node tools/map-auction-json.mjs [options]

Options:
  --url <url>            Add a URL to inspect. Can be used multiple times.
  --bearer-token <token> Send an Authorization: Bearer header with each request.
  --out-dir <path>       Output directory. Default: ${DEFAULT_OUT_DIR}
  --sample-size <count>  Number of array elements to inspect per array. Use "all" to inspect every element.
                         Default: all elements
  --enum-threshold <n>   If a primitive field has ${DEFAULT_ENUM_THRESHOLD} or fewer unique values, flag it as an enum candidate.
                         Default: ${DEFAULT_ENUM_THRESHOLD}
  --help, -h             Show this help message.
`);
}

function createEmptyNode() {
  return {
    typeCounts: new Map(),
    primitiveExamples: new Map(),
    primitiveDistinctValues: new Map(),
    objectStats: null,
    arrayStats: null,
  };
}

function determineType(value) {
  if (value === null) {
    return 'null';
  }
  if (Array.isArray(value)) {
    return 'array';
  }
  return typeof value;
}

function addPrimitiveExample(node, type, value) {
  if (!['string', 'number', 'boolean'].includes(type)) {
    return;
  }

  const examples = node.primitiveExamples.get(type) ?? [];
  const printable = typeof value === 'string' && value.length > 80
    ? `${value.slice(0, 77)}...`
    : value;

  if (!examples.some((item) => item === printable) && examples.length < 3) {
    examples.push(printable);
    node.primitiveExamples.set(type, examples);
  }
}

function addPrimitiveDistinctValue(node, type, value, options) {
  if (!['string', 'number', 'boolean'].includes(type)) {
    return;
  }

  const entry = node.primitiveDistinctValues.get(type) ?? {
    valuesByKey: new Map(),
  };

  const key = JSON.stringify(value);
  if (!entry.valuesByKey.has(key) && entry.valuesByKey.size < options.enumThreshold + 1) {
    entry.valuesByKey.set(key, value);
  }

  node.primitiveDistinctValues.set(type, entry);
}

function observeNode(node, value, options) {
  const type = determineType(value);
  node.typeCounts.set(type, (node.typeCounts.get(type) ?? 0) + 1);

  if (type === 'object') {
    if (!node.objectStats) {
      node.objectStats = {
        observedObjects: 0,
        properties: new Map(),
      };
    }

    node.objectStats.observedObjects += 1;

    for (const [key, nestedValue] of Object.entries(value)) {
      const property = node.objectStats.properties.get(key) ?? {
        presenceCount: 0,
        node: createEmptyNode(),
      };
      property.presenceCount += 1;
      observeNode(property.node, nestedValue, options);
      node.objectStats.properties.set(key, property);
    }

    return;
  }

  if (type === 'array') {
    if (!node.arrayStats) {
      node.arrayStats = {
        observedArrays: 0,
        minLength: Number.POSITIVE_INFINITY,
        maxLength: 0,
        sampledItems: 0,
        itemNode: createEmptyNode(),
      };
    }

    node.arrayStats.observedArrays += 1;
    node.arrayStats.minLength = Math.min(node.arrayStats.minLength, value.length);
    node.arrayStats.maxLength = Math.max(node.arrayStats.maxLength, value.length);

    const inspectCount = Math.min(value.length, options.sampleSize);
    node.arrayStats.sampledItems += inspectCount;
    for (let index = 0; index < inspectCount; index += 1) {
      observeNode(node.arrayStats.itemNode, value[index], options);
    }

    return;
  }

  addPrimitiveDistinctValue(node, type, value, options);
  addPrimitiveExample(node, type, value);
}

function sortObjectKeys(input) {
  return Object.fromEntries(Object.entries(input).sort(([left], [right]) => left.localeCompare(right)));
}

function renderEnumCandidate(node, types, options) {
  if (node.objectStats || node.arrayStats) {
    return null;
  }

  const nonNullPrimitiveTypes = types.filter((type) => ['string', 'number', 'boolean'].includes(type));
  if (nonNullPrimitiveTypes.length !== 1) {
    return null;
  }

  const primitiveType = nonNullPrimitiveTypes[0];
  const tracked = node.primitiveDistinctValues.get(primitiveType);
  if (!tracked) {
    return null;
  }

  const values = [...tracked.valuesByKey.values()];
  if (values.length === 0 || values.length > options.enumThreshold) {
    return null;
  }

  return {
    nullable: types.includes('null'),
    type: primitiveType,
    uniqueCount: values.length,
    values,
  };
}

function renderNode(node, options) {
  const types = [...node.typeCounts.keys()].sort();
  const rendered = types.length === 1 ? { type: types[0] } : { types };

  if (node.primitiveExamples.size > 0) {
    const examples = {};
    for (const [type, values] of [...node.primitiveExamples.entries()].sort(([left], [right]) => left.localeCompare(right))) {
      examples[type] = values;
    }
    rendered.examples = examples;
  }

  if (node.objectStats) {
    const properties = {};
    const requiredKeys = [];
    const optionalKeys = [];

    for (const [key, property] of [...node.objectStats.properties.entries()].sort(([left], [right]) => left.localeCompare(right))) {
      properties[key] = renderNode(property.node, options);
      if (property.presenceCount === node.objectStats.observedObjects) {
        requiredKeys.push(key);
      } else {
        optionalKeys.push(key);
      }
    }

    rendered.objectCountSampled = node.objectStats.observedObjects;
    rendered.requiredKeys = requiredKeys;
    if (optionalKeys.length > 0) {
      rendered.optionalKeys = optionalKeys;
    }
    rendered.properties = properties;
  }

  if (node.arrayStats) {
    rendered.arrayCountSampled = node.arrayStats.observedArrays;
    rendered.length = {
      min: Number.isFinite(node.arrayStats.minLength) ? node.arrayStats.minLength : 0,
      max: node.arrayStats.maxLength,
    };
    rendered.sampledItems = node.arrayStats.sampledItems;
    rendered.items = renderNode(node.arrayStats.itemNode, options);
  }

  const enumCandidate = renderEnumCandidate(node, types, options);
  if (enumCandidate) {
    rendered.enumCandidate = enumCandidate;
  }

  return sortObjectKeys(rendered);
}

export function createSchema(value, options = {}) {
  const effectiveOptions = {
    enumThreshold: options.enumThreshold ?? DEFAULT_ENUM_THRESHOLD,
    sampleSize: options.sampleSize ?? DEFAULT_SAMPLE_SIZE,
  };

  const root = createEmptyNode();
  observeNode(root, value, effectiveOptions);
  return renderNode(root, effectiveOptions);
}

function formatEnumValues(values) {
  return values.map((value) => JSON.stringify(value)).join(', ');
}

function describeNode(node) {
  const typeLabel = node.type ?? node.types.join('|');

  if (typeLabel === 'array') {
    return `${typeLabel} (length: ${node.length.min}..${node.length.max}, sampledItems: ${node.sampledItems})`;
  }

  if (typeLabel === 'object') {
    return `${typeLabel} (requiredKeys: ${node.requiredKeys.length}${node.optionalKeys ? `, optionalKeys: ${node.optionalKeys.length}` : ''})`;
  }

  if (node.enumCandidate) {
    return `${typeLabel} (enumCandidate: ${node.enumCandidate.uniqueCount} values: ${formatEnumValues(node.enumCandidate.values)}${node.enumCandidate.nullable ? '; nullable' : ''})`;
  }

  return typeLabel;
}

export function flattenSchemaPaths(schema) {
  const lines = [];

  function walk(node, currentPath) {
    lines.push(`${currentPath}: ${describeNode(node)}`);

    if (node.properties) {
      for (const [key, child] of Object.entries(node.properties)) {
        walk(child, `${currentPath}.${key}`);
      }
    }

    if (node.items) {
      walk(node.items, `${currentPath}[]`);
    }
  }

  walk(schema, '$');
  return lines;
}

export function collectEnumCandidates(schema) {
  const candidates = [];

  function walk(node, currentPath) {
    if (node.enumCandidate) {
      candidates.push({
        path: currentPath,
        ...node.enumCandidate,
      });
    }

    if (node.properties) {
      for (const [key, child] of Object.entries(node.properties)) {
        walk(child, `${currentPath}.${key}`);
      }
    }

    if (node.items) {
      walk(node.items, `${currentPath}[]`);
    }
  }

  walk(schema, '$');
  return candidates;
}

export function formatEnumCandidates(candidates) {
  if (candidates.length === 0) {
    return ['No enum candidates found.'];
  }

  return candidates.map((candidate) => `${candidate.path}: ${candidate.type} (${candidate.uniqueCount} values${candidate.nullable ? ', nullable' : ''}) -> ${formatEnumValues(candidate.values)}`);
}

export function decompressIfNeeded(buffer, url) {
  if (url.endsWith('.gz')) {
    return gunzipSync(buffer);
  }
  return buffer;
}

export async function downloadBuffer(url, options = {}) {
  const headers = {};
  if (options.bearerToken) {
    headers.Authorization = `Bearer ${options.bearerToken}`;
  }

  const response = await fetch(url, {
    headers,
  });
  if (!response.ok) {
    throw new Error(`Failed to download ${url}: ${response.status} ${response.statusText}`);
  }

  const arrayBuffer = await response.arrayBuffer();
  return Buffer.from(arrayBuffer);
}

function safeLabelFromUrl(urlText) {
  const url = new URL(urlText);
  const segments = url.pathname.split('/').filter(Boolean);
  const folder = segments.at(-2) ?? 'data';
  const fileName = segments.at(-1) ?? 'payload';
  const stem = fileName
    .replace(/\.json\.gz$/i, '')
    .replace(/\.gz$/i, '')
    .replace(/\.json$/i, '');

  return `${folder}-${stem}`.replace(/[^a-zA-Z0-9._-]+/g, '_');
}

export async function processUrl(url, options = {}) {
  const effectiveOptions = {
    enumThreshold: options.enumThreshold ?? DEFAULT_ENUM_THRESHOLD,
    outDir: options.outDir ?? DEFAULT_OUT_DIR,
    sampleSize: options.sampleSize ?? DEFAULT_SAMPLE_SIZE,
  };

  const label = safeLabelFromUrl(url);
  const itemOutDir = path.join(effectiveOptions.outDir, label);
  await mkdir(itemOutDir, { recursive: true });

  const compressed = await downloadBuffer(url, effectiveOptions);
  const decompressed = decompressIfNeeded(compressed, url);
  const text = decompressed.toString('utf8');
  const data = JSON.parse(text);
  const schema = createSchema(data, {
    enumThreshold: effectiveOptions.enumThreshold,
    sampleSize: effectiveOptions.sampleSize,
  });
  const paths = flattenSchemaPaths(schema);
  const enumCandidates = collectEnumCandidates(schema);
  const enumCandidateLines = formatEnumCandidates(enumCandidates);

  const compressedName = path.basename(new URL(url).pathname);
  const decompressedName = compressedName.endsWith('.gz') ? compressedName.slice(0, -3) : `${compressedName}.json`;

  const compressedPath = path.join(itemOutDir, compressedName);
  const decompressedPath = path.join(itemOutDir, decompressedName);
  const schemaPath = path.join(itemOutDir, 'structure.json');
  const pathsPath = path.join(itemOutDir, 'structure-paths.txt');
  const enumCandidatesPath = path.join(itemOutDir, 'enum-candidates.json');
  const enumCandidatesTextPath = path.join(itemOutDir, 'enum-candidates.txt');

  await writeFile(compressedPath, compressed);
  await writeFile(decompressedPath, text, 'utf8');
  await writeFile(schemaPath, `${JSON.stringify(schema, null, 2)}\n`, 'utf8');
  await writeFile(pathsPath, `${paths.join('\n')}\n`, 'utf8');
  await writeFile(enumCandidatesPath, `${JSON.stringify(enumCandidates, null, 2)}\n`, 'utf8');
  await writeFile(enumCandidatesTextPath, `${enumCandidateLines.join('\n')}\n`, 'utf8');

  return {
    url,
    label,
    compressedPath,
    decompressedPath,
    schemaPath,
    pathsPath,
    enumCandidatesPath,
    enumCandidatesTextPath,
  };
}

export async function main(argv = process.argv.slice(2)) {
  const options = parseCliArgs(argv);

  if (options.help) {
    printHelp();
    return;
  }

  await mkdir(options.outDir, { recursive: true });

  const results = [];
  for (const url of options.urls) {
    console.log(`Processing ${url}`);
    const result = await processUrl(url, options);
    results.push(result);
    console.log(`  Saved compressed archive to ${result.compressedPath}`);
    console.log(`  Saved decompressed JSON to ${result.decompressedPath}`);
    console.log(`  Saved schema to ${result.schemaPath}`);
    console.log(`  Saved flattened paths to ${result.pathsPath}`);
    console.log(`  Saved enum candidates to ${result.enumCandidatesPath}`);
    console.log(`  Saved enum candidate summary to ${result.enumCandidatesTextPath}`);
  }

  const indexPath = path.join(options.outDir, 'index.json');
  await writeFile(indexPath, `${JSON.stringify({ generatedAt: new Date().toISOString(), results }, null, 2)}\n`, 'utf8');
  console.log(`Wrote run index to ${indexPath}`);
}

const launchedDirectly = process.argv[1]
  ? import.meta.url === pathToFileURL(process.argv[1]).href
  : false;

if (launchedDirectly) {
  main().catch((error) => {
    console.error(error instanceof Error ? error.message : error);
    process.exitCode = 1;
  });
}

