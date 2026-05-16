import path from "node:path";
import { ROOT_SELECTIONS } from "../../config.mjs";
import { isExcludedEndpoint } from "../../discovery/endpoint-paths.mjs";
import { buildRecursiveEndpointWrites } from "../../discovery/traversal.mjs";
import { addManagedWrite, addSupplementalTestFixtureWrites } from "../../plan/writes.mjs";
import { planManagedFilePrunes } from "../../plan/prune.mjs";
import { pickAllRecipeIds, pickRecipeIds, trimSkillTierToRecipes } from "../../sampling/recipes.mjs";
import { trimProfessionToSkillTiers } from "../../sampling/skill-tiers.mjs";
import { addDerivedLocalFixtureWrites } from "../modified-crafting/index-writes.mjs";
import { resolveSelectedProfessions } from "./selection.mjs";

export async function buildProfessionFixturePlan({
    apiClient,
    args,
    paths,
    selectionConfig = ROOT_SELECTIONS.profession,
}) {
    const progress = args.progress;
    const sampleSize = args.sampleSize ?? selectionConfig.sampleSize;
    const useFullDownload = Boolean(args.full);
    progress?.log("Resolving professions...");
    const selectedProfessions = await resolveSelectedProfessions(apiClient, args, selectionConfig);
    const allowlistedProfessionIds = new Set(selectedProfessions.map((profession) => profession.id));
    const writesByFile = new Map();
    const desiredFiles = new Set([paths.manifestFile]);
    const rootPayloads = new Map();
    const sampledRecipeIds = new Set();
    const sampleManifest = [];

    const professionIndex = await apiClient.fetchJson("profession/index");
    const filteredIndex = {
        ...professionIndex,
        professions: (professionIndex.professions ?? []).filter((profession) =>
            allowlistedProfessionIds.has(profession.id),
        ),
    };
    rootPayloads.set("profession/index", filteredIndex);

    for (const profession of selectedProfessions) {
        progress?.log(`Fetching profession ${profession.id} (${profession.name})...`);
        const professionPayload = await apiClient.fetchJson(`profession/${profession.id}`);
        const downloadedTierIds = [];
        const tierCount = profession.skillTierIds.length;

        for (const [tierIndex, skillTierId] of profession.skillTierIds.entries()) {
            const skillTierEndpointPath = `profession/${profession.id}/skill-tier/${skillTierId}`;
            progress?.log(
                `Fetching skill tier ${skillTierId} for profession ${profession.id} (${tierIndex + 1}/${tierCount})...`,
            );
            const tierPayload = await apiClient.fetchJson(skillTierEndpointPath);
            const chosenRecipes = useFullDownload
                ? pickAllRecipeIds(tierPayload)
                : pickRecipeIds(tierPayload, sampleSize);
            const trimmedTierPayload = trimSkillTierToRecipes(
                tierPayload,
                chosenRecipes.map((recipe) => recipe.id),
            );

            downloadedTierIds.push(skillTierId);
            rootPayloads.set(skillTierEndpointPath, trimmedTierPayload);

            sampleManifest.push({
                professionId: profession.id,
                professionName: professionPayload.name ?? "unknown",
                skillTierId,
                skillTierName: tierPayload.name ?? "unknown",
                recipes: chosenRecipes,
            });

            for (const recipe of chosenRecipes) {
                sampledRecipeIds.add(recipe.id);
            }
        }

        const trimmedProfessionPayload =
            downloadedTierIds.length === 0
                ? professionPayload
                : trimProfessionToSkillTiers(professionPayload, downloadedTierIds);
        rootPayloads.set(`profession/${profession.id}`, trimmedProfessionPayload);
    }

    const recipeIds = [...sampledRecipeIds].sort((left, right) => left - right);
    if (recipeIds.length > 0) {
        const estimateMinutes = Math.max(1, Math.ceil((recipeIds.length * 0.35) / 60));
        const samplingNote = useFullDownload ? "full download" : `sample-size=${sampleSize} per tier`;
        progress?.log(
            `Fetching ${recipeIds.length} recipe(s) (${samplingNote}); linked resources follow` +
                (useFullDownload ? ` (often ~${estimateMinutes}+ min for large professions)...` : "..."),
        );
    }
    for (const [recipeIndex, recipeId] of recipeIds.entries()) {
        const recipeEndpointPath = `recipe/${recipeId}`;
        const recipePayload = await apiClient.fetchJson(recipeEndpointPath, {
            current: recipeIndex + 1,
            total: recipeIds.length,
        });
        rootPayloads.set(recipeEndpointPath, recipePayload);
    }

    const boundedRootEndpointPaths = new Set(
        [...rootPayloads.keys()].filter((endpointPath) => {
            const family = endpointPath.split("/")[0];
            return family === "profession" || family === "recipe";
        }),
    );

    progress?.log("Discovering linked resources (items, modified crafting, etc.)...");
    const { discoveredEndpointPaths, skippedEndpointPaths } = await buildRecursiveEndpointWrites({
        apiClient,
        baseResources: paths.baseResources,
        desiredFiles,
        progress,
        rootPayloads,
        shouldFollowEndpoint: (endpointPath) => {
            if (isExcludedEndpoint(endpointPath)) {
                return false;
            }
            const family = endpointPath.split("/")[0];
            if (family === "profession" || family === "recipe") {
                return boundedRootEndpointPaths.has(endpointPath);
            }
            return true;
        },
        writesByFile,
    });

    await addDerivedLocalFixtureWrites(writesByFile, desiredFiles, paths.baseResources, apiClient);
    addSupplementalTestFixtureWrites(writesByFile, desiredFiles, paths.baseResources);
    addManagedWrite(writesByFile, desiredFiles, paths.manifestFile, sampleManifest);

    const fullSelection = !args.professionIds;
    const managedRootDirs = [
        ...new Set(
            discoveredEndpointPaths.map((endpointPath) => path.join(paths.baseResources, endpointPath.split("/")[0])),
        ),
    ];

    const deletes = await planManagedFilePrunes({
        managedRoots: managedRootDirs,
        desiredFiles: [...desiredFiles],
        enablePrune: fullSelection,
    });

    const familyCounts = discoveredEndpointPaths.reduce((counts, endpointPath) => {
        const family = endpointPath.split("/")[0];
        counts[family] = (counts[family] ?? 0) + 1;
        return counts;
    }, {});

    const metadataOnlyProfessions = selectedProfessions.filter((profession) => profession.metadataOnly);

    return {
        deletes,
        meta: {
            discoveredEndpointPaths,
            fullSelection,
            metadataOnlyProfessions,
            sampledRecipeIds: [...sampledRecipeIds].sort((left, right) => left - right),
            selectedProfessions,
            skippedEndpointPaths,
        },
        summary: {
            families: familyCounts,
            metadataOnlyProfessions: metadataOnlyProfessions.length,
            professions: selectedProfessions.length,
            recipes: sampledRecipeIds.size,
            resources: discoveredEndpointPaths.length,
            skipped: skippedEndpointPaths.length,
            skillTiers: sampleManifest.length,
        },
        writes: [...writesByFile.values()],
    };
}
