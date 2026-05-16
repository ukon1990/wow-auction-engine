import fs from "node:fs/promises";
import path from "node:path";

async function ensureDir(dirPath, dryRun) {
    if (dryRun) {
        return;
    }
    await fs.mkdir(dirPath, { recursive: true });
}

async function writeFixture(filePath, payload, dryRun, { raw = false } = {}) {
    const body = raw ? payload : `${JSON.stringify(payload, null, 4)}\n`;
    if (dryRun) {
        console.log(`[dry-run] write ${filePath}`);
        return;
    }
    await fs.writeFile(filePath, body, "utf8");
    console.log(`wrote ${filePath}`);
}

async function deleteFile(filePath, dryRun) {
    if (dryRun) {
        console.log(`[dry-run] delete ${filePath}`);
        return;
    }
    await fs.rm(filePath, { force: true });
    console.log(`deleted ${filePath}`);
}

export async function applyPlan(plan, { dryRun }) {
    const dirs = new Set(plan.writes.map((operation) => path.dirname(operation.filePath)));

    for (const dirPath of dirs) {
        await ensureDir(dirPath, dryRun);
    }

    for (const operation of plan.writes) {
        await writeFixture(operation.filePath, operation.payload, dryRun, { raw: operation.raw });
    }

    for (const operation of plan.deletes) {
        await deleteFile(operation.filePath, dryRun);
    }
}

export function formatCompletionSummary(plan) {
    const familySummary = Object.entries(plan.summary.families)
        .sort(([left], [right]) => left.localeCompare(right))
        .map(([family, count]) => `${family}=${count}`)
        .join(", ");

    const metadataOnlySummary =
        plan.summary.metadataOnlyProfessions > 0
            ? `, metadataOnlyProfessions=${plan.summary.metadataOnlyProfessions}`
            : "";

    const summary =
        `completed: professions=${plan.summary.professions}, tiers=${plan.summary.skillTiers}, ` +
        `recipes=${plan.summary.recipes}, resources=${plan.summary.resources}, skipped=${plan.summary.skipped}` +
        metadataOnlySummary;

    if (!plan.meta.fullSelection) {
        return `${summary}, prune=skipped-for-filtered-selection, families=[${familySummary}]`;
    }

    return `${summary}, families=[${familySummary}]`;
}
