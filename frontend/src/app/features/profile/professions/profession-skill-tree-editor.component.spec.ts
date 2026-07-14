import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ProfessionSkillTree, ProfessionSkillTreeNode } from '@api/generated';
import {
  ProfessionSkillTreeEditor,
  editableEntries,
  entryNameVisible,
  layoutGraph,
  nodeAllocatedRank,
  nodeRankCap,
  displayRankLimit,
} from './profession-skill-tree-editor.component';
import { milestoneTooltipText } from './profession-skill-tree-nodes';

const rootNode = node(10, 'Foundations', 0);
const childNode = node(20, 'Armorsmithing', 1, [{ parentNodeId: 10, requiredParentRanks: 5 }]);
const secondTabNode = node(30, 'Weaponsmithing', 0);
const tree: ProfessionSkillTree = {
  id: 7,
  expansionId: 12,
  professionId: 164,
  externalTreeId: 700,
  name: 'Midnight Blacksmithing',
  tabs: [
    { id: 1, externalTabId: 101, name: 'Armor', displayOrder: 0, nodes: [rootNode, childNode] },
    { id: 2, externalTabId: 102, name: 'Weapons', displayOrder: 1, nodes: [secondTabNode] },
  ],
};

describe('ProfessionSkillTreeEditor', () => {
  let fixture: ComponentFixture<ProfessionSkillTreeEditor>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProfessionSkillTreeEditor],
    }).compileComponents();
    fixture = TestBed.createComponent(ProfessionSkillTreeEditor);
    fixture.componentRef.setInput('tree', tree);
    fixture.componentRef.setInput('allocations', new Map([[100, 2]]));
    fixture.detectChanges();
  });

  it('shows one accessible tab panel at a time', () => {
    const root = fixture.nativeElement as HTMLElement;
    const tabs = root.querySelectorAll<HTMLButtonElement>('[role="tab"]');
    expect(tabs).toHaveLength(2);
    expect(tabs[0].getAttribute('aria-selected')).toBe('true');
    expect(root.querySelectorAll('[role="tabpanel"]')).toHaveLength(1);
    expect(root.textContent).toContain('Foundations');
    expect(root.textContent).not.toContain('Weaponsmithing');

    tabs[1].click();
    fixture.detectChanges();
    expect(root.textContent).toContain('Weaponsmithing');
    expect(root.textContent).not.toContain('Foundations');
  });

  it('focuses and scrolls the primary root node when switching tabs', async () => {
    const scrollTo = vi.fn();
    const root = fixture.nativeElement as HTMLElement;
    const scrollContainer = root.querySelector<HTMLElement>('.overflow-auto');
    expect(scrollContainer).not.toBeNull();
    scrollContainer!.scrollTo = scrollTo;
    Object.defineProperty(scrollContainer!, 'clientWidth', { value: 400, configurable: true });
    Object.defineProperty(scrollContainer!, 'clientHeight', { value: 300, configurable: true });
    Object.defineProperty(scrollContainer!, 'scrollWidth', { value: 2000, configurable: true });
    Object.defineProperty(scrollContainer!, 'scrollHeight', { value: 1500, configurable: true });

    const tabs = root.querySelectorAll<HTMLButtonElement>('[role="tab"]');
    tabs[1].click();
    fixture.detectChanges();
    await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
    await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));

    const weaponsRoot = layoutGraph([secondTabNode]).nodes[0];
    const focused = document.activeElement as HTMLElement | null;
    expect(focused?.id).toBe(`profession-tree-node-${tree.id}-${secondTabNode.id}`);

    const centerLeft = weaponsRoot.x + weaponsRoot.width / 2 - 200;
    const centerTop = weaponsRoot.y + weaponsRoot.height / 2 - 150;
    expect(scrollTo).toHaveBeenCalledWith({
      left: Math.min(1600, Math.max(0, centerLeft)),
      top: Math.min(1200, Math.max(0, centerTop)),
      behavior: 'smooth',
    });
  });

  it('renders connected nodes and keeps prerequisite parents available to assistive tech', () => {
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelectorAll('svg path[marker-end]')).toHaveLength(1);
    const summaries = [...root.querySelectorAll('.sr-only')].map((element) => element.textContent);
    expect(summaries).toContain('Foundations at rank 5');
  });

  it('hides entry labels that repeat the node title', () => {
    fixture.componentRef.setInput('allocations', new Map());
    fixture.componentRef.setInput('tree', {
      ...tree,
      tabs: [
        {
          id: 1,
          externalTabId: 101,
          name: 'Armor',
          displayOrder: 0,
          nodes: [
            {
              ...rootNode,
              entries: [
                {
                  id: 100,
                  externalEntryId: 1000,
                  name: 'Foundations',
                  rankLimit: 1,
                  displayOrder: 0,
                },
                {
                  id: 101,
                  externalEntryId: 1001,
                  name: 'Foundations',
                  rankLimit: 25,
                  displayOrder: 1,
                },
              ],
            },
          ],
        },
      ],
    });
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    const labels = [...root.querySelectorAll('article span.text-xs.font-medium')].map((element) =>
      element.textContent?.trim(),
    );
    expect(labels).toEqual([]);
    expect(root.textContent).not.toContain('0/1');
    expect(root.textContent).toContain('0/25');
  });

  it('emits compact rank changes', () => {
    const emitted = vi.fn();
    fixture.componentInstance.rankChanged.subscribe(emitted);
    const increase = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
      'button[aria-label="Increase Foundations rank"]',
    );
    increase?.click();
    expect(emitted).toHaveBeenCalledWith({ node: rootNode, entryId: 100, change: 1 });
  });

  it('shows ranks stored on a hidden duplicate entry on the visible progression row', () => {
    fixture.componentRef.setInput('allocations', new Map([[100, 12]]));
    fixture.componentRef.setInput('tree', {
      ...tree,
      tabs: [
        {
          id: 1,
          externalTabId: 101,
          name: 'Armor',
          displayOrder: 0,
          nodes: [
            {
              ...rootNode,
              maxRanks: 1,
              entries: [
                {
                  id: 100,
                  externalEntryId: 1000,
                  name: 'Foundations',
                  rankLimit: 1,
                  displayOrder: 0,
                },
                {
                  id: 101,
                  externalEntryId: 1001,
                  name: 'Foundations',
                  rankLimit: 25,
                  displayOrder: 1,
                },
              ],
            },
          ],
        },
      ],
    });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('12/25');
  });

  it('renders the node cap when an entry rank limit over-reports it', () => {
    fixture.componentRef.setInput('allocations', new Map([[100, 30]]));
    fixture.componentRef.setInput('tree', {
      ...tree,
      tabs: [
        {
          id: 1,
          externalTabId: 101,
          name: 'Armor',
          displayOrder: 0,
          nodes: [
            {
              ...rootNode,
              name: 'Elevating Equipment',
              maxRanks: 30,
              entries: [
                {
                  id: 100,
                  externalEntryId: 1000,
                  name: 'Elevating Equipment',
                  rankLimit: 31,
                  displayOrder: 0,
                },
              ],
            },
          ],
        },
      ],
    });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('30/30');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('30/31');
  });

  it('renders 30/30 when addon reports 31 for both node and entry limits', () => {
    fixture.componentRef.setInput('allocations', new Map([[100, 30]]));
    fixture.componentRef.setInput('tree', {
      ...tree,
      tabs: [
        {
          id: 1,
          externalTabId: 101,
          name: 'Armor',
          displayOrder: 0,
          nodes: [
            {
              ...rootNode,
              name: 'Elevating Equipment',
              maxRanks: 31,
              entries: [
                {
                  id: 100,
                  externalEntryId: 1000,
                  name: 'Elevating Equipment',
                  rankLimit: 31,
                  displayOrder: 0,
                },
              ],
            },
          ],
        },
      ],
    });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('30/30');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('30/31');
  });
});

describe('node rank helpers', () => {
  const sample = node(10, 'Foundations', 0);

  it('uses the highest configured rank cap for the node', () => {
    expect(
      nodeRankCap({
        ...sample,
        maxRanks: 30,
        entries: [{ id: 100, rankLimit: 31, displayOrder: 0 }],
      }),
    ).toBe(30);
  });

  it('shows the node cap instead of an over-reporting entry rank limit', () => {
    const node = {
      ...sample,
      maxRanks: 30,
      entries: [{ id: 100, rankLimit: 31, displayOrder: 0 }],
    };
    expect(displayRankLimit(node, node.entries[0])).toBe(30);
  });

  it('normalizes addon off-by-one limits when node and entry both report 31', () => {
    const node = {
      ...sample,
      maxRanks: 31,
      entries: [{ id: 100, rankLimit: 31, displayOrder: 0 }],
    };
    expect(nodeRankCap(node)).toBe(30);
    expect(displayRankLimit(node, node.entries[0])).toBe(30);
  });

  it('aggregates allocations across all node entries', () => {
    const nodeWithEntries = {
      ...sample,
      entries: [
        { id: 100, rankLimit: 1, displayOrder: 0 },
        { id: 101, rankLimit: 25, displayOrder: 1 },
      ],
    };
    expect(nodeAllocatedRank(nodeWithEntries, new Map([[100, 12]]))).toBe(12);
  });
});

describe('layoutGraph', () => {
  it('places prerequisites above their children deterministically', () => {
    const first = layoutGraph([childNode, rootNode]);
    const second = layoutGraph([childNode, rootNode]);
    const root = first.nodes.find((position) => position.node.id === rootNode.id);
    const child = first.nodes.find((position) => position.node.id === childNode.id);
    expect(root!.y).toBeLessThan(child!.y);
    expect(first).toEqual(second);
  });

  it('hides unnamed pass-through nodes while preserving visible relationships', () => {
    const hidden = node(15, '', 1, [{ parentNodeId: 10, requiredParentRanks: 1 }], 1);
    const descendant = node(20, 'Armorsmithing', 2, [{ parentNodeId: 15, requiredParentRanks: 1 }]);

    const layout = layoutGraph([rootNode, hidden, descendant]);

    expect(layout.nodes.map((position) => position.node.id)).toEqual([10, 20]);
    expect(layout.connectors).toHaveLength(1);
    expect(layout.connectors[0].key).toBe('10-20');
    expect(layout.nodes[1].y).toBeGreaterThan(layout.nodes[0].y);
  });

  it('fans children out under their visible parent instead of a single row', () => {
    const hub = node(10, 'Armorsmithing', 0, [], 30);
    const left = node(20, 'Gauntlets', 1, [{ parentNodeId: 10, requiredParentRanks: 1 }]);
    const center = node(21, 'Helm', 2, [{ parentNodeId: 10, requiredParentRanks: 1 }]);
    const right = node(22, 'Belts', 3, [{ parentNodeId: 10, requiredParentRanks: 1 }]);

    const layout = layoutGraph([hub, left, center, right]);
    const hubPosition = layout.nodes.find((position) => position.node.id === hub.id)!;
    const childPositions = layout.nodes.filter((position) => position.node.id !== hub.id);

    expect(childPositions).toHaveLength(3);
    expect(hubPosition.y).toBeLessThan(Math.min(...childPositions.map((position) => position.y)));
    expect(new Set(childPositions.map((position) => position.x)).size).toBe(3);
    expect(layout.connectors).toHaveLength(3);
  });

  it('shows unnamed hub nodes and stacks grandchildren beneath their branch parent', () => {
    const root = node(10, 'Elevating Equipment', 0, [], 20);
    const hub = node(11, '', 1, [{ parentNodeId: 10, requiredParentRanks: 1 }], 20);
    const branch = node(20, 'Haranir Heightening', 2, [
      { parentNodeId: 11, requiredParentRanks: 1 },
    ]);
    const leaf = node(21, "Nature's Novelties", 3, [{ parentNodeId: 20, requiredParentRanks: 1 }]);

    const layout = layoutGraph([root, hub, branch, leaf]);
    const rootPosition = layout.nodes.find((position) => position.node.id === root.id)!;
    const hubPosition = layout.nodes.find((position) => position.node.id === hub.id)!;
    const branchPosition = layout.nodes.find((position) => position.node.id === branch.id)!;
    const leafPosition = layout.nodes.find((position) => position.node.id === leaf.id)!;

    expect(layout.nodes.map((position) => position.node.id)).toEqual([10, 11, 20, 21]);
    expect(rootPosition.y).toBeLessThan(hubPosition.y);
    expect(hubPosition.y).toBeLessThan(branchPosition.y);
    expect(branchPosition.y).toBeLessThan(leafPosition.y);
    expect(layout.connectors).toHaveLength(3);
  });

  it('returns an empty graph when every node is unnamed', () => {
    expect(layoutGraph([node(15, '', 0)])).toMatchObject({ nodes: [], connectors: [] });
  });

  it('hides milestone nodes and keeps them available for parent tooltips', () => {
    const belts = node(104566, 'Belts', 1, [], 26);
    const milestone = {
      ...node(104499, '', 2, [{ parentNodeId: 104566, requiredParentRanks: 5 }], 1),
      nodeKind: 'milestone' as const,
      requiredRank: 5,
      description: 'Gain +5 Skill when crafting waist armor.',
    };

    const layout = layoutGraph([belts, milestone]);

    expect(layout.nodes.map((position) => position.node.id)).toEqual([104566]);
    expect(milestoneTooltipText(belts, [belts, milestone])).toContain('Rank 5');
    expect(milestoneTooltipText(belts, [belts, milestone])).toContain('Gain +5 Skill');
  });

  it('sizes nodes from their editable entry count so connectors meet the card edge', () => {
    const layout = layoutGraph([
      {
        ...rootNode,
        entries: [
          { id: 100, externalEntryId: 1000, name: 'Foundations', rankLimit: 1, displayOrder: 0 },
          { id: 101, externalEntryId: 1001, name: 'Foundations', rankLimit: 25, displayOrder: 1 },
        ],
      },
      childNode,
    ]);
    const root = layout.nodes.find((position) => position.node.id === rootNode.id)!;
    const child = layout.nodes.find((position) => position.node.id === childNode.id)!;

    expect(root.height).toBeLessThan(72 + 2 * 44 + 8);
    expect(layout.connectors[0].path).toContain(`${root.y + root.height}`);
    expect(child.y).toBe(root.y + root.height + 52);
  });
});

describe('editableEntries', () => {
  it('hides unlock rows when a primary progression entry exists', () => {
    const sample = node(10, 'Gauntlets', 0);
    const entries = [
      { id: 100, externalEntryId: 1000, name: 'Gauntlets', rankLimit: 1, displayOrder: 0 },
      { id: 101, externalEntryId: 1001, name: 'Gauntlets', rankLimit: 25, displayOrder: 1 },
    ];
    expect(editableEntries({ ...sample, entries })).toEqual([entries[1]]);
  });

  it('keeps one progression row when addon data omitted per-entry rank limits', () => {
    const sample = node(10, "Nature's Novelties", 0);
    const entries = [
      {
        id: 100,
        externalEntryId: 1000,
        name: "Nature's Novelties",
        rankLimit: 30,
        displayOrder: 0,
      },
      {
        id: 101,
        externalEntryId: 1001,
        name: "Nature's Novelties",
        rankLimit: 30,
        displayOrder: 1,
      },
    ];
    expect(editableEntries({ ...sample, entries })).toEqual([entries[1]]);
  });

  it('keeps one progression row when the unlock entry has no name', () => {
    const sample = node(10, "Nature's Novelties", 0);
    const entries = [
      { id: 100, externalEntryId: 1000, name: null, rankLimit: 1, displayOrder: 0 },
      {
        id: 101,
        externalEntryId: 1001,
        name: "Nature's Novelties",
        rankLimit: 30,
        displayOrder: 1,
      },
    ];
    expect(editableEntries({ ...sample, entries })).toEqual([entries[1]]);
  });
});

describe('entryNameVisible', () => {
  it('hides labels that repeat the node title', () => {
    const sample = node(10, 'Tool Stones', 0);
    expect(entryNameVisible(sample.entries[0], sample)).toBe(false);
  });

  it('shows labels that differ from the node title', () => {
    const sample = node(10, 'Tool Stones', 0);
    const entry = { ...sample.entries[0], name: 'Bonus yield' };
    expect(entryNameVisible(entry, sample)).toBe(true);
  });

  it('treats apostrophe variants as the same label', () => {
    const sample = node(10, "Nature's Novelties", 0);
    const entry = { ...sample.entries[0], name: 'Nature\u2019s Novelties' };
    expect(entryNameVisible(entry, sample)).toBe(false);
  });
});

function node(
  id: number,
  name: string,
  displayOrder: number,
  prerequisites: ProfessionSkillTreeNode['prerequisites'] = [],
  maxRanks = 30,
): ProfessionSkillTreeNode {
  return {
    id,
    externalNodeId: id,
    name,
    maxRanks,
    requiredRank: 0,
    displayOrder,
    prerequisites,
    entries: [
      {
        id: id * 10,
        externalEntryId: id * 100,
        name,
        rankLimit: maxRanks,
        displayOrder: 0,
      },
    ],
  };
}
