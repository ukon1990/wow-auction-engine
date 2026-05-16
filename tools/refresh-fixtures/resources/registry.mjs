import { buildProfessionFixturePlan } from "./profession/plan.mjs";

function createProfessionResource() {
    return {
        name: "profession",
        buildPlan: buildProfessionFixturePlan,
    };
}

const definitions = {
    profession: createProfessionResource(),
    // item: createItemResource(),
};

export function getResourceDefinition(name) {
    return definitions[name] ?? null;
}

export function listResourceNames() {
    return Object.keys(definitions);
}

export function getResourceDefinitions() {
    return { ...definitions };
}
