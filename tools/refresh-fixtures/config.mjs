/**
 * Default fixture refresh configuration.
 *
 * - Default run (no --profession-id): uses rootSelections.profession with sampled tiers/recipes.
 * - Explicit --profession-id: loads from Blizzard API, all tiers, all recipes (metadata-only if no tiers).
 */
export const blizzardConfig = {
    baseUrl: process.env.BLIZZARD_BASE_URL ?? "https://us.api.blizzard.com/data/wow",
    tokenUrl: process.env.BLIZZARD_TOKEN_URL ?? "https://eu.battle.net/oauth/token",
    namespace: process.env.BLIZZARD_NAMESPACE ?? "static-us",
    samplePerTier: parseInt(process.env.PROFESSION_FIXTURE_SAMPLE_SIZE ?? "6", 10),
};

export const ROOT_SELECTIONS = {
    profession: {
        sampleSize: blizzardConfig.samplePerTier,
        professions: [
            { id: 164, name: "Blacksmithing", skillTierIds: [2907, 2751] },
            { id: 333, name: "Enchanting", skillTierIds: [2909, 2753] },
            { id: 182, name: "Herbalism", skillTierIds: [2912, 2550] },
            { id: 356, name: "Fishing", skillTierIds: [2911, 2826] },
        ],
    },
};
