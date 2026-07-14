export interface SkillTreeGraphEntryShape {
  readonly id: number;
  readonly name?: string | null;
  readonly description?: string | null;
  readonly rankLimit: number;
  readonly displayOrder: number;
}

export interface SkillTreeGraphNodeShape {
  readonly id: number;
  readonly externalNodeId: number;
  readonly nodeKind?: 'path' | 'milestone';
  readonly name?: string | null;
  readonly description?: string | null;
  readonly maxRanks: number;
  readonly requiredRank: number;
  readonly displayOrder: number;
  readonly prerequisites: readonly { parentNodeId: number; requiredParentRanks: number }[];
  readonly entries: readonly SkillTreeGraphEntryShape[];
}

export function isMilestoneNode(
  node: SkillTreeGraphNodeShape,
  allNodes: readonly SkillTreeGraphNodeShape[] = [],
): boolean {
  if (node.nodeKind === 'milestone') return true;
  if (node.nodeKind === 'path') return false;

  if (node.maxRanks !== 1 || node.requiredRank <= 0) return false;
  if (hasGraphChildren(node, allNodes)) return false;

  const parentIds = node.prerequisites.map((prerequisite) => prerequisite.parentNodeId);
  if (parentIds.length !== 1) return false;

  const parent = findGraphNode(allNodes, parentIds[0]);
  if (!parent || parent.maxRanks <= 1) return false;

  const name = node.name?.trim() ?? '';
  const description = node.description?.trim() ?? '';
  if (!name && description.length > 0) return true;
  if (/^gain\s*\+/i.test(name) || name.length > 48) return true;
  return false;
}

export function milestoneNodesForParent(
  parent: SkillTreeGraphNodeShape,
  nodes: readonly SkillTreeGraphNodeShape[],
): readonly SkillTreeGraphNodeShape[] {
  return nodes
    .filter((node) => isMilestoneNode(node, nodes))
    .filter((node) =>
      node.prerequisites.some(
        (prerequisite) =>
          prerequisite.parentNodeId === parent.id ||
          prerequisite.parentNodeId === parent.externalNodeId,
      ),
    )
    .sort(compareGraphNodes);
}

export function milestoneTooltipText(
  parent: SkillTreeGraphNodeShape,
  nodes: readonly SkillTreeGraphNodeShape[],
): string | null {
  const lines = milestoneNodesForParent(parent, nodes).map((milestone) =>
    milestoneSummaryLine(milestone),
  );
  return lines.length ? lines.join('\n') : null;
}

function milestoneSummaryLine(milestone: SkillTreeGraphNodeShape): string {
  const label = milestoneLabel(milestone);
  if (milestone.requiredRank > 0) {
    return $localize`:@@professionProfiles.milestoneAtRank:Rank ${milestone.requiredRank}:INTERPOLATION:: ${label}:INTERPOLATION_1:`;
  }
  return label;
}

function milestoneLabel(milestone: SkillTreeGraphNodeShape): string {
  return milestone.description?.trim() || milestone.name?.trim() || '';
}

function hasGraphChildren(
  node: SkillTreeGraphNodeShape,
  allNodes: readonly SkillTreeGraphNodeShape[],
): boolean {
  return allNodes.some((candidate) =>
    candidate.prerequisites.some(
      (prerequisite) =>
        prerequisite.parentNodeId === node.id || prerequisite.parentNodeId === node.externalNodeId,
    ),
  );
}

function findGraphNode(
  nodes: readonly SkillTreeGraphNodeShape[],
  nodeId: number,
): SkillTreeGraphNodeShape | undefined {
  return nodes.find((node) => node.id === nodeId || node.externalNodeId === nodeId);
}

function compareGraphNodes(left: SkillTreeGraphNodeShape, right: SkillTreeGraphNodeShape): number {
  return (
    left.requiredRank - right.requiredRank ||
    left.displayOrder - right.displayOrder ||
    left.id - right.id
  );
}
