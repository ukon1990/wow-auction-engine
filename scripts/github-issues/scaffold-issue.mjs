#!/usr/bin/env node
/**
 * Scaffold a GitHub issue body with codebase touch points.
 *
 * Usage:
 *   node .../scaffold-issue.mjs recipes "Add recipe fork"
 *   node .../scaffold-issue.mjs --list
 *   node .../scaffold-issue.mjs --list-labels
 *   node .../scaffold-issue.mjs pantry --title "Backend: Pantry CRUD"
 *   node .../scaffold-issue.mjs pantry --parent 40 --label database --create --dry-run
 *   node .../scaffold-issue.mjs planner "Bug" --label bug,frontend --exact-labels
 */

import { buildIssueBody, getTopic, listTopics } from "./lib/topics.mjs";
import { createIssue, getIssue } from "./lib/gh-issues.mjs";
import {
  formatLabelWarning,
  labelsForTopic,
  listRepoLabels,
} from "./lib/labels.mjs";

function parseArgs(argv) {
  const flags = new Set();
  const values = {};
  const positional = [];

  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (
      a === "--list" ||
      a === "--list-labels" ||
      a === "--create" ||
      a === "--dry-run" ||
      a === "--json" ||
      a === "--exact-labels"
    ) {
      flags.add(a.slice(2));
      continue;
    }
    if (
      a === "--title" ||
      a === "--parent" ||
      a === "--blocked-by"
    ) {
      values[a.slice(2)] = argv[++i];
      continue;
    }
    if (a === "--label") {
      values.label = values.label ? `${values.label},${argv[++i]}` : argv[++i];
      continue;
    }
    positional.push(a);
  }

  return { flags, values, positional };
}

function parseNumbers(csv) {
  if (!csv) return [];
  return csv
    .split(",")
    .map((s) => Number(s.trim().replace(/^#/, "")))
    .filter((n) => Number.isFinite(n));
}

function usage() {
  console.error(`Usage:
  scaffold-issue.mjs <topic> [summary] [options]

Options:
  --list / --list-labels
  --title "feat: ..."
  --label <a,b>           Extra labels (validated against repo)
  --exact-labels          Apply only --label values; skip topic/default labels
  --parent <issue#>       Link as GitHub sub-issue of parent
  --blocked-by <#,#>      Dependency refs in issue body
  --create / --dry-run / --json
`);
  process.exit(1);
}

const { flags, values, positional } = parseArgs(process.argv.slice(2));

if (flags.has("list-labels")) {
  for (const name of listRepoLabels()) console.log(name);
  process.exit(0);
}

if (flags.has("list") || positional.length === 0) {
  if (!flags.has("list") && positional.length === 0) usage();
  for (const { id, summary } of listTopics()) {
    console.log(`${id.padEnd(16)} ${summary}`);
  }
  process.exit(0);
}

const topicId = positional[0];
const summary = positional.slice(1).join(" ") || getTopic(topicId).summary;
const title = values.title ?? `feat: ${summary}`;
const parentNumber = values.parent ? Number(values.parent) : undefined;
const blockedBy = parseNumbers(values["blocked-by"]);
const extraLabels = values.label
  ? values.label.split(",").map((s) => s.trim())
  : [];
const dryRun = flags.has("dry-run");
const exactLabels = flags.has("exact-labels");

let parentTitle;
if (parentNumber) {
  try {
    parentTitle = getIssue(parentNumber).title;
  } catch {
    console.error(`Parent issue #${parentNumber} not found`);
    process.exit(1);
  }
}

const topic = getTopic(topicId);
const { applied, unknown } = labelsForTopic(topic, extraLabels, {
  exact: exactLabels,
});
const labelWarning = formatLabelWarning({ applied, unknown });

const body = buildIssueBody({
  summary,
  topic,
  parentNumber,
  blockedBy,
  parentTitle,
  appliedLabels: applied,
});

const result = {
  title,
  topicId,
  parentNumber,
  blockedBy,
  labels: { applied, unknown },
  body,
};

if (flags.has("create")) {
  const created = createIssue({
    title,
    body,
    labels: [...applied, ...unknown],
    ensureLabels: exactLabels ? [] : undefined,
    parentNumber,
    dryRun,
  });
  result.created = created;

  if (dryRun) {
    console.log(
      `[dry-run] would create${parentNumber ? ` sub-issue of #${parentNumber}` : ""}: ${title}`,
    );
    console.log(`Labels: ${applied.join(", ") || "(none)"}`);
    if (labelWarning) console.warn(`Warning: ${labelWarning}`);
    if (blockedBy.length)
      console.log(`Blocked by: ${blockedBy.map((n) => `#${n}`).join(", ")}`);
    console.log("\n--- body ---\n");
    console.log(body);
    process.exit(0);
  }

  console.log(`Created #${created.number}: ${created.url}`);
  console.log(`Labels: ${created.labels.applied.join(", ")}`);
  if (created.labels.warning)
    console.warn(`Warning: ${created.labels.warning}`);
  if (created.parentLink?.attempted) {
    if (created.parentLink.linked) {
      console.log(`Linked as sub-issue of #${parentNumber}`);
    } else {
      console.warn(
        `Sub-issue link failed: ${created.parentLink.error ?? "unknown"}`,
      );
      console.warn(created.parentLink.fallback);
    }
  }
  process.exit(0);
}

if (flags.has("json")) {
  console.log(JSON.stringify(result, null, 2));
  process.exit(0);
}

console.log(`Title: ${title}`);
if (parentNumber) console.log(`Parent: #${parentNumber}`);
if (blockedBy.length)
  console.log(`Blocked by: ${blockedBy.map((n) => `#${n}`).join(", ")}`);
console.log(`Labels: ${applied.join(", ")}`);
if (labelWarning) console.warn(`Warning: ${labelWarning}`);
console.log(`\n${body}`);
