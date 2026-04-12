#!/usr/bin/env node

import fs from "node:fs/promises";
import path from "node:path";

const BASE_URL = process.env.BLIZZARD_BASE_URL ?? "https://us.api.blizzard.com/data/wow";
const TOKEN_URL = process.env.BLIZZARD_TOKEN_URL ?? "https://eu.battle.net/oauth/token";
const NAMESPACE = process.env.BLIZZARD_NAMESPACE ?? "static-us";
const SAMPLE_PER_TIER = parseInt(process.env.PROFESSION_FIXTURE_SAMPLE_SIZE ?? "6", 10);

const TARGET_PROFESSIONS = [
    { id: 164, name: "Blacksmithing", tiers: [2907, 2751] },
    { id: 333, name: "Enchanting", tiers: [2909, 2753] },
    { id: 182, name: "Herbalism", tiers: [2912, 2550] },
    { id: 356, name: "Fishing", tiers: [2911, 2826] },
];

function parseArgs(argv) {
    const args = {
        dryRun: false,
        professionIds: null,
        sampleSize: SAMPLE_PER_TIER,
    };

    for (let i = 0; i < argv.length; i += 1) {
        const arg = argv[i];
        if (arg === "--dry-run") {
            args.dryRun = true;
        } else if (arg === "--profession-id") {
            const value = argv[i + 1];
            if (!value) {
                throw new Error("Missing value for --profession-id");
            }
            i += 1;
            args.professionIds = (args.professionIds ?? []).concat(
                value
                    .split(",")
                    .map((v) => parseInt(v.trim(), 10))
                    .filter((v) => Number.isFinite(v)),
            );
        } else if (arg === "--sample-size") {
            const value = parseInt(argv[i + 1] ?? "", 10);
            if (!Number.isFinite(value) || value < 5 || value > 10) {
                throw new Error("--sample-size must be an integer in range 5..10");
            }
            i += 1;
            args.sampleSize = value;
        } else {
            throw new Error(`Unknown argument: ${arg}`);
        }
    }

    return args;
}

async function ensureDir(dir, dryRun) {
    if (dryRun) {
        return;
    }
    await fs.mkdir(dir, { recursive: true });
}

async function writeJson(filePath, payload, dryRun) {
    const body = `${JSON.stringify(payload, null, 4)}\n`;
    if (dryRun) {
        console.log(`[dry-run] write ${filePath}`);
        return;
    }
    await fs.writeFile(filePath, body, "utf8");
    console.log(`wrote ${filePath}`);
}

async function fetchAccessToken() {
    const directToken = process.env.BLIZZARD_ACCESS_TOKEN;
    if (directToken) {
        return directToken;
    }

    const clientId = process.env.BLIZZARD_CLIENT_ID;
    const clientSecret = process.env.BLIZZARD_CLIENT_SECRET;
    if (!clientId || !clientSecret) {
        throw new Error(
            "Missing Blizzard credentials. Set BLIZZARD_ACCESS_TOKEN or BLIZZARD_CLIENT_ID + BLIZZARD_CLIENT_SECRET.",
        );
    }

    const body = new URLSearchParams({
        grant_type: "client_credentials",
        client_id: clientId,
        client_secret: clientSecret,
    });

    const response = await fetch(TOKEN_URL, {
        method: "POST",
        headers: {
            "Content-Type": "application/x-www-form-urlencoded",
        },
        body,
    });
    if (!response.ok) {
        const text = await response.text();
        throw new Error(`Failed to refresh token (${response.status}): ${text}`);
    }

    const payload = await response.json();
    if (!payload.access_token) {
        throw new Error("Token response did not include access_token");
    }
    return payload.access_token;
}

async function getJson(endpointPath, token) {
    const url = new URL(`${BASE_URL}/${endpointPath.replace(/^\//, "")}`);
    url.searchParams.set("namespace", NAMESPACE);

    const response = await fetch(url, {
        headers: {
            Authorization: `Bearer ${token}`,
        },
    });
    if (!response.ok) {
        const text = await response.text();
        throw new Error(`GET ${url} failed (${response.status}): ${text}`);
    }

    return response.json();
}

function pickRecipeIds(skillTierPayload, sampleSize) {
    const categories = (skillTierPayload.categories ?? [])
        .map((category) => ({
            categoryName: category.name ?? "unknown",
            recipes: category.recipes ?? [],
        }))
        .filter((category) => category.recipes.length > 0);

    if (categories.length === 0) {
        return [];
    }

    const selected = [];
    const used = new Set();
    let index = 0;
    while (selected.length < sampleSize) {
        let addedInRound = false;
        for (const category of categories) {
            if (index >= category.recipes.length) {
                continue;
            }
            const recipe = category.recipes[index];
            if (!recipe || used.has(recipe.id)) {
                continue;
            }
            selected.push({
                id: recipe.id,
                name: recipe.name ?? "unknown",
                category: category.categoryName,
            });
            used.add(recipe.id);
            addedInRound = true;
            if (selected.length >= sampleSize) {
                break;
            }
        }
        if (!addedInRound) {
            break;
        }
        index += 1;
    }

    return selected;
}

function trimSkillTierToRecipes(skillTierPayload, selectedRecipeIds) {
    const selectedSet = new Set(selectedRecipeIds);
    const categories = (skillTierPayload.categories ?? [])
        .map((category) => {
            const recipes = (category.recipes ?? []).filter((recipe) => selectedSet.has(recipe.id));
            return {
                ...category,
                recipes,
            };
        })
        .filter((category) => (category.recipes ?? []).length > 0);

    return {
        ...skillTierPayload,
        categories,
    };
}

async function main() {
    const args = parseArgs(process.argv.slice(2));
    const selectedProfessions = TARGET_PROFESSIONS.filter(
        (profession) => !args.professionIds || args.professionIds.includes(profession.id),
    );
    if (selectedProfessions.length === 0) {
        throw new Error("No professions selected.");
    }

    const repoRoot = process.cwd();
    const baseResources = path.join(repoRoot, "src/test/resources/blizzard");
    const professionRoot = path.join(baseResources, "profession");
    const professionDetailsDir = path.join(professionRoot, "details");
    const skillTierDir = path.join(professionRoot, "skill-tier");
    const recipeRoot = path.join(baseResources, "recipe");
    const recipeDetailsDir = path.join(recipeRoot, "details");

    await ensureDir(professionRoot, args.dryRun);
    await ensureDir(professionDetailsDir, args.dryRun);
    await ensureDir(skillTierDir, args.dryRun);
    await ensureDir(recipeRoot, args.dryRun);
    await ensureDir(recipeDetailsDir, args.dryRun);

    const token = await fetchAccessToken();

    const professionIndex = await getJson("profession/index", token);
    const allowedIds = new Set(selectedProfessions.map((p) => p.id));
    const filteredIndex = {
        ...professionIndex,
        professions: (professionIndex.professions ?? []).filter((p) => allowedIds.has(p.id)),
    };
    await writeJson(path.join(professionRoot, "index-response.json"), filteredIndex, args.dryRun);

    const recipeIds = new Set();
    const sampleManifest = [];

    for (const profession of selectedProfessions) {
        const professionPayload = await getJson(`profession/${profession.id}`, token);
        await writeJson(
            path.join(professionDetailsDir, `${profession.id}-response.json`),
            professionPayload,
            args.dryRun,
        );

        for (const tierId of profession.tiers) {
            const tierPayload = await getJson(`profession/${profession.id}/skill-tier/${tierId}`, token);

            const chosenRecipes = pickRecipeIds(tierPayload, args.sampleSize);
            const trimmedTierPayload = trimSkillTierToRecipes(
                tierPayload,
                chosenRecipes.map((recipe) => recipe.id),
            );
            await writeJson(path.join(skillTierDir, `${tierId}-response.json`), trimmedTierPayload, args.dryRun);

            sampleManifest.push({
                professionId: profession.id,
                professionName: professionPayload.name ?? "unknown",
                skillTierId: tierId,
                skillTierName: tierPayload.name ?? "unknown",
                recipes: chosenRecipes,
            });
            for (const recipe of chosenRecipes) {
                recipeIds.add(recipe.id);
            }
        }
    }

    const orderedRecipeIds = [...recipeIds].sort((a, b) => a - b);
    for (const recipeId of orderedRecipeIds) {
        const recipePayload = await getJson(`recipe/${recipeId}`, token);
        await writeJson(path.join(recipeDetailsDir, `${recipeId}-response.json`), recipePayload, args.dryRun);
    }

    await writeJson(path.join(baseResources, "profession-recipe-sample-manifest.json"), sampleManifest, args.dryRun);

    console.log(
        `completed: professions=${selectedProfessions.length}, tiers=${sampleManifest.length}, recipes=${orderedRecipeIds.length}`,
    );
}

main().catch((error) => {
    console.error(error.message);
    process.exit(1);
});
