import { Decoder } from 'cbor-x';
import { inflateSync } from 'fflate';

import { LuaProcessingError } from './lua-assignment-processor';

const MAX_COMPRESSED_TALENT_BYTES = 16 * 1024 * 1024;
const MAX_INFLATED_TALENT_BYTES = 32 * 1024 * 1024;
const SUPPORTED_SCOPES = new Set([
  'character',
  'profession',
  'profession_talents',
  'professions_talents',
]);
const DANGEROUS_KEYS = new Set(['__proto__', 'prototype', 'constructor']);
const cborDecoder = new Decoder({ mapsAsObjects: false });

export type DecodedProfessionTalents = Readonly<{
  scope: string;
  character: Readonly<{ key: string | null; name: string | null; realm: string | null }>;
  professions: ReadonlyArray<{
    skillLineId: number;
    name: string | null;
    trees: ReadonlyArray<{
      treeId: number;
      skillLineId: number;
      expansionId: number;
      name: string | null;
      tabs: ReadonlyArray<{
        tabId: number;
        name: string | null;
        description: string | null;
        nodes: ReadonlyArray<{
          nodeId: number;
          maxRanks: number | null;
          requiredRank: number | null;
          description: string | null;
          parentNodeIds: readonly number[];
          entries: ReadonlyArray<{
            entryId: number;
            rankLimit: number | null;
            description: string | null;
          }>;
        }>;
      }>;
    }>;
    allocations: ReadonlyArray<{ nodeId: number; entryId: number; rank: number }>;
  }>;
}>;

export function decodeAuctionHelperTalentExport(
  encodedPayload: string,
  wrapperScope: string | null,
): DecodedProfessionTalents {
  if (!encodedPayload.startsWith('AHCBOR1:')) {
    throw new LuaProcessingError('MALFORMED_LUA', 'Talent payload must start with AHCBOR1:.');
  }
  const compressed = decodeBase64(encodedPayload.slice('AHCBOR1:'.length));
  if (compressed.byteLength > MAX_COMPRESSED_TALENT_BYTES) {
    throw new LuaProcessingError(
      'INPUT_LIMIT_EXCEEDED',
      'Compressed talent export limit exceeded.',
    );
  }
  let inflated: Uint8Array;
  try {
    inflated = inflateSync(compressed, { out: new Uint8Array(MAX_INFLATED_TALENT_BYTES) });
  } catch (cause) {
    throw new LuaProcessingError(
      'MALFORMED_LUA',
      cause instanceof Error ? cause.message : 'Unable to decompress talent export.',
    );
  }
  if (inflated.byteLength > MAX_INFLATED_TALENT_BYTES) {
    throw new LuaProcessingError('INPUT_LIMIT_EXCEEDED', 'Inflated talent export limit exceeded.');
  }
  let decoded: unknown;
  try {
    decoded = sanitizeDecodedValue(cborDecoder.decode(inflated), 0, { count: 0 });
  } catch (cause) {
    if (cause instanceof LuaProcessingError) throw cause;
    throw new LuaProcessingError(
      'MALFORMED_LUA',
      cause instanceof Error ? cause.message : 'Unable to decode talent export.',
    );
  }
  const root = object(decoded);
  const meta = object(root['meta']);
  const scope = string(meta['scope']);
  if (!scope || !SUPPORTED_SCOPES.has(scope)) {
    throw new LuaProcessingError(
      'UNSUPPORTED_LUA',
      `Unsupported AuctionHelper export scope: ${scope ?? 'missing'}.`,
    );
  }
  if (wrapperScope !== null && wrapperScope !== scope) {
    throw new LuaProcessingError(
      'MALFORMED_LUA',
      'AuctionHelperLastExport scope does not match decoded meta.scope.',
    );
  }
  const characterRoot = object(root['character']);
  const characterValue = scope === 'character' ? object(characterRoot['meta']) : characterRoot;
  const professions =
    scope === 'character'
      ? collection(characterRoot['professions'])
      : scope === 'profession_talents' || scope === 'profession'
        ? [root['profession']]
        : collection(root['professions']);
  return {
    scope,
    character: {
      key: string(characterValue['key']) ?? string(meta['characterKey']),
      name: string(characterValue['name']),
      realm: string(characterValue['realm']),
    },
    professions: professions.map(normalizeProfession).filter(isPresent),
  };
}

function normalizeProfession(
  value: unknown,
): DecodedProfessionTalents['professions'][number] | null {
  const profession = object(value);
  const skillLineId = integer(profession['skillLineID']);
  if (skillLineId === null) return null;
  const specializations = collection(profession['specializationTrees']);
  const primary = object(profession['specializationTree']);
  if (specializations.length === 0 && Object.keys(primary).length > 0)
    specializations.push(primary);
  const trees = specializations.flatMap((specializationValue) => {
    const specialization = object(specializationValue);
    const configId = integer(specialization['configID']);
    const tierSkillLineId = integer(specialization['skillLineID']);
    const expansionId = normalizedExpansionId(specialization);
    if (configId === null || tierSkillLineId === null || expansionId === null) return [];
    const tabs = collection(specialization['tabs']).flatMap((tabValue) => {
      const tab = object(tabValue);
      const tabId = integer(tab['treeID']);
      if (tabId === null) return [];
      const sourceNodes = collection(tab['nodes']);
      const parentNodeIds = parentNodeIdsByChild(sourceNodes);
      return [
        {
          tabId,
          name: string(object(tab['tabInfo'])['name']),
          description: string(object(tab['tabInfo'])['description']),
          nodes: sourceNodes.map((node) => normalizeNode(node, parentNodeIds)).filter(isPresent),
        },
      ];
    });
    return [
      {
        treeId: configId,
        skillLineId: tierSkillLineId,
        expansionId,
        name: string(specialization['tierName']),
        tabs,
      },
    ];
  });
  const allocations = specializations.flatMap((specializationValue) =>
    collection(object(specializationValue)['tabs']).flatMap((tabValue) =>
      collection(object(tabValue)['nodes']).map(allocationFromNode).filter(isPresent),
    ),
  );
  return {
    skillLineId,
    name: string(profession['professionName']) ?? string(profession['currentLevelName']),
    trees,
    allocations,
  };
}

function normalizeNode(
  value: unknown,
  parentNodeIds: ReadonlyMap<number, readonly number[]>,
):
  | DecodedProfessionTalents['professions'][number]['trees'][number]['tabs'][number]['nodes'][number]
  | null {
  const node = object(value);
  const nodeId = integer(node['nodeID']);
  if (nodeId === null) return null;
  const nodeInfo = object(node['nodeInfo']);
  return {
    nodeId,
    maxRanks: integer(nodeInfo['maxRanks']),
    requiredRank: integer(node['unlockRank']),
    description: string(node['pathDescription']),
    parentNodeIds: parentNodeIds.get(nodeId) ?? [],
    entries: collection(node['entries'])
      .map((entryValue) => {
        const entry = object(entryValue);
        return {
          entryId: integer(entry['entryID']) ?? 0,
          rankLimit:
            integer(object(entry['entryInfo'])['maxRanks']) ??
            integer(object(entry['definitionInfo'])['maxRanks']),
          description: string(object(entry['definitionInfo'])['overrideDescription']),
        };
      })
      .filter((entry) => entry.entryId > 0),
  };
}

function parentNodeIdsByChild(nodes: readonly unknown[]): Map<number, readonly number[]> {
  const result = new Map<number, readonly number[]>();
  nodes.forEach((value) => {
    const node = object(value);
    const parentNodeId = integer(node['nodeID']);
    if (parentNodeId === null) return;
    collection(node['childPathIDs'])
      .map(integer)
      .filter(isPresent)
      .forEach((childNodeId) => {
        const parents = result.get(childNodeId) ?? [];
        if (!parents.includes(parentNodeId)) result.set(childNodeId, [...parents, parentNodeId]);
      });
  });
  return result;
}

function normalizedExpansionId(specialization: Record<string, unknown>): number | null {
  const wowExpansionId = integer(specialization['expansionID']);
  if (wowExpansionId !== null) return wowExpansionId + 1;
  const tierName = string(specialization['tierName'])?.toLowerCase();
  if (tierName?.includes('midnight')) return 12;
  if (tierName?.includes('khaz algar')) return 11;
  if (tierName?.includes('dragon isles')) return 10;
  return null;
}

function allocationFromNode(
  value: unknown,
): { nodeId: number; entryId: number; rank: number } | null {
  const node = object(value);
  const nodeInfo = object(node['nodeInfo']);
  const activeEntry = object(nodeInfo['activeEntry']);
  const nodeId = integer(node['nodeID']);
  const entryId = integer(activeEntry['entryID']) ?? integer(nodeInfo['activeEntryID']);
  const rank = integer(activeEntry['rank']) ?? integer(nodeInfo['currentRank']);
  return nodeId !== null && entryId !== null && rank !== null ? { nodeId, entryId, rank } : null;
}

function sanitizeDecodedValue(value: unknown, depth: number, state: { count: number }): unknown {
  if (depth > 64 || state.count++ > 5_000_000) {
    throw new LuaProcessingError(
      'INPUT_LIMIT_EXCEEDED',
      'Decoded talent structure limit exceeded.',
    );
  }
  if (value === null || typeof value === 'string' || typeof value === 'boolean') return value;
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (value instanceof Uint8Array) return new TextDecoder('utf-8', { fatal: true }).decode(value);
  if (Array.isArray(value))
    return value.map((item) => sanitizeDecodedValue(item, depth + 1, state));
  if (value instanceof Map) {
    const result = Object.create(null) as Record<string, unknown>;
    value.forEach((item, key) => assignSafe(result, decodedKey(key), item, depth, state));
    return result;
  }
  if (typeof value === 'object') {
    const result = Object.create(null) as Record<string, unknown>;
    Object.entries(value).forEach(([key, item]) => assignSafe(result, key, item, depth, state));
    return result;
  }
  throw new LuaProcessingError('UNSUPPORTED_LUA', 'Unsupported value in decoded talent export.');
}

function decodedKey(value: unknown): string {
  if (typeof value === 'string' || typeof value === 'number') return String(value);
  if (value instanceof Uint8Array) return new TextDecoder('utf-8', { fatal: true }).decode(value);
  throw new LuaProcessingError('UNSUPPORTED_LUA', 'Unsupported decoded property key.');
}

function assignSafe(
  target: Record<string, unknown>,
  key: string,
  value: unknown,
  depth: number,
  state: { count: number },
): void {
  if (DANGEROUS_KEYS.has(key)) {
    throw new LuaProcessingError('UNSUPPORTED_LUA', `Unsafe decoded key: ${key}.`);
  }
  target[key] = sanitizeDecodedValue(value, depth + 1, state);
}

function decodeBase64(value: string): Uint8Array {
  try {
    const binary = atob(value);
    return Uint8Array.from(binary, (character) => character.charCodeAt(0));
  } catch {
    throw new LuaProcessingError('MALFORMED_LUA', 'Talent payload is not valid Base64.');
  }
}

function object(value: unknown): Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : (Object.create(null) as Record<string, unknown>);
}

function collection(value: unknown): unknown[] {
  return Array.isArray(value) ? value : Object.values(object(value));
}

function string(value: unknown): string | null {
  return typeof value === 'string' ? value : null;
}

function integer(value: unknown): number | null {
  return typeof value === 'number' && Number.isInteger(value) ? value : null;
}

function isPresent<T>(value: T | null): value is T {
  return value !== null;
}
