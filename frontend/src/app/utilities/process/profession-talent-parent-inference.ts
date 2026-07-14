export interface TalentNodeParentSource {
  readonly nodeId: number;
  readonly name?: string | null;
  readonly parentNodeIds?: readonly number[];
}

export interface TalentPathEdge {
  readonly fromPathId: number;
  readonly toPathId: number;
}

export interface TalentParentResolutionOptions {
  readonly childPathIdsByNodeId?: ReadonlyMap<number, readonly number[]>;
  readonly edges?: readonly TalentPathEdge[];
}

export type TalentParentResolutionInput =
  ReadonlyMap<number, readonly number[]> | TalentParentResolutionOptions;

export function resolveParentNodeIdsByChild(
  nodes: ReadonlyArray<{
    readonly nodeId: number;
    readonly childPathIds?: readonly number[];
  }>,
): Map<number, number[]> {
  const result = new Map<number, number[]>();
  nodes.forEach((node) => {
    for (const childNodeId of node.childPathIds ?? []) {
      const parents = result.get(childNodeId) ?? [];
      if (!parents.includes(node.nodeId)) {
        result.set(childNodeId, [...parents, node.nodeId]);
      }
    }
  });
  return result;
}

export function resolveParentNodeIdsFromEdges(
  edges: readonly TalentPathEdge[],
): Map<number, number[]> {
  const result = new Map<number, number[]>();
  edges.forEach(({ fromPathId, toPathId }) => {
    const parents = result.get(toPathId) ?? [];
    if (!parents.includes(fromPathId)) {
      result.set(toPathId, [...parents, fromPathId]);
    }
  });
  return result;
}

export function inferParentNodeIdsFromExportOrder(
  nodes: readonly TalentNodeParentSource[],
): Map<number, number[]> {
  const named = nodes
    .map((node, index) => ({ node, index }))
    .filter(({ node }) => Boolean(node.name?.trim()));
  if (named.length < 2) return new Map();

  const result = new Map<number, number[]>();
  const root = named[named.length - 1].node;
  let cursor = named.length - 2;
  const hubs: TalentNodeParentSource[] = [];

  while (cursor >= 0) {
    const hub = named[cursor].node;
    hubs.unshift(hub);
    const leafStart = Math.max(0, cursor - 3);
    for (let index = leafStart; index < cursor; index++) {
      result.set(named[index].node.nodeId, [hub.nodeId]);
    }
    cursor -= 4;
  }

  for (const hub of hubs) {
    result.set(hub.nodeId, [root.nodeId]);
  }

  return result;
}

export function resolveTalentParentNodeIds(
  nodes: readonly TalentNodeParentSource[],
  resolution?: TalentParentResolutionInput,
): Map<number, number[]> {
  const options = normalizeParentResolution(resolution);
  const fromChildPaths = options.childPathIdsByNodeId
    ? resolveParentNodeIdsByChild(
        nodes.map((node) => ({
          nodeId: node.nodeId,
          childPathIds: options.childPathIdsByNodeId!.get(node.nodeId) ?? [],
        })),
      )
    : new Map<number, number[]>();
  const fromEdges = options.edges?.length
    ? resolveParentNodeIdsFromEdges(options.edges)
    : new Map<number, number[]>();
  const fromExplicit = new Map<number, number[]>();
  nodes.forEach((node) => {
    if (node.parentNodeIds?.length) {
      fromExplicit.set(node.nodeId, [...node.parentNodeIds]);
    }
  });

  const merged = mergeParentMaps(fromChildPaths, fromEdges, fromExplicit);
  if ([...merged.values()].some((parents) => parents.length > 0)) {
    return merged;
  }

  return inferParentNodeIdsFromExportOrder(nodes);
}

function normalizeParentResolution(
  resolution?: TalentParentResolutionInput,
): TalentParentResolutionOptions {
  if (!resolution) return {};
  if (resolution instanceof Map) return { childPathIdsByNodeId: resolution };
  const options = resolution as TalentParentResolutionOptions;
  return {
    ...(options.childPathIdsByNodeId ? { childPathIdsByNodeId: options.childPathIdsByNodeId } : {}),
    ...(options.edges ? { edges: options.edges } : {}),
  };
}

function mergeParentMaps(...maps: ReadonlyArray<Map<number, number[]>>): Map<number, number[]> {
  const result = new Map<number, number[]>();
  maps.forEach((map) => {
    map.forEach((parents, nodeId) => {
      const existing = result.get(nodeId) ?? [];
      result.set(nodeId, [...new Set([...existing, ...parents])]);
    });
  });
  return result;
}
