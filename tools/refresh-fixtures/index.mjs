import { createApiClient } from "./api/blizzard-client.mjs";
import { parseArgs, formatHelpText } from "./cli.mjs";
import { buildPaths } from "./paths.mjs";
import { applyPlan, formatCompletionSummary } from "./plan/apply.mjs";
import { createProgressReporter } from "./progress.mjs";
import { getResourceDefinition, getResourceDefinitions } from "./resources/registry.mjs";

export { parseArgs, formatHelpText } from "./cli.mjs";
export { buildPaths, endpointPathToFixturePath } from "./paths.mjs";
export { createApiClient } from "./api/blizzard-client.mjs";
export { collectLinkedEndpointPaths, normalizeEndpointPath, isExcludedEndpoint } from "./discovery/endpoint-paths.mjs";
export { buildRecursiveEndpointWrites } from "./discovery/traversal.mjs";
export { pickDefaultSkillTierIds, pickAllSkillTierIds, trimProfessionToSkillTiers } from "./sampling/skill-tiers.mjs";
export { pickRecipeIds, pickAllRecipeIds, trimSkillTierToRecipes } from "./sampling/recipes.mjs";
export { planManagedFilePrunes } from "./plan/prune.mjs";
export { applyPlan, formatCompletionSummary } from "./plan/apply.mjs";
export { resolveSelectedProfessions } from "./resources/profession/selection.mjs";
export { buildProfessionFixturePlan } from "./resources/profession/plan.mjs";
export { getResourceDefinition, listResourceNames, getResourceDefinitions } from "./resources/registry.mjs";
export { ROOT_SELECTIONS, blizzardConfig } from "./config.mjs";

export async function buildRefreshPlan({
    repoRoot = process.cwd(),
    args,
    apiClient = createApiClient(),
    definitions = getResourceDefinitions(),
} = {}) {
    const definition = definitions[args.resource];
    if (!definition) {
        throw new Error(`Unknown resource: ${args.resource}`);
    }

    const paths = buildPaths(repoRoot);
    return definition.buildPlan({
        apiClient,
        args,
        paths,
    });
}

export async function runCli(argv, { repoRoot = process.cwd(), apiClient } = {}) {
    const args = parseArgs(argv);
    if (args.help) {
        console.log(formatHelpText());
        return;
    }

    const progress = createProgressReporter({ enabled: !args.quiet });
    const client =
        apiClient ??
        createApiClient({
            onRequest: (endpointPath, requestMeta) => progress.logRequest(endpointPath, requestMeta),
        });

    progress.log("Building refresh plan (fetching from Blizzard)...");
    const plan = await buildRefreshPlan({
        apiClient: client,
        args: { ...args, progress },
        repoRoot,
    });

    progress.log(`Applying ${plan.writes.length} write(s) and ${plan.deletes.length} delete(s)...`);
    await applyPlan(plan, { dryRun: args.dryRun });
    console.log(formatCompletionSummary(plan));
}
