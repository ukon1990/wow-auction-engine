import fs from "node:fs";
import path from "node:path";
import { gh } from "./gh.mjs";
import { PLANNING_DIR } from "./paths.mjs";

let repoLabelsCache = null;

export function loadLabelConfig() {
  return JSON.parse(
    fs.readFileSync(path.join(PLANNING_DIR, "labels.json"), "utf8"),
  );
}

/** @returns {Set<string>} */
export function getRepoLabels(refresh = false) {
  if (!repoLabelsCache || refresh) {
    const raw = gh(["label", "list", "--limit", "200", "--json", "name"]);
    repoLabelsCache = new Set(JSON.parse(raw).map((l) => l.name));
  }
  return repoLabelsCache;
}

function unique(list) {
  return [...new Set(list.filter(Boolean))];
}

function withoutDenied(labels, deny) {
  return labels.filter((l) => !deny.includes(l));
}

/** @param {string[]} candidates @param {{ ensure?: string[] }} [opts] */
export function resolveLabels(candidates, opts = {}) {
  const config = loadLabelConfig();
  const ensure = opts.ensure ?? config.defaultFeature ?? ["enhancement"];
  const deny = config.deny ?? [];
  const repo = getRepoLabels();

  const merged = unique([...withoutDenied(candidates, deny), ...ensure]);
  const applied = [];
  const unknown = [];

  for (const label of merged) {
    if (repo.has(label)) applied.push(label);
    else unknown.push(label);
  }

  return { applied, unknown };
}

export function labelsForTopic(topic, extra = [], opts = {}) {
  const config = loadLabelConfig();
  if (opts.exact) {
    return resolveLabels(extra, { ensure: [] });
  }
  return resolveLabels([
    ...(topic.labels ?? []),
    ...extra,
    ...(config.defaultFeature ?? []),
  ]);
}

export function labelsForSplitStep(step) {
  const config = loadLabelConfig();
  const stepDefaults = config.splitStep?.[step.key] ?? [];
  const explicit = step.labels ?? [];
  const allow = new Set(config.topicLabelAllow?.[step.key] ?? []);
  const topicLabels = (step.topic?.labels ?? []).filter(
    (l) => allow.size === 0 || allow.has(l),
  );

  return resolveLabels([
    ...stepDefaults,
    ...explicit,
    ...topicLabels,
    ...(config.defaultFeature ?? []),
  ]);
}

export function formatLabelWarning({ applied, unknown }) {
  if (!unknown.length) return null;
  return `Skipped unknown labels (not on repo): ${unknown.join(", ")}. Applied: ${applied.join(", ") || "(none)"}`;
}

export function listRepoLabels() {
  return [...getRepoLabels()].sort();
}
