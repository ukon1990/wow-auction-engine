import { existsSync } from "node:fs";
import path from "node:path";

export function buildPaths(repoRoot) {
    const baseResources = existsSync(path.join(repoRoot, "backend", "pom.xml"))
        ? path.join(repoRoot, "backend", "src/test/resources/blizzard")
        : path.join(repoRoot, "src/test/resources/blizzard");

    return {
        baseResources,
        manifestFile: path.join(baseResources, "profession-recipe-sample-manifest.json"),
    };
}

export function endpointPathToFixturePath(endpointPath, baseResources) {
    const parts = endpointPath.split("/").filter(Boolean);
    if (parts.length === 0) {
        throw new Error(`Cannot map endpoint path: ${endpointPath}`);
    }
    const fileName = `${parts.at(-1)}-response.json`;
    return path.join(baseResources, ...parts.slice(0, -1), fileName);
}
