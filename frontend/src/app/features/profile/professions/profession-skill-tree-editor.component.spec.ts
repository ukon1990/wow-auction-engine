import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ProfessionSkillTree, ProfessionSkillTreeNode } from '@api/generated';
import { ProfessionSkillTreeEditor, layoutGraph } from './profession-skill-tree-editor.component';

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

  it('renders connected nodes and names prerequisite parents', () => {
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelectorAll('svg path[marker-end]')).toHaveLength(1);
    expect(root.textContent).toContain('Foundations at rank 5');
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
});

describe('layoutGraph', () => {
  it('places prerequisites in an earlier horizontal layer deterministically', () => {
    const first = layoutGraph([childNode, rootNode]);
    const second = layoutGraph([childNode, rootNode]);
    const root = first.nodes.find((position) => position.node.id === rootNode.id);
    const child = first.nodes.find((position) => position.node.id === childNode.id);
    expect(root!.x).toBeLessThan(child!.x);
    expect(first).toEqual(second);
  });
});

function node(
  id: number,
  name: string,
  displayOrder: number,
  prerequisites: ProfessionSkillTreeNode['prerequisites'] = [],
): ProfessionSkillTreeNode {
  return {
    id,
    externalNodeId: id,
    name,
    maxRanks: 30,
    requiredRank: 0,
    displayOrder,
    prerequisites,
    entries: [
      {
        id: id * 10,
        externalEntryId: id * 100,
        name,
        rankLimit: 30,
        displayOrder: 0,
      },
    ],
  };
}
