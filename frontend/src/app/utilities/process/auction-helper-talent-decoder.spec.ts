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
          tabs: [
            {
              treeID: 999,
              tabInfo: { name: 'Weaponsmithing' },
              nodes: [
                {
                  nodeID: 101,
                  nodeInfo: {
                    maxRanks: 30,
                    currentRank: 12,
                    activeEntry: { entryID: 201 },
                  },
                  entries: [{ entryID: 201, entryInfo: { maxRanks: 30 } }],
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
              treeId: 999,
              nodes: [{ nodeId: 101, maxRanks: 30, entries: [{ entryId: 201, rankLimit: 30 }] }],
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
