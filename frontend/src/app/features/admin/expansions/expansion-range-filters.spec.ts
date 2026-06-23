import { AdminExpansionItemRange } from '@api/generated';
import {
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
});
