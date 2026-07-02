import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/** `scripts/github-issues/` */
export const TOOLKIT_DIR = path.resolve(__dirname, "..");

/** Repo root */
export const REPO_ROOT = path.resolve(TOOLKIT_DIR, "../..");

export const PLANNING_DIR = path.join(TOOLKIT_DIR, "planning");

export const AUDIT_DIR = path.join(TOOLKIT_DIR, "audit");
