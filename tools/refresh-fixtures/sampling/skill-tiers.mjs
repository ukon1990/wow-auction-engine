export function pickDefaultSkillTierIds(skillTiers) {
    const ids = (skillTiers ?? []).map((tier) => tier.id).filter((id) => Number.isFinite(id));
    ids.sort((left, right) => left - right);
    if (ids.length === 0) {
        return [];
    }
    if (ids.length <= 2) {
        return ids;
    }
    return [ids.at(-1), ids.at(-2)];
}

export function pickAllSkillTierIds(skillTiers) {
    return (skillTiers ?? [])
        .map((tier) => tier.id)
        .filter((id) => Number.isFinite(id))
        .sort((left, right) => left - right);
}

export function trimProfessionToSkillTiers(professionPayload, selectedTierIds) {
    const selectedSet = new Set(selectedTierIds);

    return {
        ...professionPayload,
        skill_tiers: (professionPayload.skill_tiers ?? []).filter((tier) => selectedSet.has(tier.id)),
    };
}
