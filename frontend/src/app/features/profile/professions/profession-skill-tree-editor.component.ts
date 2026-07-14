import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  linkedSignal,
  output,
} from '@angular/core';

export interface SkillTreeGraphEntry {
  readonly id: number;
  readonly name?: string | null;
  readonly description?: string | null;
  readonly rankLimit: number;
  readonly displayOrder: number;
}

export interface SkillTreeGraphNode {
  readonly id: number;
  readonly externalNodeId: number;
  readonly name?: string | null;
  readonly description?: string | null;
  readonly maxRanks: number;
  readonly displayOrder: number;
  readonly prerequisites: readonly { parentNodeId: number; requiredParentRanks: number }[];
  readonly entries: readonly SkillTreeGraphEntry[];
}

export interface SkillTreeGraphTab {
  readonly id: number;
  readonly name?: string | null;
  readonly description?: string | null;
  readonly nodes: readonly SkillTreeGraphNode[];
}

export interface SkillTreeGraphTree {
  readonly id: number;
  readonly name?: string | null;
  readonly tabs: readonly SkillTreeGraphTab[];
}

interface PositionedNode {
  readonly node: SkillTreeGraphNode;
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
}

interface Connector {
  readonly key: string;
  readonly path: string;
}

interface GraphLayout {
  readonly width: number;
  readonly height: number;
  readonly nodes: readonly PositionedNode[];
  readonly connectors: readonly Connector[];
}

interface SubtreeLayout {
  readonly width: number;
  readonly positions: readonly PositionedNode[];
  readonly connectors: readonly Connector[];
}

export const graphNodeWidth = 224;
const nodeHeaderHeight = 72;
const entryRowHeight = 44;
const nodeFooterPadding = 8;
const columnGap = 40;
const rowGap = 52;
const graphPadding = 24;

@Component({
  selector: 'app-profession-skill-tree-editor',
  templateUrl: './profession-skill-tree-editor.component.html',
  host: { class: 'block min-w-0 max-w-full' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfessionSkillTreeEditor {
  readonly tree = input.required<SkillTreeGraphTree>();
  readonly allocations = input.required<ReadonlyMap<number, number>>();
  readonly editable = input(true);
  readonly rankChanged = output<{
    node: SkillTreeGraphNode;
    entryId: number;
    change: number;
  }>();

  protected readonly selectedTabId = linkedSignal(() => this.tree().tabs[0]?.id ?? null);
  protected readonly selectedTab = computed(() => {
    const tabs = this.tree().tabs;
    return tabs.find((tab) => tab.id === this.selectedTabId()) ?? tabs[0] ?? null;
  });
  protected readonly layout = computed(() => layoutGraph(this.selectedTab()?.nodes ?? []));

  protected readonly nodeDisplayName = nodeDisplayName;
  protected readonly editableEntries = editableEntries;

  protected selectTab(tab: SkillTreeGraphTab): void {
    this.selectedTabId.set(tab.id);
  }

  protected tabKeydown(event: KeyboardEvent, index: number): void {
    const tabs = this.tree().tabs;
    if (!tabs.length) return;
    let nextIndex: number | null = null;
    if (event.key === 'ArrowRight') nextIndex = (index + 1) % tabs.length;
    if (event.key === 'ArrowLeft') nextIndex = (index - 1 + tabs.length) % tabs.length;
    if (event.key === 'Home') nextIndex = 0;
    if (event.key === 'End') nextIndex = tabs.length - 1;
    if (nextIndex == null) return;
    event.preventDefault();
    this.selectTab(tabs[nextIndex]);
    document.getElementById(this.tabButtonId(tabs[nextIndex].id))?.focus();
  }

  protected rankFor(entryId: number): number {
    return this.allocations().get(entryId) ?? 0;
  }

  protected canIncrease(node: SkillTreeGraphNode, entryId: number): boolean {
    const entry = node.entries.find((candidate) => candidate.id === entryId);
    if (!entry || this.rankFor(entryId) >= entry.rankLimit) return false;
    return (
      node.entries.reduce((total, candidate) => total + this.rankFor(candidate.id), 0) <
      node.maxRanks
    );
  }

  protected parentSummary(node: SkillTreeGraphNode, tab: SkillTreeGraphTab): string {
    const parents = visibleParents(node, tab.nodes);
    if (!parents.length) {
      return $localize`:@@professionProfiles.noPrerequisites:No prerequisites`;
    }
    return parents
      .map((parent) => {
        const requiredRanks =
          node.prerequisites.find(
            (prerequisite) =>
              prerequisite.parentNodeId === parent.id ||
              prerequisite.parentNodeId === parent.externalNodeId,
          )?.requiredParentRanks ?? 1;
        return $localize`:@@professionProfiles.namedPrerequisite:${nodeDisplayName(parent)}:INTERPOLATION: at rank ${requiredRanks}:INTERPOLATION:`;
      })
      .join(', ');
  }

  protected tabButtonId(tabId: number): string {
    return `profession-tree-tab-${this.tree().id}-${tabId}`;
  }

  protected tabPanelId(tabId: number): string {
    return `profession-tree-panel-${this.tree().id}-${tabId}`;
  }

  protected markerId(tabId: number): string {
    return `profession-tree-arrow-${this.tree().id}-${tabId}`;
  }

  protected decreaseLabel(entryName: string): string {
    return $localize`:@@professionProfiles.decreaseRank:Decrease ${entryName}:INTERPOLATION: rank`;
  }

  protected increaseLabel(entryName: string): string {
    return $localize`:@@professionProfiles.increaseRank:Increase ${entryName}:INTERPOLATION: rank`;
  }

  protected entryNameVisible(entry: SkillTreeGraphEntry, node: SkillTreeGraphNode): boolean {
    return entryNameVisible(entry, node);
  }

  protected entryLabel(entry: SkillTreeGraphEntry, node: SkillTreeGraphNode, index = 0): string {
    const entryName = entry.name?.trim();
    const nodeName = nodeDisplayName(node);
    if (entryName && normalizeLabel(entryName) !== normalizeLabel(nodeName)) return entryName;
    if (editableEntries(node).length > 1) return `${nodeName} (${index + 1})`;
    return nodeName;
  }

  protected treeLabel(): string {
    return (
      this.tree().name?.trim() ||
      $localize`:@@professionProfiles.specializationTree:Specialization tree`
    );
  }

  protected tabLabel(tab: SkillTreeGraphTab): string {
    return tab.name?.trim() || `#${tab.id}`;
  }
}

export function layoutGraph(nodes: readonly SkillTreeGraphNode[]): GraphLayout {
  const visibleNodes = nodes.filter((node) => isVisibleNode(node, nodes));
  if (!visibleNodes.length) return { width: 0, height: 0, nodes: [], connectors: [] };

  const roots = visibleNodes
    .filter((node) => visibleParents(node, nodes).length === 0)
    .sort(compareNodes);

  let offsetX = graphPadding;
  const positions: PositionedNode[] = [];
  const connectors: Connector[] = [];

  for (const [index, root] of roots.entries()) {
    const layout = layoutSubtree(root, offsetX, graphPadding, nodes);
    positions.push(...layout.positions);
    connectors.push(...layout.connectors);
    offsetX += layout.width + (index < roots.length - 1 ? columnGap : 0);
  }

  const width = Math.max(offsetX + graphPadding - columnGap, graphNodeWidth + graphPadding * 2);
  const height =
    positions.length > 0
      ? Math.max(...positions.map((position) => position.y + position.height)) + graphPadding
      : 0;

  return { width, height, nodes: positions, connectors };
}

function layoutSubtree(
  node: SkillTreeGraphNode,
  offsetX: number,
  y: number,
  nodes: readonly SkillTreeGraphNode[],
): SubtreeLayout {
  const children = visibleChildNodes(node, nodes);
  const height = heightForNode(node);

  if (!children.length) {
    return {
      width: graphNodeWidth,
      positions: [{ node, x: offsetX, y, width: graphNodeWidth, height }],
      connectors: [],
    };
  }

  let childX = offsetX;
  const childLayouts: SubtreeLayout[] = [];
  for (const child of children) {
    const layout = layoutSubtree(child, childX, y + height + rowGap, nodes);
    childLayouts.push(layout);
    childX += layout.width + columnGap;
  }

  const childrenWidth = childX - offsetX - columnGap;
  const subtreeWidth = Math.max(graphNodeWidth, childrenWidth);
  const parentPosition: PositionedNode = {
    node,
    x: offsetX + (subtreeWidth - graphNodeWidth) / 2,
    y,
    width: graphNodeWidth,
    height,
  };

  const childConnectors = children.flatMap((child, index) => {
    const childPosition = childLayouts[index].positions.find(
      (position) => position.node.id === child.id,
    );
    if (!childPosition) return [];
    return [connectorBetween(parentPosition, childPosition)];
  });

  return {
    width: subtreeWidth,
    positions: [parentPosition, ...childLayouts.flatMap((layout) => layout.positions)],
    connectors: [...childLayouts.flatMap((layout) => layout.connectors), ...childConnectors],
  };
}

function visibleChildNodes(
  parent: SkillTreeGraphNode,
  nodes: readonly SkillTreeGraphNode[],
): readonly SkillTreeGraphNode[] {
  return nodes
    .filter((node) => isVisibleNode(node, nodes))
    .filter((node) => visibleParents(node, nodes).some((candidate) => candidate.id === parent.id))
    .sort(compareNodes);
}

function connectorBetween(parent: PositionedNode, child: PositionedNode): Connector {
  const startX = parent.x + parent.width / 2;
  const startY = parent.y + parent.height;
  const endX = child.x + child.width / 2;
  const endY = child.y;
  const middleY = startY + (endY - startY) / 2;
  return {
    key: `${parent.node.id}-${child.node.id}`,
    path: `M ${startX} ${startY} C ${startX} ${middleY}, ${endX} ${middleY}, ${endX} ${endY}`,
  };
}

function compareNodes(left: SkillTreeGraphNode, right: SkillTreeGraphNode): number {
  return left.displayOrder - right.displayOrder || left.id - right.id;
}

function findNode(
  nodes: readonly SkillTreeGraphNode[],
  nodeId: number,
): SkillTreeGraphNode | undefined {
  return nodes.find((node) => node.id === nodeId || node.externalNodeId === nodeId);
}

export function isVisibleNode(
  node: SkillTreeGraphNode,
  allNodes: readonly SkillTreeGraphNode[] = [],
): boolean {
  if (node.name?.trim()) return true;
  return node.maxRanks > 1 && hasStructuralChildren(node, allNodes);
}

export function nodeDisplayName(node: SkillTreeGraphNode): string {
  const explicit = node.name?.trim();
  if (explicit) return explicit;
  const entryName = node.entries.find((entry) => entry.name?.trim())?.name?.trim();
  if (entryName) return entryName;
  return $localize`:@@professionProfiles.unnamedTalentHub:Specialization`;
}

export function editableEntries(node: SkillTreeGraphNode): readonly SkillTreeGraphEntry[] {
  const sorted = [...node.entries].sort(
    (left, right) => left.displayOrder - right.displayOrder || left.id - right.id,
  );
  if (sorted.length <= 1) return sorted;

  const nodeLabel = normalizeLabel(nodeDisplayName(node));
  const labelFor = (entry: SkillTreeGraphEntry): string =>
    normalizeLabel(entry.name?.trim()) || nodeLabel;

  if (sorted.length === 2 && labelFor(sorted[0]) === labelFor(sorted[1])) {
    return [sorted[1]];
  }

  let candidates = sorted.filter((entry) => entry.rankLimit > 1);
  if (!candidates.length) candidates = sorted;

  const deduped = new Map<string, SkillTreeGraphEntry>();
  for (const entry of candidates) {
    const label = labelFor(entry);
    const existing = deduped.get(label);
    if (
      !existing ||
      entry.rankLimit > existing.rankLimit ||
      (entry.rankLimit === existing.rankLimit && entry.displayOrder > existing.displayOrder)
    ) {
      deduped.set(label, entry);
    }
  }

  return [...deduped.values()].sort(
    (left, right) => left.displayOrder - right.displayOrder || left.id - right.id,
  );
}

export function entryNameVisible(entry: SkillTreeGraphEntry, node: SkillTreeGraphNode): boolean {
  const entryName = entry.name?.trim();
  if (!entryName) return false;
  return normalizeLabel(entryName) !== normalizeLabel(nodeDisplayName(node));
}

function hasStructuralChildren(
  node: SkillTreeGraphNode,
  allNodes: readonly SkillTreeGraphNode[],
): boolean {
  return allNodes.some((candidate) =>
    candidate.prerequisites.some(
      (prerequisite) =>
        prerequisite.parentNodeId === node.id || prerequisite.parentNodeId === node.externalNodeId,
    ),
  );
}

function normalizeLabel(value: string | null | undefined): string {
  return (value ?? '')
    .trim()
    .normalize('NFKD')
    .replace(/[\u2018\u2019\u0060\u00B4']/g, '')
    .replace(/\s+/g, ' ')
    .toLocaleLowerCase();
}

function heightForNode(node: SkillTreeGraphNode): number {
  const entryCount = Math.max(editableEntries(node).length, 1);
  return nodeHeaderHeight + entryCount * entryRowHeight + nodeFooterPadding;
}

export function visibleParents(
  node: SkillTreeGraphNode,
  nodes: readonly SkillTreeGraphNode[],
): SkillTreeGraphNode[] {
  const result = new Map<number, SkillTreeGraphNode>();
  const visit = (candidate: SkillTreeGraphNode, visiting: Set<number>): void => {
    if (visiting.has(candidate.id)) return;
    if (isVisibleNode(candidate, nodes)) {
      result.set(candidate.id, candidate);
      return;
    }
    const nextVisiting = new Set(visiting).add(candidate.id);
    candidate.prerequisites.forEach((prerequisite) => {
      const parent = findNode(nodes, prerequisite.parentNodeId);
      if (parent) visit(parent, nextVisiting);
    });
  };
  node.prerequisites.forEach((prerequisite) => {
    const parent = findNode(nodes, prerequisite.parentNodeId);
    if (parent) visit(parent, new Set([node.id]));
  });
  return [...result.values()].sort(compareNodes);
}
