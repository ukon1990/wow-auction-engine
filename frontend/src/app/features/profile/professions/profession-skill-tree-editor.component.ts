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

const nodeWidth = 192;
const nodeHeight = 164;
const columnGap = 40;
const rowGap = 52;
const graphPadding = 24;

@Component({
  selector: 'app-profession-skill-tree-editor',
  templateUrl: './profession-skill-tree-editor.component.html',
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
        return $localize`:@@professionProfiles.namedPrerequisite:${parent.name ?? ''}:INTERPOLATION: at rank ${requiredRanks}:INTERPOLATION:`;
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

  protected entryLabel(entry: SkillTreeGraphEntry, node: SkillTreeGraphNode): string {
    return entry.name?.trim() || node.name?.trim() || '';
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
  const visibleNodes = nodes.filter(isVisibleNode);
  if (!visibleNodes.length) return { width: 0, height: 0, nodes: [], connectors: [] };
  const parentsByNode = new Map(visibleNodes.map((node) => [node.id, visibleParents(node, nodes)]));
  const levels = new Map<number, number>();
  const levelFor = (node: SkillTreeGraphNode, visiting = new Set<number>()): number => {
    const cached = levels.get(node.id);
    if (cached != null) return cached;
    if (visiting.has(node.id)) return 0;
    const nextVisiting = new Set(visiting).add(node.id);
    const parents = parentsByNode.get(node.id) ?? [];
    const level = parents.length
      ? Math.max(...parents.map((parent) => levelFor(parent, nextVisiting))) + 1
      : 0;
    levels.set(node.id, level);
    return level;
  };
  visibleNodes.forEach((node) => levelFor(node));
  const rows = new Map<number, SkillTreeGraphNode[]>();
  [...visibleNodes]
    .sort((left, right) => left.displayOrder - right.displayOrder || left.id - right.id)
    .forEach((node) => {
      const level = levels.get(node.id) ?? 0;
      rows.set(level, [...(rows.get(level) ?? []), node]);
    });
  const maxColumns = Math.max(...[...rows.values()].map((row) => row.length));
  const maxLevel = Math.max(...rows.keys());
  const width = graphPadding * 2 + maxColumns * nodeWidth + (maxColumns - 1) * columnGap;
  const height = graphPadding * 2 + (maxLevel + 1) * nodeHeight + maxLevel * rowGap;
  const positioned = [...rows].flatMap(([level, row]) => {
    const rowWidth = row.length * nodeWidth + (row.length - 1) * columnGap;
    const offset = graphPadding + (width - graphPadding * 2 - rowWidth) / 2;
    return row.map((node, index) => ({
      node,
      x: offset + index * (nodeWidth + columnGap),
      y: graphPadding + level * (nodeHeight + rowGap),
    }));
  });
  const positions = new Map(
    positioned.flatMap((item) => [
      [item.node.id, item],
      [item.node.externalNodeId, item],
    ]),
  );
  const connectors = positioned.flatMap((child) =>
    (parentsByNode.get(child.node.id) ?? []).flatMap((parentNode) => {
      const parent = positions.get(parentNode.id);
      if (!parent) return [];
      const startX = parent.x + nodeWidth / 2;
      const startY = parent.y + nodeHeight;
      const endX = child.x + nodeWidth / 2;
      const endY = child.y;
      const middleY = startY + (endY - startY) / 2;
      return [
        {
          key: `${parent.node.id}-${child.node.id}`,
          path: `M ${startX} ${startY} C ${startX} ${middleY}, ${endX} ${middleY}, ${endX} ${endY}`,
        },
      ];
    }),
  );
  return { width, height, nodes: positioned, connectors };
}

function findNode(
  nodes: readonly SkillTreeGraphNode[],
  nodeId: number,
): SkillTreeGraphNode | undefined {
  return nodes.find((node) => node.id === nodeId || node.externalNodeId === nodeId);
}

export function isVisibleNode(node: SkillTreeGraphNode): boolean {
  return Boolean(node.name?.trim());
}

export function visibleParents(
  node: SkillTreeGraphNode,
  nodes: readonly SkillTreeGraphNode[],
): SkillTreeGraphNode[] {
  const result = new Map<number, SkillTreeGraphNode>();
  const visit = (candidate: SkillTreeGraphNode, visiting: Set<number>): void => {
    if (visiting.has(candidate.id)) return;
    if (isVisibleNode(candidate)) {
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
  return [...result.values()].sort(
    (left, right) => left.displayOrder - right.displayOrder || left.id - right.id,
  );
}
