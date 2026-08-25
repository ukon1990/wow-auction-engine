import path from "node:path";
import { ROOT_SELECTIONS } from "../../config.mjs";
import { endpointPathToFixturePath } from "../../paths.mjs";
import { planManagedFilePrunes } from "../../plan/prune.mjs";
import { addManagedWrite } from "../../plan/writes.mjs";
import { addConnectedRealmIndexWrites } from "./index-writes.mjs";
import { pickConnectedRealmIds } from "./selection.mjs";

export async function buildConnectedRealmFixturePlan({
    apiClient,
    args,
    paths,
    selectionConfig = ROOT_SELECTIONS.connectedRealm,
}) {
    const progress = args.progress;
    const sampleSize = args.sampleSize ?? selectionConfig.sampleSize;
    const fetchOptions = {
        baseUrl: selectionConfig.baseUrl,
        namespace: selectionConfig.namespace,
    };

    progress?.log(
        `Fetching connected-realm index (${selectionConfig.namespace} via ${selectionConfig.baseUrl})...`,
    );
    const indexPayload = await apiClient.fetchJson("connected-realm/index", undefined, fetchOptions);

    const selectedIds = pickConnectedRealmIds(indexPayload, {
        sampleSize,
        connectedRealmIds: args.connectedRealmIds,
        full: Boolean(args.full),
    });

    const selectionNote = args.connectedRealmIds?.length
        ? `explicit ids (${selectedIds.length})`
        : args.full
          ? `full index (${selectedIds.length})`
          : `lowest ${selectedIds.length} id(s)`;
    progress?.log(`Selected ${selectionNote}: ${selectedIds.join(", ")}`);

    const writesByFile = new Map();
    const desiredFiles = new Set();

    for (const [index, connectedRealmId] of selectedIds.entries()) {
        const endpointPath = `connected-realm/${connectedRealmId}`;
        progress?.log(
            `Fetching connected realm ${connectedRealmId} (${index + 1}/${selectedIds.length})...`,
        );
        const detailPayload = await apiClient.fetchJson(
            endpointPath,
            { current: index + 1, total: selectedIds.length },
            fetchOptions,
        );
        addManagedWrite(
            writesByFile,
            desiredFiles,
            endpointPathToFixturePath(endpointPath, paths.baseResources),
            detailPayload,
        );
    }

    addConnectedRealmIndexWrites(writesByFile, desiredFiles, paths.baseResources, indexPayload, selectedIds);

    const managedRoot = path.join(paths.baseResources, "connected-realm");
    const fullSelection = !args.connectedRealmIds?.length;
    const deletes = await planManagedFilePrunes({
        managedRoots: [managedRoot],
        desiredFiles: [...desiredFiles],
        enablePrune: fullSelection,
    });

    return {
        deletes,
        meta: {
            discoveredEndpointPaths: [
                "connected-realm/index",
                ...selectedIds.map((id) => `connected-realm/${id}`),
            ],
            fullSelection,
            selectedConnectedRealmIds: selectedIds,
            skippedEndpointPaths: [],
        },
        summary: {
            connectedRealms: selectedIds.length,
            families: { "connected-realm": selectedIds.length + 1 },
            metadataOnlyProfessions: 0,
            professions: 0,
            recipes: 0,
            resources: selectedIds.length + 1,
            skipped: 0,
            skillTiers: 0,
        },
        writes: [...writesByFile.values()],
    };
}
