#!/usr/bin/env node
/**
 * Fetch a GitHub issue with parent, sub-issues, and body dependency refs.
 *
 * Usage:
 *   node scripts/github-issues/view-issue.mjs 75
 *   node scripts/github-issues/view-issue.mjs 76 --json
 *   node scripts/github-issues/view-issue.mjs 75 --no-body
 */

import { getIssueWithRelations } from "./lib/gh-issues.mjs";

function parseArgs(argv) {
  const flags = new Set();
  const positional = [];
  for (const a of argv) {
    if (a === "--json") flags.add("json");
    else if (a === "--no-body") flags.add("no-body");
    else if (!a.startsWith("-")) positional.push(a);
  }
  return { flags, positional };
}

function usage() {
  console.error(`Usage: view-issue.mjs <issue#> [--json] [--no-body]`);
  process.exit(1);
}

const { flags, positional } = parseArgs(process.argv.slice(2));
if (!positional.length) usage();

const issueNumber = Number(positional[0].replace(/^#/, ""));
if (!Number.isFinite(issueNumber)) usage();

const issue = getIssueWithRelations(issueNumber, {
  includeBody: !flags.has("no-body"),
});

if (flags.has("json")) {
  console.log(JSON.stringify(issue, null, 2));
  process.exit(0);
}

console.log(`#${issue.number} — ${issue.title} (${issue.state})`);
console.log(issue.url);
if (issue.suggestedBranch) console.log(`Branch: ${issue.suggestedBranch}`);
if (issue.labels?.length) console.log(`Labels: ${issue.labels.join(", ")}`);

if (issue.parent) {
  console.log(
    `\nParent: #${issue.parent.number} — ${issue.parent.title ?? "(unknown)"}`,
  );
}

if (issue.subIssues.length) {
  console.log(`\nSub-issues (${issue.subIssues.length}):`);
  for (const s of issue.subIssues) {
    console.log(`  #${s.number} [${s.state}] ${s.title}`);
  }
} else if (issue.dependencies.bodySubIssueNumbers.length) {
  console.log(
    `\nSub-issues (body refs only, not linked via API): ${issue.dependencies.bodySubIssueNumbers.map((n) => `#${n}`).join(", ")}`,
  );
}

if (issue.dependencies.blockedBy.length) {
  console.log(
    `\nBlocked by: ${issue.dependencies.blockedBy.map((n) => `#${n}`).join(", ")}`,
  );
}

if (issue.body) {
  console.log(`\n--- body ---\n${issue.body}`);
}
