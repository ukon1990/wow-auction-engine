#!/usr/bin/env node

import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

import { downloadBuffer, decompressIfNeeded } from './map-auction-json.mjs';

export const DEFAULT_OUT_DIR = path.resolve(process.cwd(), 'target', 'auction-field-analysis');
export const DEFAULT_FIELD_PATH = 'item.bonus_lists';
export const DEFAULT_TOP_N = 50;

export function parseCliArgs(argv) {
  const options = {
    bearerToken: undefined,
    fieldPath: DEFAULT_FIELD_PATH,
    outDir: DEFAULT_OUT_DIR,
    topN: DEFAULT_TOP_N,
    urls: [],
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

    if (argument === '--field-path') {
      const value = argv[index + 1];
      if (!value) {
        throw new Error('Missing value for --field-path');
      }
      options.fieldPath = value;
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

    if (argument === '--top') {
      const rawValue = argv[index + 1];
      const value = Number.parseInt(rawValue, 10);
      if (!Number.isInteger(value) || value < 1) {
        throw new Error('--top must be a positive integer');
      }
      options.topN = value;
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
    throw new Error('At least one --url is required');
  }

  return options;
}

function printHelp() {
  console.log(`Analyze a field across WoW auction API payloads.

Usage:
  node tools/analyze-auction-field.mjs [options]

Options:
  --url <url>              Add a URL to inspect. Can be used multiple times.
  --bearer-token <token>   Send an Authorization: Bearer header with each request.
  --field-path <path>      Dot path relative to each auction object. Default: ${DEFAULT_FIELD_PATH}
  --out-dir <path>         Output directory. Default: ${DEFAULT_OUT_DIR}
  --top <count>            Number of top values/combinations to emit. Default: ${DEFAULT_TOP_N}
  --help, -h               Show this help message.
`);
}

function safeLabelFromUrl(urlText) {
  const url = new URL(urlText);
  const segments = url.pathname.split('/').filter(Boolean);
  return segments.join('-').replace(/[^a-zA-Z0-9._-]+/g, '_');
}

export function getValueAtPath(source, fieldPath) {
  const segments = fieldPath.split('.').filter(Boolean);
  let current = source;

  for (const segment of segments) {
    if (current === null || current === undefined || typeof current !== 'object' || !(segment in current)) {
      return undefined;
    }
    current = current[segment];
  }

  return current;
}

function createCounter() {
  return new Map();
}

function incrementCounter(counter, key, amount = 1) {
  counter.set(key, (counter.get(key) ?? 0) + amount);
}

function toSortedRows(counter, topN, mapper) {
  return [...counter.entries()]
    .sort((left, right) => {
      if (right[1] !== left[1]) {
        return right[1] - left[1];
      }
      return String(left[0]).localeCompare(String(right[0]));
    })
    .slice(0, topN)
    .map(([key, count]) => mapper(key, count));
}

function toSortedNumericRows(counter, mapper) {
  return [...counter.entries()]
    .sort((left, right) => Number(left[0]) - Number(right[0]))
    .map(([key, count]) => mapper(key, count));
}

export function analyzeAuctionField(data, fieldPath, options = {}) {
  const topN = options.topN ?? DEFAULT_TOP_N;
  const auctions = Array.isArray(data.auctions) ? data.auctions : [];

  const valueCounter = createCounter();
  const combinationCounter = createCounter();
  const arrayLengthCounter = createCounter();

  let auctionsWithField = 0;
  let auctionsWithoutField = 0;
  let nullFieldCount = 0;
  let scalarFieldCount = 0;
  let arrayFieldCount = 0;
  let emptyArrayCount = 0;
  let totalExtractedValues = 0;

  for (const auction of auctions) {
    const rawValue = getValueAtPath(auction, fieldPath);

    if (rawValue === undefined) {
      auctionsWithoutField += 1;
      continue;
    }

    auctionsWithField += 1;

    if (rawValue === null) {
      nullFieldCount += 1;
      continue;
    }

    if (Array.isArray(rawValue)) {
      arrayFieldCount += 1;
      incrementCounter(arrayLengthCounter, rawValue.length);

      if (rawValue.length === 0) {
        emptyArrayCount += 1;
        continue;
      }

      const normalizedValues = rawValue.map((value) => {
        incrementCounter(valueCounter, JSON.stringify(value));
        totalExtractedValues += 1;
        return value;
      });

      const comboKey = JSON.stringify([...normalizedValues].sort((left, right) => String(left).localeCompare(String(right))));
      incrementCounter(combinationCounter, comboKey);
      continue;
    }

    scalarFieldCount += 1;
    totalExtractedValues += 1;
    incrementCounter(valueCounter, JSON.stringify(rawValue));
    incrementCounter(combinationCounter, JSON.stringify([rawValue]));
  }

  return {
    fieldPath,
    auctionsAnalyzed: auctions.length,
    auctionsWithField,
    auctionsWithoutField,
    nullFieldCount,
    scalarFieldCount,
    arrayFieldCount,
    emptyArrayCount,
    totalExtractedValues,
    distinctValueCount: valueCounter.size,
    distinctCombinationCount: combinationCounter.size,
    valueCounts: toSortedRows(valueCounter, Number.POSITIVE_INFINITY, (key, count) => ({
      value: JSON.parse(key),
      count,
    })),
    combinationCounts: toSortedRows(combinationCounter, Number.POSITIVE_INFINITY, (key, count) => ({
      values: JSON.parse(key),
      count,
    })),
    topValues: toSortedRows(valueCounter, topN, (key, count) => ({
      value: JSON.parse(key),
      count,
    })),
    topCombinations: toSortedRows(combinationCounter, topN, (key, count) => ({
      values: JSON.parse(key),
      count,
    })),
    arrayLengthDistribution: toSortedNumericRows(arrayLengthCounter, (key, count) => ({
      length: Number(key),
      count,
    })),
  };
}

export function mergeAnalyses(analyses, options = {}) {
  const topN = options.topN ?? DEFAULT_TOP_N;
  if (analyses.length === 0) {
    return null;
  }

  const mergedValueCounter = createCounter();
  const mergedCombinationCounter = createCounter();
  const mergedLengthCounter = createCounter();

  const merged = {
    fieldPath: analyses[0].fieldPath,
    sources: analyses.length,
    auctionsAnalyzed: 0,
    auctionsWithField: 0,
    auctionsWithoutField: 0,
    nullFieldCount: 0,
    scalarFieldCount: 0,
    arrayFieldCount: 0,
    emptyArrayCount: 0,
    totalExtractedValues: 0,
  };

  for (const analysis of analyses) {
    merged.auctionsAnalyzed += analysis.auctionsAnalyzed;
    merged.auctionsWithField += analysis.auctionsWithField;
    merged.auctionsWithoutField += analysis.auctionsWithoutField;
    merged.nullFieldCount += analysis.nullFieldCount;
    merged.scalarFieldCount += analysis.scalarFieldCount;
    merged.arrayFieldCount += analysis.arrayFieldCount;
    merged.emptyArrayCount += analysis.emptyArrayCount;
    merged.totalExtractedValues += analysis.totalExtractedValues;

    for (const row of analysis.valueCounts ?? analysis.topValues) {
      incrementCounter(mergedValueCounter, JSON.stringify(row.value), row.count);
    }
    for (const row of analysis.combinationCounts ?? analysis.topCombinations) {
      incrementCounter(mergedCombinationCounter, JSON.stringify(row.values), row.count);
    }
    for (const row of analysis.arrayLengthDistribution) {
      incrementCounter(mergedLengthCounter, row.length, row.count);
    }
  }

  return {
    ...merged,
    distinctValueCount: mergedValueCounter.size,
    distinctCombinationCount: mergedCombinationCounter.size,
    valueCounts: toSortedRows(mergedValueCounter, Number.POSITIVE_INFINITY, (key, count) => ({
      value: JSON.parse(key),
      count,
    })),
    combinationCounts: toSortedRows(mergedCombinationCounter, Number.POSITIVE_INFINITY, (key, count) => ({
      values: JSON.parse(key),
      count,
    })),
    topValues: toSortedRows(mergedValueCounter, topN, (key, count) => ({
      value: JSON.parse(key),
      count,
    })),
    topCombinations: toSortedRows(mergedCombinationCounter, topN, (key, count) => ({
      values: JSON.parse(key),
      count,
    })),
    arrayLengthDistribution: toSortedNumericRows(mergedLengthCounter, (key, count) => ({
      length: Number(key),
      count,
    })),
  };
}

export function formatAnalysis(analysis, scopeLabel = 'Summary') {
  const lines = [
    `${scopeLabel}`,
    `fieldPath: ${analysis.fieldPath}`,
    `auctionsAnalyzed: ${analysis.auctionsAnalyzed}`,
    `auctionsWithField: ${analysis.auctionsWithField}`,
    `auctionsWithoutField: ${analysis.auctionsWithoutField}`,
    `arrayFieldCount: ${analysis.arrayFieldCount}`,
    `scalarFieldCount: ${analysis.scalarFieldCount}`,
    `nullFieldCount: ${analysis.nullFieldCount}`,
    `emptyArrayCount: ${analysis.emptyArrayCount}`,
    `totalExtractedValues: ${analysis.totalExtractedValues}`,
    `distinctValueCount: ${analysis.distinctValueCount}`,
    `distinctCombinationCount: ${analysis.distinctCombinationCount}`,
    'topValues:',
  ];

  if (analysis.topValues.length === 0) {
    lines.push('  (none)');
  } else {
    for (const row of analysis.topValues) {
      lines.push(`  ${JSON.stringify(row.value)} => ${row.count}`);
    }
  }

  lines.push('topCombinations:');
  if (analysis.topCombinations.length === 0) {
    lines.push('  (none)');
  } else {
    for (const row of analysis.topCombinations) {
      lines.push(`  ${JSON.stringify(row.values)} => ${row.count}`);
    }
  }

  lines.push('arrayLengthDistribution:');
  if (analysis.arrayLengthDistribution.length === 0) {
    lines.push('  (none)');
  } else {
    for (const row of analysis.arrayLengthDistribution) {
      lines.push(`  ${row.length} => ${row.count}`);
    }
  }

  return lines.join('\n');
}

export async function loadAuctionPayload(url, options = {}) {
  const compressed = await downloadBuffer(url, options);
  const decompressed = decompressIfNeeded(compressed, url);
  return JSON.parse(decompressed.toString('utf8'));
}

export async function processUrl(url, options) {
  const payload = await loadAuctionPayload(url, options);
  const analysis = analyzeAuctionField(payload, options.fieldPath, options);
  const label = safeLabelFromUrl(url);
  const outputDir = path.join(options.outDir, label);
  await mkdir(outputDir, { recursive: true });

  const jsonPath = path.join(outputDir, 'field-analysis.json');
  const textPath = path.join(outputDir, 'field-analysis.txt');

  await writeFile(jsonPath, `${JSON.stringify(analysis, null, 2)}\n`, 'utf8');
  await writeFile(textPath, `${formatAnalysis(analysis, `Source: ${url}`)}\n`, 'utf8');

  return {
    url,
    label,
    analysis,
    jsonPath,
    textPath,
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
    console.log(`Analyzing ${url}`);
    const result = await processUrl(url, options);
    results.push(result);
    console.log(`  Saved JSON analysis to ${result.jsonPath}`);
    console.log(`  Saved text analysis to ${result.textPath}`);
  }

  const aggregate = mergeAnalyses(results.map((result) => result.analysis), options);
  const summaryPath = path.join(options.outDir, 'summary.json');
  const summaryTextPath = path.join(options.outDir, 'summary.txt');

  await writeFile(summaryPath, `${JSON.stringify({ generatedAt: new Date().toISOString(), results, aggregate }, null, 2)}\n`, 'utf8');
  if (aggregate) {
    await writeFile(summaryTextPath, `${formatAnalysis(aggregate, 'Aggregate')}\n`, 'utf8');
  }

  console.log(`Wrote aggregate summary to ${summaryPath}`);
  console.log(`Wrote aggregate text summary to ${summaryTextPath}`);
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
