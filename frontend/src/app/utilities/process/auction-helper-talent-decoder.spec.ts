import { encode } from 'cbor-x';
import { deflateSync } from 'fflate';

import { decodeAuctionHelperTalentExport } from './auction-helper-talent-decoder';

describe('decodeAuctionHelperTalentExport', () => {
  it('decodes and maps the full AHCBOR1 talent pipeline', () => {
    const payload = packagePayload({
      meta: { scope: 'profession_talents', characterKey: 'Player-Realm' },
      character: { key: 'Player-Realm', name: 'Player', realm: 'Realm' },
      profession: {
        skillLineID: 2872,
        professionName: 'Blacksmithing',
        specializationTree: {
          configID: 123,
          skillLineID: 2872,
          expansionID: 10,
          tierName: 'Khaz Algar Blacksmithing',
          tabs: [
            {
              treeID: 999,
              tabInfo: { name: 'Weaponsmithing' },
              nodes: [
                {
                  nodeID: 101,
                  childPathIDs: [102],
                  nodeInfo: {
                    maxRanks: 13,
                    currentRank: 13,
                    activeEntry: { entryID: 201, rank: 12 },
                  },
                  entries: [
                    {
                      entryID: 201,
                      entryInfo: { maxRanks: 30 },
                      definitionInfo: { overrideName: 'Weaponsmithing' },
                    },
                  ],
                },
                {
                  nodeID: 102,
                  nodeInfo: { maxRanks: 10 },
                  entries: [{ entryID: 202, entryInfo: { maxRanks: 10 } }],
                },
              ],
            },
          ],
        },
      },
    });

    expect(decodeAuctionHelperTalentExport(payload, 'profession_talents')).toMatchObject({
      scope: 'profession_talents',
      professions: [
        {
          skillLineId: 2872,
          trees: [
            {
              treeId: 123,
              skillLineId: 2872,
              expansionId: 11,
              tabs: [
                {
                  tabId: 999,
                  nodes: [
                    {
                      nodeId: 101,
                      name: 'Weaponsmithing',
                      maxRanks: 13,
                      parentNodeIds: [],
                      entries: [{ entryId: 201, name: 'Weaponsmithing', rankLimit: 30 }],
                    },
                    {
                      nodeId: 102,
                      parentNodeIds: [101],
                      entries: [{ entryId: 202, rankLimit: 10 }],
                    },
                  ],
                },
              ],
            },
          ],
          allocations: [{ nodeId: 101, entryId: 201, rank: 12 }],
        },
      ],
    });
  });

  it('rejects unsupported and mismatched scopes', () => {
    expect(() =>
      decodeAuctionHelperTalentExport(
        packagePayload({ meta: { scope: 'inventory' } }),
        'inventory',
      ),
    ).toThrowError(/Unsupported AuctionHelper export scope/);
    expect(() =>
      decodeAuctionHelperTalentExport(
        packagePayload({ meta: { scope: 'profession_talents' }, profession: {} }),
        'professions_talents',
      ),
    ).toThrowError(/does not match/);
  });

  it('supports the real single-profession wrapper shape', () => {
    const result = decodeAuctionHelperTalentExport(
      packagePayload({
        meta: { scope: 'profession', characterKey: 'Player-Realm' },
        character: { name: 'Player', realm: 'Realm' },
        profession: {
          skillLineID: 2872,
          professionName: 'Blacksmithing',
          specializationTree: { tabs: [] },
        },
      }),
      'profession',
    );

    expect(result).toMatchObject({
      scope: 'profession',
      character: { key: 'Player-Realm', name: 'Player', realm: 'Realm' },
      professions: [{ skillLineId: 2872, name: 'Blacksmithing' }],
    });
  });

  it('supports the full character dump wrapper shape', () => {
    const result = decodeAuctionHelperTalentExport(
      packagePayload({
        meta: { scope: 'character', characterKey: 'Player-Realm' },
        character: {
          meta: { name: 'Player', realm: 'Realm' },
          professions: {
            '2872': {
              skillLineID: 2872,
              professionName: 'Blacksmithing',
              specializationTree: {
                configID: 123,
                skillLineID: 2872,
                expansionID: 10,
                tierName: 'Khaz Algar Blacksmithing',
                tabs: [{ treeID: 999, tabInfo: { name: 'Weaponsmithing' }, nodes: [] }],
              },
            },
          },
        },
      }),
      'character',
    );

    expect(result).toMatchObject({
      scope: 'character',
      character: { key: 'Player-Realm', name: 'Player', realm: 'Realm' },
      professions: [
        {
          skillLineId: 2872,
          name: 'Blacksmithing',
          trees: [
            {
              treeId: 123,
              skillLineId: 2872,
              expansionId: 11,
              name: 'Khaz Algar Blacksmithing',
              tabs: [{ tabId: 999, name: 'Weaponsmithing' }],
            },
          ],
        },
      ],
    });
  });

  it('rejects malformed, oversized, and dangerous decoded data', () => {
    expect(() => decodeAuctionHelperTalentExport('AHCBOR1:not-base64!', null)).toThrowError();
    const unsafe = new Map<unknown, unknown>([
      ['meta', { scope: 'profession_talents' }],
      ['constructor', { polluted: true }],
    ]);
    expect(() => decodeAuctionHelperTalentExport(packagePayload(unsafe), null)).toThrowError(
      /Unsafe decoded key/,
    );
  });
});

function packagePayload(value: unknown): string {
  const compressed = deflateSync(encode(value), { level: 6 });
  let binary = '';
  compressed.forEach((byte) => (binary += String.fromCharCode(byte)));
  return `AHCBOR1:${btoa(binary)}`;
}
