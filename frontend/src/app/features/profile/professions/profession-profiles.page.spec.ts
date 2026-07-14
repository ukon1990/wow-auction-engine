import { preferredTreeId } from './profession-profiles.page';

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
});
