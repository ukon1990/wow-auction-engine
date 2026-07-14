import type { CraftingProfileFit } from '@api/generated';

/** Concise, honest profile context for the existing crafting result card. */
export function profileFitSummary(profileFit: CraftingProfileFit): string {
  if (profileFit.bestCandidate) {
    return profileFit.craftable === null
      ? $localize`:@@crafting.profileFit.heuristicBestCrafter:Heuristic best crafter: ${profileFit.bestCandidate.characterName}:INTERPOLATION:. Crafting rules are unavailable.`
      : $localize`:@@crafting.profileFit.bestCrafter:Best crafter: ${profileFit.bestCandidate.characterName}:INTERPOLATION:.`;
  }
  if (profileFit.craftable === null) {
    return $localize`:@@crafting.profileFit.rulesUnavailable:Crafting rules are unavailable; ranking is heuristic.`;
  }
  return $localize`:@@crafting.profileFit.noMatch:No matching character profile.`;
}
