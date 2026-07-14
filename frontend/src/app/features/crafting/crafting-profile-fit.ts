import type { CraftingProfileFit } from '@api/generated';

/** Concise, honest profile context for the existing crafting result card. */
export function profileFitSummary(profileFit: CraftingProfileFit): string {
  if (profileFit.bestCandidate) {
    if (profileFit.craftable === true && profileFit.bestCandidate.predictedQuality != null) {
      return $localize`:@@crafting.profileFit.evaluatedBestCrafter:Best crafter: ${profileFit.bestCandidate.characterName}:INTERPOLATION: (quality ${profileFit.bestCandidate.predictedQuality}:INTERPOLATION_2:).`;
    }
    if (profileFit.diagnostic === 'profile_evaluated' && profileFit.craftable === false) {
      return $localize`:@@crafting.profileFit.evaluatedNotCraftable:${profileFit.bestCandidate.characterName}:INTERPOLATION: cannot craft this recipe with the current profile.`;
    }
    return profileFit.craftable === null
      ? $localize`:@@crafting.profileFit.heuristicBestCrafter:Heuristic best crafter: ${profileFit.bestCandidate.characterName}:INTERPOLATION:. Crafting rules are unavailable.`
      : $localize`:@@crafting.profileFit.bestCrafter:Best crafter: ${profileFit.bestCandidate.characterName}:INTERPOLATION:.`;
  }
  if (profileFit.craftable === null) {
    return $localize`:@@crafting.profileFit.rulesUnavailable:Crafting rules are unavailable; ranking is heuristic.`;
  }
  return $localize`:@@crafting.profileFit.noMatch:No matching character profile.`;
}
