#!/usr/bin/env node
/**
 * Split a feature into dependent sub-issues under a parent GitHub issue.
 *
 * Usage:
 *   node .../split-feature.mjs --list-templates
 *   node .../split-feature.mjs --topic pantry --feature "pantry items" --parent 40
 *   node .../split-feature.mjs --topic pantry --parent 40 --create --dry-run
 */

import {
  buildIssueBody,
  buildSplitPlan,
  getTopic,
  listSplitTemplates,
  renderParentTrackingBody,
} from "./lib/topics.mjs";
import {
  createIssue,
  getIssue,
  updateIssueBody,
  addIssueLabels,
} from "./lib/gh-issues.mjs";
import {
  formatLabelWarning,
  labelsForTopic,
  loadLabelConfig,
} from "./lib/labels.mjs";

function parseArgs(argv) {
  const flags = new Set();
  const values = {};
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a.startsWith("--")) {
      const key = a.slice(2);
      if (
        key === "list-templates" ||
        key === "create" ||
        key === "dry-run" ||
        key === "json" ||
        key === "no-update-parent"
      ) {
        flags.add(key);
      } else {
        values[key] = argv[++i];
      }
    }
  }
  return { flags, values };
}

function usage() {
  console.error(`Usage:
  split-feature.mjs --topic <id> --parent <issue#> [options]

Required:
  --topic <id>           Primary domain (pantry, recipes, mfa, …)
  --parent <issue#>      Parent epic/feature issue

Options:
  --feature "name"       Short name for titles (default: topic summary)
  --template <id>        Split template (default: full-stack)
  --label <a,b>          Extra labels applied to every sub-issue
  --list-templates
  --create / --dry-run / --json
  --no-update-parent     Skip appending Sub-issues checklist to parent
`);
  process.exit(1);
}

const { flags, values } = parseArgs(process.argv.slice(2));

if (flags.has("list-templates")) {
  for (const t of listSplitTemplates()) {
    console.log(`${t.id.padEnd(16)} ${t.description} (${t.steps} steps)`);
  }
  process.exit(0);
}

if (!values.topic || !values.parent) usage();

const topicId = values.topic;
const parentNumber = Number(values.parent);
const templateId = values.template ?? "full-stack";
const featureName = values.feature;
const extraLabels = values.label
  ? values.label.split(",").map((s) => s.trim())
  : [];
const dryRun = flags.has("dry-run");
const shouldCreate = flags.has("create");

getTopic(topicId);

let parent;
try {
  parent = getIssue(parentNumber);
} catch {
  console.error(`Parent issue #${parentNumber} not found`);
  process.exit(1);
}

const plan = buildSplitPlan(templateId, { topicId, featureName });

/** @type {Map<string, number>} */
const keyToNumber = new Map();
/** @type {Array<object>} */
const created = [];

for (const step of plan) {
  const blockedByNumbers = step.blockedByKeys
    .map((k) => keyToNumber.get(k))
    .filter((n) => n != null);

  const mergedLabels = [...step.labels, ...extraLabels];
  const body = buildIssueBody({
    summary: step.summary,
    topic: step.topic,
    parentNumber,
    blockedBy: blockedByNumbers,
    parentTitle: parent.title,
    appliedLabels: mergedLabels,
  });

  const labelWarning = formatLabelWarning({
    applied: mergedLabels,
    unknown: step.labelUnknown ?? [],
  });

  const item = {
    key: step.key,
    title: step.title,
    topicId: step.topicId,
    labels: mergedLabels,
    labelUnknown: step.labelUnknown,
    blockedByKeys: step.blockedByKeys,
    blockedByNumbers,
    body,
    number: null,
  };

  if (shouldCreate) {
    const result = createIssue({
      title: step.title,
      body,
      labels: [...mergedLabels, ...(step.labelUnknown ?? [])],
      parentNumber,
      dryRun,
    });

    item.number = result.number;
    if (!dryRun && result.number) {
      keyToNumber.set(step.key, result.number);
    }

    created.push({
      key: step.key,
      number: result.number,
      title: step.title,
      blockedByNumbers,
      url: result.url,
      labels: result.labels?.applied ?? mergedLabels,
      parentLink: result.parentLink,
    });

    if (dryRun) {
      const depLabel =
        step.blockedByKeys.length === 0
          ? "(none)"
          : step.blockedByKeys.join(", ");
      console.log(
        `[dry-run] ${step.key}: ${step.title}\n  blocked by step(s): ${depLabel}\n  labels: ${mergedLabels.join(", ")}`,
      );
      if (labelWarning) console.warn(`  warning: ${labelWarning}`);
    } else {
      console.log(`Created #${result.number} [${step.key}]: ${result.url}`);
      console.log(`  labels: ${result.labels.applied.join(", ")}`);
      if (result.labels.warning)
        console.warn(`  warning: ${result.labels.warning}`);
      if (result.parentLink?.attempted && !result.parentLink.linked) {
        console.warn(
          `  sub-issue link failed; parent #${parentNumber} noted in body`,
        );
      }
    }
  } else {
    created.push(item);
  }
}

if (
  shouldCreate &&
  !dryRun &&
  !flags.has("no-update-parent") &&
  created.length
) {
  const newBody = renderParentTrackingBody(parent.body ?? "", created);
  updateIssueBody(parentNumber, newBody);

  const parentLabels = labelsForTopic(
    { labels: loadLabelConfig().parentEpic ?? ["enhancement"] },
    [],
  );
  const parentLabelResult = addIssueLabels(parentNumber, parentLabels.applied);
  console.log(`Updated parent #${parentNumber} with sub-issue checklist`);
  if (parentLabelResult.applied?.length) {
    console.log(`Parent labels: ${parentLabelResult.applied.join(", ")}`);
  }
}

if (flags.has("json")) {
  console.log(
    JSON.stringify(
      {
        parent: { number: parentNumber, title: parent.title },
        template: templateId,
        topic: topicId,
        steps: created,
      },
      null,
      2,
    ),
  );
  process.exit(0);
}

if (!shouldCreate) {
  console.log(
    `Split plan: ${templateId} → ${plan.length} sub-issues under #${parentNumber} (${parent.title})\n`,
  );
  for (const step of plan) {
    const deps =
      step.blockedByKeys.length === 0
        ? "no deps"
        : `after: ${step.blockedByKeys.join(", ")}`;
    const labelWarn = step.labelUnknown?.length
      ? ` | unknown labels skipped: ${step.labelUnknown.join(",")}`
      : "";
    console.log(`  [${step.key}] ${step.title}`);
    console.log(
      `         topic=${step.topicId} | ${deps} | labels=${step.labels.join(",")}${labelWarn}`,
    );
  }
  console.log(
    "\nAdd --create to file issues, or --create --dry-run to preview.",
  );
}
