#!/usr/bin/env node

import { fileURLToPath } from 'node:url';
import path from "node:path";
import { runCli } from "./refresh-fixtures/index.mjs";

export {
    parseArgs,
    formatHelpText,
    pickDefaultSkillTierIds,
    pickAllSkillTierIds,
    trimProfessionToSkillTiers,
    pickRecipeIds,
    pickAllRecipeIds,
    trimSkillTierToRecipes,
    normalizeEndpointPath,
    endpointPathToFixturePath,
    collectLinkedEndpointPaths,
    planManagedFilePrunes,
    resolveSelectedProfessions,
    buildProfessionFixturePlan,
    buildRefreshPlan,
    applyPlan,
} from "./refresh-fixtures/index.mjs";

export { runCli };

const isDirectExecution = process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1]);

if (isDirectExecution) {
  runCli(process.argv.slice(2)).catch((error) => {
    console.error(error.message);
    process.exit(1);
  });
}
