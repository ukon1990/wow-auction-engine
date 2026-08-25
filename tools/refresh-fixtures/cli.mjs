import { blizzardConfig } from "./config.mjs";

function parseIdList(value) {
    return value
        .split(",")
        .map((entry) => parseInt(entry.trim(), 10))
        .filter((entry) => Number.isFinite(entry));
}

export function parseArgs(argv) {
    const args = {
        connectedRealmIds: null,
        dryRun: false,
        full: false,
        help: false,
        quiet: false,
        professionIds: null,
        skillTierIds: null,
        resource: "profession",
        sampleSize: null,
    };

    for (let index = 0; index < argv.length; index += 1) {
        const arg = argv[index];

        if (arg === "--dry-run") {
            args.dryRun = true;
        } else if (arg === "--help" || arg === "-h") {
            args.help = true;
        } else if (arg === "--quiet" || arg === "-q") {
            args.quiet = true;
        } else if (arg === "--full") {
            args.full = true;
        } else if (arg === "--profession-id") {
            const value = argv[index + 1];
            if (!value) {
                throw new Error("Missing value for --profession-id");
            }
            index += 1;
            args.professionIds = (args.professionIds ?? []).concat(parseIdList(value));
        } else if (arg === "--connected-realm-id") {
            const value = argv[index + 1];
            if (!value) {
                throw new Error("Missing value for --connected-realm-id");
            }
            index += 1;
            args.connectedRealmIds = (args.connectedRealmIds ?? []).concat(parseIdList(value));
        } else if (arg === "--skill-tier-id") {
            const value = argv[index + 1];
            if (!value) {
                throw new Error("Missing value for --skill-tier-id");
            }
            index += 1;
            args.skillTierIds = (args.skillTierIds ?? []).concat(parseIdList(value));
        } else if (arg === "--resource") {
            const value = argv[index + 1];
            if (!value) {
                throw new Error("Missing value for --resource");
            }
            index += 1;
            args.resource = value.trim();
        } else if (arg === "--sample-size") {
            const value = parseInt(argv[index + 1] ?? "", 10);
            if (!Number.isFinite(value) || value < 1) {
                throw new Error("--sample-size must be a positive integer");
            }
            index += 1;
            args.sampleSize = value;
        } else {
            throw new Error(`Unknown argument: ${arg}`);
        }
    }

    return args;
}

export function formatHelpText() {
    return [
        "Usage: node ./tools/refresh-fixtures.mjs [options]",
        "",
        "Options:",
        "  --dry-run                      Show planned writes/deletes without modifying files",
        "  --resource <name>              Resource definition to run (default: profession)",
        "  --profession-id <ids>          Refresh listed professions from Blizzard",
        "  --skill-tier-id <ids>          Limit to these skill tiers (requires --profession-id)",
        "  --connected-realm-id <ids>     Refresh listed connected realms (skips lowest-id sampling)",
        "  --sample-size <n>              Profession: max recipes per tier (default: " +
            `${blizzardConfig.samplePerTier}). Connected-realm: lowest N ids (default: ${blizzardConfig.connectedRealmSampleSize})`,
        "  --full                         Profession: all tiers/recipes. Connected-realm: entire regional index",
        "  --quiet, -q                    Suppress progress messages on stderr",
        "  --help, -h                     Show this help",
    ].join("\n");
}
