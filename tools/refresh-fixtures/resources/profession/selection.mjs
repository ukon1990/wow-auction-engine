import { ROOT_SELECTIONS } from "../../config.mjs";
import { pickAllSkillTierIds, pickDefaultSkillTierIds } from "../../sampling/skill-tiers.mjs";

export async function resolveSelectedProfessions(apiClient, args, selectionConfig = ROOT_SELECTIONS.profession) {
    if (args.skillTierIds?.length && !args.professionIds?.length) {
        throw new Error("--skill-tier-id requires --profession-id (skill tier ids are not profession ids).");
    }

    if (!args.professionIds) {
        return selectionConfig.professions;
    }

    const selected = [];

    for (const professionId of args.professionIds) {
        let professionPayload;
        try {
            professionPayload = await apiClient.fetchJson(`profession/${professionId}`);
        } catch (error) {
            const hint = args.skillTierIds?.length
                ? ""
                : " If you passed a skill tier id by mistake, use --profession-id <professionId> --skill-tier-id <tierId>.";
            throw new Error(`Could not load profession ${professionId} from Blizzard.${hint}`, { cause: error });
        }

        const skillTierIds = args.skillTierIds?.length
            ? args.skillTierIds
            : args.full
              ? pickAllSkillTierIds(professionPayload.skill_tiers)
              : pickDefaultSkillTierIds(professionPayload.skill_tiers);

        selected.push({
            id: professionId,
            name: professionPayload.name?.en_US ?? professionPayload.name ?? "unknown",
            skillTierIds,
            metadataOnly: skillTierIds.length === 0,
        });
    }

    if (selected.length === 0) {
        throw new Error("No professions selected.");
    }
    return selected;
}
