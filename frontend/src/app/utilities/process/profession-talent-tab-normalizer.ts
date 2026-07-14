import {
  resolveTalentParentNodeIds,
  type TalentPathEdge,
} from './profession-talent-parent-inference';
import {
  boundedDescription,
  boundedName,
  looksLikeTalentDescription,
} from './normalized-string-bounds';

export interface NormalizedTalentEntry {
  readonly entryId: number;
  readonly name?: string;
  readonly rankLimit?: number;
  readonly description?: string;
}

export interface NormalizedTalentNode {
  readonly nodeId: number;
  readonly nodeKind?: 'path' | 'milestone';
  readonly name?: string;
  readonly maxRanks?: number;
  readonly requiredRank?: number;
  readonly description?: string;
  readonly parentNodeIds: number[];
  readonly entries: readonly NormalizedTalentEntry[];
}

export interface NormalizedTalentAllocation {
  readonly nodeId: number;
  readonly entryId: number;
  readonly rank: number;
}

export interface NormalizedTalentTab {
  readonly tabId: number;
  readonly name?: string;
  readonly description?: string;
  readonly nodes: readonly NormalizedTalentNode[];
  readonly allocations: readonly NormalizedTalentAllocation[];
}

export function normalizeTalentTab(tab: Record<string, unknown>): NormalizedTalentTab | null {
  const tabId = asInt(tab['treeID']);
  if (tabId === null) return null;
  const tabInfo = asRecord(tab['tabInfo']);
  const name = boundedName(asText(tabInfo['name']) ?? undefined);
  const description = boundedDescription(asText(tabInfo['description']) ?? undefined);

  if (usesStructuredTabLayout(tab)) {
    return normalizeStructuredTalentTab(tab, tabId, name, description);
  }

  return normalizeLegacyTalentTab(tab, tabId, name, description);
}

function usesStructuredTabLayout(tab: Record<string, unknown>): boolean {
  return (
    asArray(tab['paths']).length > 0 ||
    asArray(tab['edges']).length > 0 ||
    asArray(tab['milestones']).length > 0
  );
}

function normalizeStructuredTalentTab(
  tab: Record<string, unknown>,
  tabId: number,
  name?: string,
  description?: string,
): NormalizedTalentTab {
  const paths = asArray(tab['paths']).map(asRecord);
  const milestones = asArray(tab['milestones']).map(asRecord);
  const edges = parsePathEdges(asArray(tab['edges']));

  const pathResults = paths.map(normalizePathNode).filter((result) => result.node.nodeId > 0);
  const milestoneResults = milestones
    .map(normalizeMilestoneNode)
    .filter((result) => result.node.nodeId > 0);
  const nodes = [...pathResults, ...milestoneResults].map((result) => result.node);
  const allocations = [...pathResults, ...milestoneResults]
    .map((result) => result.allocation)
    .filter(
      (allocation): allocation is NormalizedTalentAllocation =>
        allocation !== null && allocation.rank > 0,
    );

  const childPathIdsByNodeId = childPathIdsByNode(paths);
  const parentNodeIds = resolveTalentParentNodeIds(nodes, { childPathIdsByNodeId, edges });
  const nodesWithParents = nodes.map((node) => ({
    ...node,
    parentNodeIds: parentNodeIds.get(node.nodeId) ?? node.parentNodeIds,
  }));

  return { tabId, name, description, nodes: nodesWithParents, allocations };
}

function normalizeLegacyTalentTab(
  tab: Record<string, unknown>,
  tabId: number,
  name?: string,
  description?: string,
): NormalizedTalentTab {
  const sourceNodes = asArray(tab['nodes']).map(asRecord);
  const childPathIdsByNodeId = childPathIdsByNode(sourceNodes);
  const results = sourceNodes.map(normalizeLegacyNode).filter((result) => result.node.nodeId > 0);
  const nodes = results.map((result) => result.node);
  const allocations = results
    .map((result) => result.allocation)
    .filter(
      (allocation): allocation is NormalizedTalentAllocation =>
        allocation !== null && allocation.rank > 0,
    );
  const parentNodeIds = resolveTalentParentNodeIds(nodes, childPathIdsByNodeId);
  const nodesWithParents = nodes.map((node) => ({
    ...node,
    parentNodeIds: parentNodeIds.get(node.nodeId) ?? [],
  }));

  return { tabId, name, description, nodes: nodesWithParents, allocations };
}

function normalizePathNode(raw: Record<string, unknown>): {
  node: NormalizedTalentNode;
  allocation: NormalizedTalentAllocation | null;
} {
  const nodeInfo = asRecord(raw['nodeInfo']);
  const nodeId = asInt(raw['nodeID']);
  if (nodeId === null) {
    return {
      node: { nodeId: -1, parentNodeIds: [], entries: [] },
      allocation: null,
    };
  }

  const name = boundedName(pathNodeName(raw));
  const description = boundedDescription(pathNodeDescription(raw));
  const maxRanks = asInt(nodeInfo['maxRanks']) ?? asInt(nodeInfo['totalMaxRanks']) ?? undefined;
  const requiredRank = asInt(raw['unlockRank']) ?? undefined;
  const sourceEntries = asArray(raw['entries']).map(asRecord);
  const activeEntry = asRecord(nodeInfo['activeEntry']);
  const defaultEntryId =
    asInt(activeEntry['entryID']) ?? asInt(nodeInfo['activeEntryID']) ?? nodeId;
  const entries =
    sourceEntries.length > 0
      ? sourceEntries.flatMap((entry) => normalizeEntry(entry, maxRanks))
      : [
          {
            entryId: defaultEntryId,
            ...(name ? { name } : {}),
            ...(maxRanks !== undefined ? { rankLimit: maxRanks } : {}),
            ...(description ? { description } : {}),
          },
        ];
  const rank =
    asInt(activeEntry['rank']) ?? asInt(nodeInfo['currentRank']) ?? asInt(nodeInfo['activeRank']);
  const allocationEntryId = entries[0]?.entryId ?? defaultEntryId;
  const allocation = rank !== null ? { nodeId, entryId: allocationEntryId, rank } : null;

  return {
    node: {
      nodeId,
      nodeKind: 'path',
      ...(name ? { name } : {}),
      ...(maxRanks !== undefined ? { maxRanks } : {}),
      ...(requiredRank !== undefined ? { requiredRank } : {}),
      ...(description ? { description } : {}),
      parentNodeIds: [],
      entries,
    },
    allocation,
  };
}

function normalizeMilestoneNode(raw: Record<string, unknown>): {
  node: NormalizedTalentNode;
  allocation: NormalizedTalentAllocation | null;
} {
  const nodeInfo = asRecord(raw['nodeInfo']);
  const nodeId = asInt(raw['nodeID']);
  if (nodeId === null) {
    return {
      node: { nodeId: -1, parentNodeIds: [], entries: [] },
      allocation: null,
    };
  }

  const name = boundedName(milestoneNodeName(raw));
  const description = boundedDescription(milestoneNodeDescription(raw));
  const parentPathId = asInt(raw['parentPathID']);
  const requiredRank = asInt(raw['milestoneRank']) ?? asInt(raw['unlockRank']) ?? undefined;
  const maxRanks = asInt(nodeInfo['maxRanks']) ?? 1;
  const activeEntry = asRecord(nodeInfo['activeEntry']);
  const entryId = asInt(activeEntry['entryID']) ?? asInt(nodeInfo['activeEntryID']) ?? nodeId;
  const rank =
    asInt(activeEntry['rank']) ?? asInt(nodeInfo['currentRank']) ?? asInt(nodeInfo['activeRank']);
  const entries: NormalizedTalentEntry[] = [
    {
      entryId,
      ...(name ? { name } : {}),
      rankLimit: maxRanks,
      ...(description ? { description } : {}),
    },
  ];

  return {
    node: {
      nodeId,
      nodeKind: 'milestone',
      ...(name ? { name } : {}),
      maxRanks,
      ...(requiredRank !== undefined ? { requiredRank } : {}),
      ...(description ? { description } : {}),
      parentNodeIds: parentPathId !== null ? [parentPathId] : [],
      entries,
    },
    allocation: rank !== null && rank > 0 ? { nodeId, entryId, rank } : null,
  };
}

function normalizeLegacyNode(raw: Record<string, unknown>): {
  node: NormalizedTalentNode;
  allocation: NormalizedTalentAllocation | null;
} {
  const nodeInfo = asRecord(raw['nodeInfo']);
  const nodeId = asInt(raw['nodeID']);
  if (nodeId === null) {
    return {
      node: { nodeId: -1, parentNodeIds: [], entries: [] },
      allocation: null,
    };
  }

  const sourceEntries = asArray(raw['entries']).map(asRecord);
  const activeEntry = asRecord(nodeInfo['activeEntry']);
  const name = boundedName(
    asText(raw['overrideName']) ??
      asText(raw['name']) ??
      asText(raw['nodeName']) ??
      asText(nodeInfo['overrideName']) ??
      asText(nodeInfo['name']) ??
      sourceEntries.map(talentEntryName).find((value) => value !== undefined) ??
      undefined,
  );
  const description = boundedDescription(
    asText(raw['pathDescription']) ?? asText(raw['nodeDescription']) ?? undefined,
  );
  const maxRanks = asInt(nodeInfo['maxRanks']) ?? asInt(nodeInfo['totalMaxRanks']) ?? undefined;
  const requiredRank = asInt(raw['unlockRank']) ?? undefined;
  const entries = sourceEntries.flatMap((entry) => normalizeEntry(entry, maxRanks));
  const entryId = asInt(activeEntry['entryID']) ?? asInt(nodeInfo['activeEntryID']);
  const rank =
    asInt(activeEntry['rank']) ?? asInt(nodeInfo['currentRank']) ?? asInt(nodeInfo['activeRank']);
  const allocation = entryId !== null && rank !== null ? { nodeId, entryId, rank } : null;

  return {
    node: {
      nodeId,
      nodeKind: 'path',
      ...(name ? { name } : {}),
      ...(maxRanks !== undefined ? { maxRanks } : {}),
      ...(requiredRank !== undefined ? { requiredRank } : {}),
      ...(description ? { description } : {}),
      parentNodeIds: [],
      entries,
    },
    allocation,
  };
}

function normalizeEntry(
  entry: Record<string, unknown>,
  nodeMaxRanks?: number,
): NormalizedTalentEntry[] {
  const entryId = asInt(entry['entryID']);
  if (entryId === null) return [];
  const entryInfo = asRecord(entry['entryInfo']);
  const definitionInfo = asRecord(entry['definitionInfo']);
  const name = talentEntryName(entry);
  const entryRankLimit =
    asInt(entryInfo['maxRanks']) ?? asInt(definitionInfo['maxRanks']) ?? undefined;
  const rankLimit = resolveEntryRankLimit(nodeMaxRanks, entryRankLimit);
  const description = talentEntryDescription(entry);
  return [
    {
      entryId,
      ...(name ? { name } : {}),
      ...(rankLimit !== undefined ? { rankLimit } : {}),
      ...(description ? { description } : {}),
    },
  ];
}

function resolveEntryRankLimit(nodeMaxRanks?: number, entryRankLimit?: number): number | undefined {
  if (nodeMaxRanks !== undefined && entryRankLimit !== undefined) {
    return Math.max(nodeMaxRanks, entryRankLimit);
  }
  return nodeMaxRanks ?? entryRankLimit;
}

function pathNodeName(raw: Record<string, unknown>): string | undefined {
  const nodeInfo = asRecord(raw['nodeInfo']);
  return (
    asText(raw['nodeName']) ??
    asText(raw['overrideName']) ??
    asText(raw['name']) ??
    asText(nodeInfo['overrideName']) ??
    asText(nodeInfo['name']) ??
    undefined
  );
}

function pathNodeDescription(raw: Record<string, unknown>): string | undefined {
  return asText(raw['nodeDescription']) ?? asText(raw['pathDescription']) ?? undefined;
}

function milestoneNodeName(raw: Record<string, unknown>): string | undefined {
  return asText(raw['nodeName']) ?? asText(raw['overrideName']) ?? asText(raw['name']) ?? undefined;
}

function milestoneNodeDescription(raw: Record<string, unknown>): string | undefined {
  return asText(raw['nodeDescription']) ?? undefined;
}

function talentEntryName(entry: Record<string, unknown>): string | undefined {
  const definitionInfo = asRecord(entry['definitionInfo']);
  const overrideName = asText(definitionInfo['overrideName']) ?? asText(entry['overrideName']);
  if (overrideName) return boundedName(overrideName);
  const rawName = asText(definitionInfo['name']) ?? asText(entry['name']);
  if (rawName && !looksLikeTalentDescription(rawName)) return boundedName(rawName);
  return undefined;
}

function talentEntryDescription(entry: Record<string, unknown>): string | undefined {
  const definitionInfo = asRecord(entry['definitionInfo']);
  const overrideDescription = asText(definitionInfo['overrideDescription']);
  if (overrideDescription) return boundedDescription(overrideDescription);
  const rawName = asText(definitionInfo['name']) ?? asText(entry['name']);
  if (rawName && looksLikeTalentDescription(rawName)) return boundedDescription(rawName);
  return undefined;
}

function childPathIdsByNode(nodes: readonly Record<string, unknown>[]): Map<number, number[]> {
  const result = new Map<number, number[]>();
  nodes.forEach((node) => {
    const nodeId = asInt(node['nodeID']);
    if (nodeId === null) return;
    const childPathIds = childPathIdsFromNode(node);
    if (childPathIds.length) result.set(nodeId, childPathIds);
  });
  return result;
}

function childPathIdsFromNode(node: Record<string, unknown>): number[] {
  const nodeInfo = asRecord(node['nodeInfo']);
  const ids = new Set<number>();
  for (const value of [
    ...asArray(node['childPathIDs']),
    ...asArray(node['childIDs']),
    ...asArray(nodeInfo['childPathIDs']),
    ...asArray(nodeInfo['childIDs']),
  ]) {
    const childNodeId = asInt(value);
    if (childNodeId !== null) ids.add(childNodeId);
  }
  return [...ids];
}

function parsePathEdges(values: unknown[]): TalentPathEdge[] {
  return values.flatMap((value) => {
    const edge = asRecord(value);
    const fromPathId = asInt(edge['fromPathID']);
    const toPathId = asInt(edge['toPathID']);
    if (fromPathId === null || toPathId === null) return [];
    return [{ fromPathId, toPathId }];
  });
}

function asRecord(value: unknown): Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}

function asArray(value: unknown): unknown[] {
  if (Array.isArray(value)) return value;
  if (typeof value === 'object' && value !== null) return Object.values(value);
  return [];
}

function asText(value: unknown): string | null {
  return typeof value === 'string' ? value : null;
}

function asInt(value: unknown): number | null {
  return typeof value === 'number' && Number.isInteger(value) ? value : null;
}
