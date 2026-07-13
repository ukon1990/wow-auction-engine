import type { CraftingProfileFit } from '@api/generated';

import { profileFitSummary } from './crafting-profile-fit';

describe('profileFitSummary', () => {
  const fit = (overrides: Partial<CraftingProfileFit> = {}): CraftingProfileFit => ({
    state: 'configured',
    craftable: null,
    diagnostic: 'recipe_rules_unavailable_heuristic_ranking',
    alternatives: [],
    ...overrides,
  });

  it('labels a best candidate as heuristic when rules are unavailable', () => {
    expect(
      profileFitSummary(
        fit({
          bestCandidate: {
            characterId: 1,
            characterName: 'Aelwyn',
            region: 'eu',
            realmName: 'Draenor',
            professionId: 171,
            allocationCount: 2,
          },
        }),
      ),
    ).toContain('Heuristic best crafter: Aelwyn');
    expect(profileFitSummary(fit())).toContain('rules are unavailable');
  });

  it('names the best candidate only when craftability is known', () => {
    expect(
      profileFitSummary(
        fit({
          craftable: true,
          bestCandidate: {
            characterId: 1,
            characterName: 'Aelwyn',
            region: 'eu',
            realmName: 'Draenor',
            professionId: 171,
            allocationCount: 2,
          },
        }),
      ),
    ).toContain('Aelwyn');
  });
});
