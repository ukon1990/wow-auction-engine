import { AdminExpansion, AdminExpansionItemRange } from '@api/generated';
import {
  defaultCreateRangeValues,
  defaultExpansionRangeFilters,
  filterExpansionRanges,
  sortExpansionRanges,
} from './expansion-range-filters';

const expansion = {
  id: 2,
  slug: 'tbc',
  name: 'The Burning Crusade',
  majorVersion: 2,
  displayOrder: 2,
};

const ranges: AdminExpansionItemRange[] = [
  {
    id: 1,
    expansion,
    startItemId: 100,
    endItemId: 200,
    source: 'manual',
    enabled: true,
    createdAt: '2026-06-23T10:00:00Z',
    updatedAt: '2026-06-23T10:00:00Z',
  },
  {
    id: 2,
    expansion,
    startItemId: 300,
    endItemId: 400,
    source: 'itemversion',
    enabled: false,
    createdAt: '2026-06-23T10:00:00Z',
    updatedAt: '2026-06-23T10:00:00Z',
  },
];

describe('expansion-range-filters', () => {
  it('filters by expansion, source, enabled, and item id', () => {
    const filtered = filterExpansionRanges(ranges, {
      ...defaultExpansionRangeFilters(),
      expansionId: '2',
      source: 'manual',
      enabled: 'true',
      itemId: '150',
    });

    expect(filtered).toEqual([ranges[0]]);
  });

  it('sorts by expansion display order then start item id', () => {
    const otherExpansion = {
      id: 1,
      slug: 'vanilla',
      name: 'Vanilla',
      majorVersion: 1,
      displayOrder: 1,
    };
    const unsorted: AdminExpansionItemRange[] = [
      { ...ranges[1], expansion: otherExpansion, startItemId: 50 },
      { ...ranges[0], startItemId: 10 },
    ];

    expect(sortExpansionRanges(unsorted).map((range) => range.startItemId)).toEqual([50, 10]);
  });

  it('defaults create values to the latest expansion and its max item id', () => {
    const expansions: AdminExpansion[] = [
      {
        id: 1,
        slug: 'vanilla',
        name: 'Vanilla',
        majorVersion: 1,
        displayOrder: 1,
      },
      {
        id: 3,
        slug: 'wotlk',
        name: 'Wrath of the Lich King',
        majorVersion: 3,
        displayOrder: 3,
      },
    ];
    const latestExpansionRanges: AdminExpansionItemRange[] = [
      {
        id: 1,
        expansion: expansions[1],
        startItemId: 1000,
        endItemId: 1500,
        source: 'manual',
        enabled: true,
        createdAt: '2026-06-23T10:00:00Z',
        updatedAt: '2026-06-23T10:00:00Z',
      },
      {
        id: 2,
        expansion: expansions[1],
        startItemId: 2000,
        endItemId: 2500,
        source: 'manual',
        enabled: true,
        createdAt: '2026-06-23T10:00:00Z',
        updatedAt: '2026-06-23T10:00:00Z',
      },
      {
        id: 3,
        expansion: expansions[0],
        startItemId: 1,
        endItemId: 999,
        source: 'manual',
        enabled: true,
        createdAt: '2026-06-23T10:00:00Z',
        updatedAt: '2026-06-23T10:00:00Z',
      },
    ];

    expect(defaultCreateRangeValues(expansions, latestExpansionRanges)).toEqual({
      expansionId: '3',
      startItemId: '2500',
    });
  });

  it('defaults start item id to 1 when the latest expansion has no ranges', () => {
    const expansions: AdminExpansion[] = [
      {
        id: 2,
        slug: 'tbc',
        name: 'The Burning Crusade',
        majorVersion: 2,
        displayOrder: 2,
      },
    ];

    expect(defaultCreateRangeValues(expansions, [])).toEqual({
      expansionId: '2',
      startItemId: '1',
    });
  });
});
