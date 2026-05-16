import { supplementalTestFixtures } from "../resources/supplemental.mjs";
import { endpointPathToFixturePath } from "../paths.mjs";

export function describeWriteOperation(filePath, payload, { raw = false } = {}) {
    return { filePath, kind: "write", payload, raw };
}

export function addManagedWrite(writesByFile, desiredFiles, filePath, payload, options = {}) {
    writesByFile.set(filePath, describeWriteOperation(filePath, payload, options));
    desiredFiles.add(filePath);
}

export function addSupplementalTestFixtureWrites(writesByFile, desiredFiles, baseResources) {
    for (const fixture of supplementalTestFixtures) {
        addManagedWrite(
            writesByFile,
            desiredFiles,
            endpointPathToFixturePath(fixture.endpointPath, baseResources),
            fixture.payload,
            { raw: fixture.raw ?? false },
        );
    }
}
