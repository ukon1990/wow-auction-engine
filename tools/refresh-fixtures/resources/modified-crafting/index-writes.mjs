import fs from "node:fs/promises";
import path from "node:path";
import { endpointPathToFixturePath } from "../../paths.mjs";
import { addManagedWrite } from "../../plan/writes.mjs";

async function readJsonFixture(filePath) {
    return JSON.parse(await fs.readFile(filePath, "utf8"));
}

function selfHref(payload) {
    return payload?._links?.self?.href;
}

function deriveIndexHrefFromDetail(payload, endpointPath) {
    const href = selfHref(payload);
    if (!href) {
        throw new Error(`Cannot derive ${endpointPath} index href from fixture without _links.self.href`);
    }

    const url = new URL(href);
    url.pathname = `/data/wow/${endpointPath}`;
    return url.toString();
}

async function readDetailPayloads(detailDir) {
    const entries = await fs.readdir(detailDir, { withFileTypes: true });
    const fixtures = await Promise.all(
        entries
            .filter(
                (entry) =>
                    entry.isFile() && entry.name.endsWith("-response.json") && entry.name !== "index-response.json",
            )
            .map(async (entry) => {
                const filePath = path.join(detailDir, entry.name);
                return {
                    filePath,
                    payload: await readJsonFixture(filePath),
                };
            }),
    );

    return fixtures
        .filter(({ payload }) => Number.isFinite(payload.id) && selfHref(payload))
        .sort((left, right) => left.payload.id - right.payload.id);
}

function filterIndexReferences(references, allowedIds) {
    const allowed = new Set(allowedIds);
    return (references ?? [])
        .filter((reference) => allowed.has(reference.id))
        .sort((left, right) => left.id - right.id);
}

// Live Blizzard category indexes sometimes include references with name: null.
function ensureCategoryIndexNullNameRegression(categories) {
    if (categories.length === 0) {
        return categories;
    }
    if (categories.some((category) => category.name === null)) {
        return categories;
    }
    return categories.map((category, index) =>
        index === 0
            ? {
                  ...category,
                  name: null,
              }
            : category,
    );
}

export async function addModifiedCraftingIndexWrites(writesByFile, desiredFiles, baseResources, apiClient) {
    const categoryFixtures = await readDetailPayloads(path.join(baseResources, "modified-crafting", "category"));
    const slotTypeFixtures = await readDetailPayloads(
        path.join(baseResources, "modified-crafting", "reagent-slot-type"),
    );
    if (categoryFixtures.length === 0 || slotTypeFixtures.length === 0) {
        throw new Error(
            "Cannot derive modified-crafting indexes without existing category and reagent slot type fixtures.",
        );
    }
    for (const fixture of categoryFixtures.concat(slotTypeFixtures)) {
        desiredFiles.add(fixture.filePath);
    }
    const categoryPayloads = categoryFixtures.map((fixture) => fixture.payload);
    const slotTypePayloads = slotTypeFixtures.map((fixture) => fixture.payload);
    const categoryIds = categoryPayloads.map((payload) => payload.id);
    const slotTypeIds = slotTypePayloads.map((payload) => payload.id);

    const [liveCategoryIndex, liveSlotTypeIndex] = await Promise.all([
        apiClient.fetchJson("modified-crafting/category/index"),
        apiClient.fetchJson("modified-crafting/reagent-slot-type/index"),
    ]);

    const filteredCategories = ensureCategoryIndexNullNameRegression(
        filterIndexReferences(liveCategoryIndex.categories, categoryIds),
    );
    const filteredSlotTypes = filterIndexReferences(liveSlotTypeIndex.slot_types, slotTypeIds);

    addManagedWrite(writesByFile, desiredFiles, endpointPathToFixturePath("modified-crafting/index", baseResources), {
        _links: {
            self: {
                href: deriveIndexHrefFromDetail(categoryPayloads[0], "modified-crafting/index"),
            },
        },
    });

    addManagedWrite(
        writesByFile,
        desiredFiles,
        endpointPathToFixturePath("modified-crafting/category/index", baseResources),
        {
            _links: liveCategoryIndex._links ?? {
                self: {
                    href: deriveIndexHrefFromDetail(categoryPayloads[0], "modified-crafting/category/index"),
                },
            },
            categories: filteredCategories,
        },
    );

    addManagedWrite(
        writesByFile,
        desiredFiles,
        endpointPathToFixturePath("modified-crafting/reagent-slot-type/index", baseResources),
        {
            _links: liveSlotTypeIndex._links ?? {
                self: {
                    href: deriveIndexHrefFromDetail(slotTypePayloads[0], "modified-crafting/reagent-slot-type/index"),
                },
            },
            slot_types: filteredSlotTypes,
        },
    );
}

export async function addDerivedLocalFixtureWrites(writesByFile, desiredFiles, baseResources, apiClient) {
    await addModifiedCraftingIndexWrites(writesByFile, desiredFiles, baseResources, apiClient);
}
