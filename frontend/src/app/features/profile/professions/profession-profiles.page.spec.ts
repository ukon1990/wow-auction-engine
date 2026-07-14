import { preferredTreeId, resolveProfileTreeId } from './profession-profiles.page';

describe('ProfessionProfilesPage tree selection', () => {
  it('selects the specialization tree with the highest exported tree ID', () => {
    expect(
      preferredTreeId([
        { id: 12, externalTreeId: 900 },
        { id: 38, externalTreeId: 700 },
        { id: 21, externalTreeId: 1200 },
      ]),
    ).toBe(21);
  });

  it('has no selection when no specialization trees are available', () => {
    expect(preferredTreeId([])).toBeNull();
  });

  it('keeps the saved profile tree even when another tree has a higher exported ID', () => {
    const trees = [
      { id: 12, externalTreeId: 900 },
      { id: 38, externalTreeId: 1200 },
    ];
    expect(resolveProfileTreeId({ treeId: 12 }, trees)).toBe(12);
  });

  it('falls back to the highest exported tree when the profile has no saved tree', () => {
    const trees = [
      { id: 12, externalTreeId: 900 },
      { id: 38, externalTreeId: 1200 },
    ];
    expect(resolveProfileTreeId({ treeId: null }, trees)).toBe(38);
  });
});
