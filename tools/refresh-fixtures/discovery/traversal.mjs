import { collectLinkedEndpointPaths } from "./endpoint-paths.mjs";
import { endpointPathToFixturePath } from "../paths.mjs";
import { addManagedWrite } from "../plan/writes.mjs";

function addPayloadToTraversal(traversalState, endpointPath, payload) {
    if (traversalState.payloads.has(endpointPath)) {
        return;
    }
    traversalState.payloads.set(endpointPath, payload);
    traversalState.queue.push(endpointPath);
}

export async function buildRecursiveEndpointWrites({
    apiClient,
    baseResources,
    rootPayloads,
    shouldFollowEndpoint,
    writesByFile,
    desiredFiles,
    progress,
}) {
    const traversalState = {
        payloads: new Map(rootPayloads),
        queue: [...rootPayloads.keys()],
        seen: new Set(),
    };
    const discoveredEndpointPaths = new Set(rootPayloads.keys());
    const skippedEndpointPaths = new Set();
    let processedCount = 0;

    while (traversalState.queue.length > 0) {
        const endpointPath = traversalState.queue.shift();
        if (traversalState.seen.has(endpointPath)) {
            continue;
        }
        traversalState.seen.add(endpointPath);
        processedCount += 1;

        let payload = traversalState.payloads.get(endpointPath);
        if (!payload) {
            payload = await apiClient.fetchJson(endpointPath, {
                current: processedCount,
                total: traversalState.seen.size + traversalState.queue.length,
            });
            traversalState.payloads.set(endpointPath, payload);
        } else if (progress && processedCount % 100 === 0) {
            progress.log(`Traversed ${processedCount} endpoint(s), queue ${traversalState.queue.length}...`);
        }

        addManagedWrite(writesByFile, desiredFiles, endpointPathToFixturePath(endpointPath, baseResources), payload);

        for (const linkedEndpointPath of collectLinkedEndpointPaths(payload)) {
            if (!shouldFollowEndpoint(linkedEndpointPath)) {
                continue;
            }
            if (traversalState.seen.has(linkedEndpointPath) || traversalState.payloads.has(linkedEndpointPath)) {
                continue;
            }
            try {
                const linkedPayload = await apiClient.fetchJson(linkedEndpointPath);
                addPayloadToTraversal(traversalState, linkedEndpointPath, linkedPayload);
                discoveredEndpointPaths.add(linkedEndpointPath);
            } catch {
                skippedEndpointPaths.add(linkedEndpointPath);
            }
        }
    }

    return {
        discoveredEndpointPaths: [...discoveredEndpointPaths].sort(),
        skippedEndpointPaths: [...skippedEndpointPaths].sort(),
    };
}
