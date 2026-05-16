import fs from "node:fs/promises";
import path from "node:path";

function listJsonFilesRecursive(rootDir) {
    return fs
        .readdir(rootDir, { withFileTypes: true })
        .then((entries) =>
            Promise.all(
                entries.map(async (entry) => {
                    const fullPath = path.join(rootDir, entry.name);
                    if (entry.isDirectory()) {
                        return listJsonFilesRecursive(fullPath);
                    }
                    return entry.isFile() && entry.name.endsWith(".json") ? [fullPath] : [];
                }),
            ),
        )
        .then((results) => results.flat())
        .catch((error) => {
            if (error.code === "ENOENT") {
                return [];
            }
            throw error;
        });
}

export async function planManagedFilePrunes({ managedRoots, desiredFiles, enablePrune }) {
    if (!enablePrune) {
        return [];
    }

    const desiredSet = new Set(desiredFiles);
    const existingByRoot = await Promise.all(managedRoots.map((root) => listJsonFilesRecursive(root)));

    return existingByRoot
        .flat()
        .filter((filePath) => !desiredSet.has(filePath))
        .sort()
        .map((filePath) => ({ filePath, kind: "delete" }));
}
