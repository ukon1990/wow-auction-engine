import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  linkedSignal,
  output,
} from '@angular/core';

import {
  ProfessionSkillTree,
  ProfessionSkillTreeNode,
  ProfessionSkillTreeTab,
} from '@api/generated';

interface PositionedNode {
  readonly node: ProfessionSkillTreeNode;
  readonly x: number;
  readonly y: number;
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

const nodeWidth = 224;
const nodeHeight = 188;
const columnGap = 64;
const rowGap = 28;
const graphPadding = 24;

@Component({
  selector: 'app-profession-skill-tree-editor',
  templateUrl: './profession-skill-tree-editor.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfessionSkillTreeEditor {
  readonly tree = input.required<ProfessionSkillTree>();
  readonly allocations = input.required<ReadonlyMap<number, number>>();
  readonly rankChanged = output<{
    node: ProfessionSkillTreeNode;
    entryId: number;
    change: number;
  }>();

  protected readonly selectedTabId = linkedSignal(() => this.tree().tabs[0]?.id ?? null);
  protected readonly selectedTab = computed(() => {
    const tabs = this.tree().tabs;
    return tabs.find((tab) => tab.id === this.selectedTabId()) ?? tabs[0] ?? null;
  });
  protected readonly layout = computed(() => layoutGraph(this.selectedTab()?.nodes ?? []));

  protected selectTab(tab: ProfessionSkillTreeTab): void {
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

  protected canIncrease(node: ProfessionSkillTreeNode, entryId: number): boolean {
    const entry = node.entries.find((candidate) => candidate.id === entryId);
    if (!entry || this.rankFor(entryId) >= entry.rankLimit) return false;
    return (
      node.entries.reduce((total, candidate) => total + this.rankFor(candidate.id), 0) <
      node.maxRanks
    );
  }

  protected parentSummary(node: ProfessionSkillTreeNode, tab: ProfessionSkillTreeTab): string {
    if (!node.prerequisites.length) {
      return $localize`:@@professionProfiles.noPrerequisites:No prerequisites`;
    }
    return node.prerequisites
      .map((prerequisite) => {
        const parent = findNode(tab.nodes, prerequisite.parentNodeId);
        const parentName = parent?.name ?? String(prerequisite.parentNodeId);
        return $localize`:@@professionProfiles.namedPrerequisite:${parentName}:INTERPOLATION: at rank ${prerequisite.requiredParentRanks}:INTERPOLATION:`;
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
}

export function layoutGraph(nodes: readonly ProfessionSkillTreeNode[]): GraphLayout {
  if (!nodes.length) return { width: 0, height: 0, nodes: [], connectors: [] };
  const ids = new Set(nodes.flatMap((node) => [node.id, node.externalNodeId]));
  const levels = new Map<number, number>();
  const levelFor = (node: ProfessionSkillTreeNode, visiting = new Set<number>()): number => {
    const cached = levels.get(node.id);
    if (cached != null) return cached;
    if (visiting.has(node.id)) return 0;
    const nextVisiting = new Set(visiting).add(node.id);
    const parents = node.prerequisites
      .map((prerequisite) => findNode(nodes, prerequisite.parentNodeId))
      .filter((parent): parent is ProfessionSkillTreeNode => parent != null);
    const level = parents.length
      ? Math.max(...parents.map((parent) => levelFor(parent, nextVisiting))) + 1
      : 0;
    levels.set(node.id, level);
    return level;
  };
  nodes.forEach((node) => levelFor(node));
  const columns = new Map<number, ProfessionSkillTreeNode[]>();
  [...nodes]
    .sort((left, right) => left.displayOrder - right.displayOrder || left.id - right.id)
    .forEach((node) => {
      const level = levels.get(node.id) ?? 0;
      columns.set(level, [...(columns.get(level) ?? []), node]);
    });
  const maxRows = Math.max(...[...columns.values()].map((column) => column.length));
  const width =
    graphPadding * 2 +
    (Math.max(...columns.keys()) + 1) * nodeWidth +
    Math.max(...columns.keys()) * columnGap;
  const height = graphPadding * 2 + maxRows * nodeHeight + (maxRows - 1) * rowGap;
  const positioned = [...columns].flatMap(([level, column]) => {
    const columnHeight = column.length * nodeHeight + (column.length - 1) * rowGap;
    const offset = graphPadding + (height - graphPadding * 2 - columnHeight) / 2;
    return column.map((node, index) => ({
      node,
      x: graphPadding + level * (nodeWidth + columnGap),
      y: offset + index * (nodeHeight + rowGap),
    }));
  });
  const positions = new Map(
    positioned.flatMap((item) => [
      [item.node.id, item],
      [item.node.externalNodeId, item],
    ]),
  );
  const connectors = positioned.flatMap((child) =>
    child.node.prerequisites.flatMap((prerequisite) => {
      if (!ids.has(prerequisite.parentNodeId)) return [];
      const parent = positions.get(prerequisite.parentNodeId);
      if (!parent) return [];
      const startX = parent.x + nodeWidth;
      const startY = parent.y + nodeHeight / 2;
      const endX = child.x;
      const endY = child.y + nodeHeight / 2;
      const middleX = startX + (endX - startX) / 2;
      return [
        {
          key: `${parent.node.id}-${child.node.id}`,
          path: `M ${startX} ${startY} C ${middleX} ${startY}, ${middleX} ${endY}, ${endX} ${endY}`,
        },
      ];
    }),
  );
  return { width, height, nodes: positioned, connectors };
}

function findNode(
  nodes: readonly ProfessionSkillTreeNode[],
  nodeId: number,
): ProfessionSkillTreeNode | undefined {
  return nodes.find((node) => node.id === nodeId || node.externalNodeId === nodeId);
}
