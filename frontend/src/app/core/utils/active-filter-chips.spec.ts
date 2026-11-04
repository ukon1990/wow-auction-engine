import { FilterSection } from '@ui';

import { formatCopper, parseActiveFilterChipId, toActiveFilterChips } from './active-filter-chips';

const sections: FilterSection[] = [
  {
    id: 'qualityIds',
    label: 'Quality',
    type: 'multiSelect',
    options: [
      { id: 'qualityIds:4', label: 'Epic', selected: true },
      { id: 'qualityIds:3', label: 'Rare', selected: false },
    ],
  },
  {
    id: 'itemClassIds',
    label: 'Class',
    type: 'select',
    options: [{ id: 'itemClassIds:0', label: 'Consumable', selected: true }],
  },
  { id: 'price', label: 'Price', type: 'range', options: [], selectedMax: 5_000_000 },
  {
    id: 'saleRatePercent',
    label: 'Sale rate %',
    type: 'range',
    options: [],
    selectedMin: 10,
    selectedMax: 50,
  },
  {
    id: 'recipeOnly',
    label: 'Has recipe',
    type: 'boolean',
    options: [{ id: 'recipeOnly:true', label: 'Has recipe', selected: true }],
  },
];

describe('toActiveFilterChips', () => {
  it('makes one plain chip per active filter', () => {
    expect(toActiveFilterChips(sections).map((chip) => chip.label)).toEqual([
      'Quality: Epic',
      'Class: Consumable',
      'Price ≤ 500g',
      'Sale rate % 10%–50%',
      'Has recipe',
    ]);
  });

  it('round-trips chip ids', () => {
    const ids = toActiveFilterChips(sections).map((chip) => parseActiveFilterChipId(chip.id));
    expect(ids).toEqual([
      { kind: 'option', sectionId: 'qualityIds', optionId: 'qualityIds:4' },
      { kind: 'select', sectionId: 'itemClassIds' },
      { kind: 'range', sectionId: 'price' },
      { kind: 'range', sectionId: 'saleRatePercent' },
      { kind: 'option', sectionId: 'recipeOnly', optionId: 'recipeOnly:true' },
    ]);
  });
});

describe('formatCopper', () => {
  it('splits copper into coins', () => {
    expect(formatCopper(125_000)).toBe('12g 50s');
    expect(formatCopper(42)).toBe('42c');
    expect(formatCopper(0)).toBe('0c');
  });
});
