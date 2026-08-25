import { endpointPathToFixturePath } from "../../paths.mjs";
import { addManagedWrite } from "../../plan/writes.mjs";
import { filterConnectedRealmIndex } from "./selection.mjs";

/**
 * Write a filtered connected-realm index that only references the selected detail fixtures.
 */
export function addConnectedRealmIndexWrites(writesByFile, desiredFiles, baseResources, indexPayload, selectedIds) {
    addManagedWrite(
        writesByFile,
        desiredFiles,
        endpointPathToFixturePath("connected-realm/index", baseResources),
        filterConnectedRealmIndex(indexPayload, selectedIds),
    );
}
