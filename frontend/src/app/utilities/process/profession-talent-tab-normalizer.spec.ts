import {
  inferParentNodeIdsFromExportOrder,
  resolveParentNodeIdsFromEdges,
  resolveTalentParentNodeIds,
} from './profession-talent-parent-inference';
import { normalizeTalentTab } from './profession-talent-tab-normalizer';

describe('profession-talent-parent-inference', () => {
  it('prefers explicit child path relationships when present', () => {
    const parents = resolveTalentParentNodeIds(
      [
        { nodeId: 101, name: 'Root' },
        { nodeId: 102, name: 'Child' },
      ],
      new Map([[101, [102]]]),
    );

    expect(parents.get(102)).toEqual([101]);
  });

  it('merges explicit path edges with child path ids', () => {
    const parents = resolveTalentParentNodeIds(
      [
        { nodeId: 104565, name: 'Armorsmithing' },
        { nodeId: 104566, name: 'Belts' },
        { nodeId: 104567, name: 'Gauntlets' },
      ],
      {
        childPathIdsByNodeId: new Map([[104566, [104567]]]),
        edges: [{ fromPathId: 104565, toPathId: 104566 }],
      },
    );

    expect(parents.get(104566)).toEqual([104565]);
    expect(parents.get(104567)).toEqual([104566]);
  });

  it('resolves parents from edges alone', () => {
    const parents = resolveParentNodeIdsFromEdges([
      { fromPathId: 10, toPathId: 20 },
      { fromPathId: 20, toPathId: 30 },
    ]);

    expect(parents.get(20)).toEqual([10]);
    expect(parents.get(30)).toEqual([20]);
  });

  it('infers three-leaf branch hubs from export order when child paths are missing', () => {
    const nodes = [
      { nodeId: 107757, name: "Nature's Novelties" },
      { nodeId: 107758, name: 'Worldsoul Wards' },
      { nodeId: 107759, name: 'Azerothian Arms' },
      { nodeId: 107760, name: 'Haranir Heightening' },
      { nodeId: 107761, name: 'Trollish Tools' },
      { nodeId: 107762, name: 'Berserker Brawn' },
      { nodeId: 107763, name: "Zul'Aman Zeal" },
      { nodeId: 107764, name: 'Amani Augments' },
      { nodeId: 107765, name: "Quel'Thalas Quality" },
      { nodeId: 107766, name: 'Eversong Empowerments' },
      { nodeId: 107767, name: "Silvermoon's Spellpower" },
      { nodeId: 107768, name: 'Thalassian Talents' },
      { nodeId: 107769, name: 'Elevating Equipment' },
    ];

    const parents = inferParentNodeIdsFromExportOrder(nodes);

    expect(parents.get(107757)).toEqual([107760]);
    expect(parents.get(107758)).toEqual([107760]);
    expect(parents.get(107759)).toEqual([107760]);
    expect(parents.get(107760)).toEqual([107769]);
    expect(parents.get(107761)).toEqual([107764]);
    expect(parents.get(107768)).toEqual([107769]);
  });
});

describe('profession-talent-tab-normalizer', () => {
  it('normalizes schema 12 paths, milestones, and edges into graph nodes', () => {
    const tab = normalizeTalentTab({
      treeID: 1068,
      tabInfo: { name: 'Armorsmithing', description: 'Armor branch' },
      paths: [
        {
          nodeKind: 'path',
          nodeID: 104565,
          nodeName: 'Armorsmithing',
          nodeDescription: 'Train in armor.',
          childPathIDs: [104566, 104568],
          nodeInfo: { maxRanks: 30, activeRank: 5, currentRank: 5 },
        },
        {
          nodeKind: 'path',
          nodeID: 104566,
          nodeName: 'Belts',
          nodeDescription: 'Craft belts.',
          childPathIDs: [104567],
          nodeInfo: { maxRanks: 26, activeRank: 1, currentRank: 1 },
        },
        {
          nodeKind: 'path',
          nodeID: 104567,
          nodeName: 'Gauntlets',
          nodeDescription: 'Craft gauntlets.',
          nodeInfo: { maxRanks: 26, activeRank: 0, currentRank: 0 },
        },
        {
          nodeKind: 'path',
          nodeID: 104568,
          nodeName: 'Helm',
          nodeDescription: 'Craft helms.',
          nodeInfo: { maxRanks: 26, activeRank: 0, currentRank: 0 },
        },
      ],
      milestones: [
        {
          nodeKind: 'milestone',
          nodeID: 104499,
          parentPathID: 104566,
          parentPathName: 'Belts',
          milestoneRank: 5,
          unlockRank: 5,
          nodeDescription: 'Gain +5 Skill when crafting waist armor.',
          nodeInfo: { maxRanks: 1, activeRank: 0, currentRank: 0 },
        },
      ],
      edges: [
        { fromPathID: 104565, toPathID: 104566, kind: 'path' },
        { fromPathID: 104565, toPathID: 104568, kind: 'path' },
      ],
    });

    expect(tab).toMatchObject({
      tabId: 1068,
      name: 'Armorsmithing',
      description: 'Armor branch',
      allocations: [
        { nodeId: 104565, entryId: 104565, rank: 5 },
        { nodeId: 104566, entryId: 104566, rank: 1 },
      ],
    });

    const nodesById = Object.fromEntries(tab!.nodes.map((node) => [node.nodeId, node]));
    expect(nodesById[104566].parentNodeIds).toEqual([104565]);
    expect(nodesById[104567].parentNodeIds).toEqual([104566]);
    expect(nodesById[104568].parentNodeIds).toEqual([104565]);
    expect(nodesById[104499]).toMatchObject({
      nodeKind: 'milestone',
      description: 'Gain +5 Skill when crafting waist armor.',
      maxRanks: 1,
      requiredRank: 5,
      parentNodeIds: [104566],
      entries: [{ entryId: 104499, rankLimit: 1 }],
    });
  });

  it('keeps milestone descriptions out of the bounded name field', () => {
    const longDescription = `Gain +${'very '.repeat(40)}long perk text.`;
    const tab = normalizeTalentTab({
      treeID: 1068,
      paths: [
        {
          nodeID: 104566,
          nodeName: 'Belts',
          nodeInfo: { maxRanks: 26, activeRank: 1, currentRank: 1 },
        },
      ],
      milestones: [
        {
          nodeID: 104499,
          parentPathID: 104566,
          milestoneRank: 5,
          nodeDescription: longDescription,
          nodeInfo: { maxRanks: 1, activeRank: 0, currentRank: 0 },
        },
      ],
      edges: [],
    });

    const milestone = tab!.nodes.find((node) => node.nodeId === 104499);
    expect(milestone?.name).toBeUndefined();
    expect(milestone?.description).toBe(longDescription);
    expect(milestone?.description?.length ?? 0).toBeLessThanOrEqual(4096);
  });

  it('uses node max ranks when entry definitions under-report rank limits', () => {
    const tab = normalizeTalentTab({
      treeID: 1068,
      paths: [
        {
          nodeID: 119324,
          nodeName: 'Thread Processing',
          nodeInfo: { maxRanks: 30, activeRank: 12, currentRank: 12 },
          entries: [{ entryID: 119324, definitionInfo: { maxRanks: 1 } }],
        },
      ],
      edges: [],
    });

    const node = tab!.nodes.find((candidate) => candidate.nodeId === 119324);
    expect(node?.maxRanks).toBe(30);
    expect(node?.entries[0]?.rankLimit).toBe(30);
    expect(tab?.allocations).toEqual([{ nodeId: 119324, entryId: 119324, rank: 12 }]);
  });

  it('infers node max ranks from entry limits when node info omits them', () => {
    const tab = normalizeTalentTab({
      treeID: 1076,
      paths: [
        {
          nodeID: 107769,
          nodeName: 'Elevating Equipment',
          nodeInfo: { activeRank: 18, currentRank: 18 },
          entries: [{ entryID: 108769, definitionInfo: { maxRanks: 31 } }],
        },
      ],
      edges: [],
    });

    const node = tab!.nodes.find((candidate) => candidate.nodeId === 107769);
    expect(node?.maxRanks).toBe(30);
    expect(node?.entries[0]?.rankLimit).toBe(30);
    expect(tab?.allocations).toEqual([{ nodeId: 107769, entryId: 108769, rank: 18 }]);
  });

  it('normalizes addon off-by-one rank limits when node and entry both report 31', () => {
    const tab = normalizeTalentTab({
      treeID: 1076,
      paths: [
        {
          nodeID: 107769,
          nodeName: 'Elevating Equipment',
          nodeInfo: { maxRanks: 31, activeRank: 30, currentRank: 30 },
          entries: [{ entryID: 108769, definitionInfo: { maxRanks: 31 } }],
        },
      ],
      edges: [],
    });

    const node = tab!.nodes.find((candidate) => candidate.nodeId === 107769);
    expect(node?.maxRanks).toBe(30);
    expect(node?.entries[0]?.rankLimit).toBe(30);
  });

  it('caps entry rank limits that over-report the node max ranks', () => {
    const tab = normalizeTalentTab({
      treeID: 1076,
      paths: [
        {
          nodeID: 107769,
          nodeName: 'Elevating Equipment',
          nodeInfo: { maxRanks: 30, activeRank: 30, currentRank: 30 },
          entries: [{ entryID: 108769, definitionInfo: { maxRanks: 31 } }],
        },
      ],
      edges: [],
    });

    const node = tab!.nodes.find((candidate) => candidate.nodeId === 107769);
    expect(node?.maxRanks).toBe(30);
    expect(node?.entries[0]?.rankLimit).toBe(30);
  });

  it('routes definition tooltip text from entry names into descriptions', () => {
    const tooltip =
      'Learn a sub-specialization of your choice.|n|nGain the ability to use Polishing Cloth to apply finishing touches to your designs.';
    const tab = normalizeTalentTab({
      treeID: 1068,
      paths: [
        {
          nodeID: 104566,
          nodeName: 'Polishing Cloth',
          nodeInfo: { maxRanks: 1, activeRank: 1, currentRank: 1 },
          entries: [{ entryID: 104566, definitionInfo: { name: tooltip } }],
        },
      ],
      edges: [],
    });

    const node = tab!.nodes.find((candidate) => candidate.nodeId === 104566);
    expect(node?.name).toBe('Polishing Cloth');
    expect(node?.entries[0]?.name).toBeUndefined();
    expect(node?.entries[0]?.description).toBe(tooltip);
    expect(node?.entries[0]?.description?.length ?? 0).toBeLessThanOrEqual(4096);
  });

  it('keeps legacy flat nodes when structured collections are absent', () => {
    const tab = normalizeTalentTab({
      treeID: 999,
      nodes: [
        {
          nodeID: 101,
          childPathIDs: [102],
          nodeInfo: { maxRanks: 30, currentRank: 12, activeEntry: { entryID: 201, rank: 12 } },
          entries: [{ entryID: 201, definitionInfo: { overrideName: 'Weaponsmithing' } }],
        },
        { nodeID: 102, nodeInfo: { maxRanks: 10 }, entries: [{ entryID: 202 }] },
      ],
    });

    expect(tab?.nodes).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ nodeId: 101, parentNodeIds: [], name: 'Weaponsmithing' }),
        expect.objectContaining({ nodeId: 102, parentNodeIds: [101] }),
      ]),
    );
    expect(tab?.allocations).toEqual([{ nodeId: 101, entryId: 201, rank: 12 }]);
  });
});
