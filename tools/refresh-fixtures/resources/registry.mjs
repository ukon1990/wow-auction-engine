import { buildProfessionFixturePlan } from "./profession/plan.mjs";
import { buildConnectedRealmFixturePlan } from "./connected-realm/plan.mjs";

function createProfessionResource() {
    return {
        name: "profession",
        buildPlan: buildProfessionFixturePlan,
    };
}

function createConnectedRealmResource() {
    return {
        name: "connected-realm",
        buildPlan: buildConnectedRealmFixturePlan,
    };
}

const definitions = {
    profession: createProfessionResource(),
    "connected-realm": createConnectedRealmResource(),
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
