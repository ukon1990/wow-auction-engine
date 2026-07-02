import { spawnSync } from "node:child_process";

/**
 * Run gh and return stdout. Throws on non-zero exit.
 * @param {string[]} args
 * @param {{ input?: string }} [opts]
 */
export function gh(args, opts = {}) {
  const result = spawnSync("gh", args, {
    encoding: "utf8",
    input: opts.input,
  });
  if (result.status !== 0) {
    throw new Error(
      result.stderr?.trim() || result.stdout?.trim() || "gh failed",
    );
  }
  return (result.stdout ?? "").trim();
}

/** @param {string[]} args @param {{ input?: string }} [opts] */
export function ghSpawn(args, opts = {}) {
  return spawnSync("gh", args, {
    encoding: "utf8",
    input: opts.input,
  });
}
