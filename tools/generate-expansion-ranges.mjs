#!/usr/bin/env node
import { readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';

export const DEFAULT_DATA_URL =
  'https://raw.githubusercontent.com/t-mart/ItemVersion/master/ItemVersion/Data.lua';

export const DEFAULT_OUT = path.resolve(
  process.cwd(),
  'backend',
  'src',
  'main',
  'resources',
  'db',
  'migration',
  'R__seed_item_expansion_ranges.sql',
);

export const EXPANSIONS = [
  { id: 1, slug: 'vanilla', name: 'Vanilla', majorVersion: 1, displayOrder: 10 },
  { id: 2, slug: 'the-burning-crusade', name: 'The Burning Crusade', majorVersion: 2, displayOrder: 20 },
  { id: 3, slug: 'wrath-of-the-lich-king', name: 'Wrath of the Lich King', majorVersion: 3, displayOrder: 30 },
  { id: 4, slug: 'cataclysm', name: 'Cataclysm', majorVersion: 4, displayOrder: 40 },
  { id: 5, slug: 'mists-of-pandaria', name: 'Mists of Pandaria', majorVersion: 5, displayOrder: 50 },
  { id: 6, slug: 'warlords-of-draenor', name: 'Warlords of Draenor', majorVersion: 6, displayOrder: 60 },
  { id: 7, slug: 'legion', name: 'Legion', majorVersion: 7, displayOrder: 70 },
  { id: 8, slug: 'battle-for-azeroth', name: 'Battle for Azeroth', majorVersion: 8, displayOrder: 80 },
  { id: 9, slug: 'shadowlands', name: 'Shadowlands', majorVersion: 9, displayOrder: 90 },
  { id: 10, slug: 'dragonflight', name: 'Dragonflight', majorVersion: 10, displayOrder: 100 },
  { id: 11, slug: 'the-war-within', name: 'The War Within', majorVersion: 11, displayOrder: 110 },
  { id: 12, slug: 'midnight', name: 'Midnight', majorVersion: 12, displayOrder: 120 },
];

const expansionByMajor = new Map(EXPANSIONS.map((expansion) => [expansion.majorVersion, expansion]));

export function parseDataLua(source) {
  const itemMapMatch = source.match(/AddonTable\.itemIdToVersionId\s*=\s*\{(.+?)\}\s*AddonTable\.versionIdToVersion/s);
  const versionMapMatch = source.match(/AddonTable\.versionIdToVersion\s*=\s*\{(.+?)\}\s*$/s);
  if (!itemMapMatch || !versionMapMatch) {
    throw new Error('Unable to find ItemVersion tables in Data.lua');
  }

  const versions = new Map();
  for (const match of versionMapMatch[1].matchAll(/\[(\d+)]=\{major=(\d+),minor=(\d+),patch=(\d+),build=(\d+)}/g)) {
    versions.set(Number(match[1]), {
      major: Number(match[2]),
      minor: Number(match[3]),
      patch: Number(match[4]),
      build: Number(match[5]),
    });
  }

  const itemVersions = [];
  for (const match of itemMapMatch[1].matchAll(/\[(\d+)]=(\d+)/g)) {
    const itemId = Number(match[1]);
    const versionId = Number(match[2]);
    const version = versions.get(versionId);
    if (!version) {
      throw new Error(`Item ${itemId} references unknown version id ${versionId}`);
    }
    const expansion = expansionByMajor.get(version.major);
    if (!expansion) {
      throw new Error(`Item ${itemId} references unsupported major version ${version.major}`);
    }
    itemVersions.push({ itemId, versionId, expansionId: expansion.id, majorVersion: version.major });
  }

  return { versions, itemVersions };
}

export function buildItemExpansionMap(itemVersions) {
    const itemExpansion = new Map();
    for (const { itemId, expansionId } of itemVersions) {
        const existing = itemExpansion.get(itemId);
        if (existing !== undefined && existing !== expansionId) {
            throw new Error(`Item ${itemId} maps to conflicting expansions ${existing} and ${expansionId}`);
        }
        itemExpansion.set(itemId, expansionId);
    }
    return itemExpansion;
}

export function compressRanges(itemVersions) {
    const itemExpansion = buildItemExpansionMap(itemVersions);
    const sortedItems = [...itemExpansion.entries()].sort(([leftId], [rightId]) => leftId - rightId);
    if (sortedItems.length === 0) {
        return [];
    }

    const ranges = [];
    let [startItemId, expansionId] = sortedItems[0];
    let endItemId = startItemId;

    for (let index = 1; index < sortedItems.length; index += 1) {
        const [itemId, itemExpansionId] = sortedItems[index];
        if (itemExpansionId === expansionId) {
            endItemId = itemId;
        } else {
            ranges.push({ expansionId, startItemId, endItemId });
            startItemId = itemId;
            endItemId = itemId;
            expansionId = itemExpansionId;
        }
    }

    ranges.push({ expansionId, startItemId, endItemId });
    const sortedRanges = ranges.sort((a, b) => a.expansionId - b.expansionId || a.startItemId - b.startItemId);
    validateRanges(sortedRanges);
    return sortedRanges;
}

export function validateRanges(ranges) {
    for (const range of ranges) {
        if (range.startItemId > range.endItemId) {
            throw new Error(
                `Invalid range for expansion ${range.expansionId}: start ${range.startItemId} > end ${range.endItemId}`,
            );
        }
    }

    for (let leftIndex = 0; leftIndex < ranges.length; leftIndex += 1) {
        const left = ranges[leftIndex];
        for (let rightIndex = leftIndex + 1; rightIndex < ranges.length; rightIndex += 1) {
            const right = ranges[rightIndex];
            if (left.expansionId === right.expansionId) {
                continue;
            }
            if (left.startItemId <= right.endItemId && right.startItemId <= left.endItemId) {
                throw new Error(
                    `Overlapping ranges for expansions ${left.expansionId} [${left.startItemId}, ${left.endItemId}] and ${right.expansionId} [${right.startItemId}, ${right.endItemId}]`,
                );
            }
        }
    }
}

export function renderSql(ranges, generatedAt = new Date()) {
  const expansionRows = EXPANSIONS.map(
    (expansion) =>
      `    (${expansion.id}, '${escapeSql(expansion.slug)}', '${escapeSql(expansion.name)}', ${expansion.majorVersion}, ${expansion.displayOrder})`,
  ).join(',\n');

  const rangeRows = ranges
    .map(
      (range) =>
        `    (${range.expansionId}, ${range.startItemId}, ${range.endItemId}, 'itemversion', TRUE, 'Generated from ItemVersion/Data.lua')`,
    )
    .join(',\n');

  return `-- Generated by tools/generate-expansion-ranges.mjs at ${generatedAt.toISOString()}.
-- Do not hand-edit source='itemversion' rows. Use source='manual' for local/admin overrides.

INSERT INTO expansion (id, slug, name, major_version, display_order)
VALUES
${expansionRows}
ON DUPLICATE KEY UPDATE
    slug = VALUES(slug),
    name = VALUES(name),
    major_version = VALUES(major_version),
    display_order = VALUES(display_order);

DELETE FROM expansion_item_range
WHERE source = 'itemversion';

${rangeRows ? `INSERT INTO expansion_item_range (expansion_id, start_item_id, end_item_id, source, enabled, note)\nVALUES\n${rangeRows};` : '-- No generated expansion item ranges.'}
`;
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const source = options.input ? await readFile(options.input, 'utf8') : await fetchText(options.url ?? DEFAULT_DATA_URL);
  const { versions, itemVersions } = parseDataLua(source);
  const ranges = compressRanges(itemVersions);
  const out = path.resolve(options.out ?? DEFAULT_OUT);
  await writeFile(out, renderSql(ranges), 'utf8');
  console.log(`Wrote ${ranges.length} ranges for ${itemVersions.length} items and ${versions.size} versions to ${out}`);
}

function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === '--input') options.input = args[++index];
    else if (arg === '--url') options.url = args[++index];
    else if (arg === '--out') options.out = args[++index];
    else if (arg === '--help') {
      console.log('Usage: node tools/generate-expansion-ranges.mjs [--input Data.lua] [--url URL] [--out SQL]');
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

async function fetchText(url) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Failed to download ${url}: ${response.status} ${response.statusText}`);
  }
  return response.text();
}

function escapeSql(value) {
  return value.replaceAll("'", "''");
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
}
