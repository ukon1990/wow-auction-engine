#!/usr/bin/env node
/**
 * Audit GitHub issues against the wow-auction-engine codebase.
 * Canonical location: scripts/github-issues/audit.mjs
 */

import { execFileSync } from "node:child_process";
import fs from "node:fs";
import { globSync } from "node:fs";
import path from "node:path";
import { gh } from "./lib/gh.mjs";
import { AUDIT_DIR, REPO_ROOT } from "./lib/paths.mjs";

const ROOT = REPO_ROOT;
const CONFIG_DIR = AUDIT_DIR;

const args = process.argv.slice(2);
const flags = new Set(args.filter((a) => a.startsWith("--")));
const issueArg = args.find((a) => !a.startsWith("--"));

const jsonOut = flags.has("--json");
const close = flags.has("--close");
const dryRun = flags.has("--dry-run");
const verbose = flags.has("--verbose");

/** @typedef {'implemented' | 'partial' | 'not_implemented' | 'obsolete' | 'meta' | 'needs_review'} IssueStatus */

function loadJson(rel) {
  return JSON.parse(fs.readFileSync(path.join(CONFIG_DIR, rel), "utf8"));
}

function readPackageScripts(pkgRel) {
  const pkgPath = path.join(ROOT, pkgRel);
  if (!fs.existsSync(pkgPath)) return {};
  return JSON.parse(fs.readFileSync(pkgPath, "utf8")).scripts ?? {};
}

function normalizeText(value) {
  return value.toLowerCase().replace(/\s+/g, " ");
}

function issueLabels(issue) {
  return issue.labels.map((l) => l.name);
}

function matchesFeature(issue, feature) {
  const match = feature.match ?? {};
  const haystack = normalizeText(`${issue.title} ${issue.body}`);

  if (match.number != null && issue.number !== match.number) return false;

  if (match.title?.length) {
    const hit = match.title.some((term) =>
      haystack.includes(normalizeText(term)),
    );
    if (!hit) return false;
  }

  if (match.labelsAny?.length) {
    const labels = issueLabels(issue);
    if (!match.labelsAny.some((l) => labels.includes(l))) return false;
  }

  if (match.labelsAll?.length) {
    const labels = issueLabels(issue);
    if (!match.labelsAll.every((l) => labels.includes(l))) return false;
  }

  if (match.body?.length) {
    const hit = match.body.some((term) =>
      haystack.includes(normalizeText(term)),
    );
    if (!hit) return false;
  }

  return Boolean(
    match.title?.length ||
    match.labelsAny?.length ||
    match.labelsAll?.length ||
    match.body?.length ||
    match.number != null,
  );
}

function runGrep({ pattern, paths, ignoreCase = false }) {
  const rgFlags = ignoreCase ? ["-i"] : [];
  const hits = [];
  for (const rel of paths) {
    const abs = path.join(ROOT, rel);
    if (!fs.existsSync(abs)) continue;
    try {
      const out = execFileSync("rg", [...rgFlags, "-l", pattern, abs], {
        encoding: "utf8",
        stdio: ["ignore", "pipe", "ignore"],
      });
      hits.push(...out.split("\n").filter(Boolean));
    } catch {
      // no matches
    }
  }
  return [...new Set(hits.map((p) => path.relative(ROOT, p)))];
}

function evaluateCheck(check) {
  if (check.path) {
    const rel = check.path;
    const ok = fs.existsSync(path.join(ROOT, rel));
    return { ok, detail: rel, kind: "path" };
  }

  if (check.grep) {
    const { pattern, paths, ignoreCase } = check.grep;
    const hits = runGrep({ pattern, paths, ignoreCase });
    return {
      ok: hits.length > 0,
      detail: hits.slice(0, 5).join(", ") || `(no match for /${pattern}/)`,
      kind: "grep",
      hits,
    };
  }

  if (check.glob) {
    const { pattern, min = 1 } = check.glob;
    const hits = globSync(pattern, { cwd: ROOT });
    return {
      ok: hits.length >= min,
      detail: hits.slice(0, 5).join(", ") || pattern,
      kind: "glob",
      hits,
    };
  }

  if (check.packageScript) {
    const { pkg, script } = check.packageScript;
    const scripts = readPackageScripts(pkg);
    return {
      ok: Boolean(scripts[script]),
      detail: `${pkg} → scripts.${script}`,
      kind: "packageScript",
    };
  }

  return { ok: false, detail: "unknown check type", kind: "unknown" };
}

function evaluateGroup(group) {
  if (!group) return { passed: false, results: [] };
  const results = (group.all ?? group.any ?? []).map((check) => ({
    check,
    ...evaluateCheck(check),
  }));
  const passed = group.all
    ? results.every((r) => r.ok)
    : results.some((r) => r.ok);
  return { passed, results };
}

function collectEvidence(results) {
  const evidence = [];
  for (const r of results) {
    if (!r.ok) continue;
    if (r.kind === "path") evidence.push(r.detail);
    else if (r.hits?.length) evidence.push(...r.hits.slice(0, 3));
    else if (r.detail) evidence.push(r.detail);
  }
  return [...new Set(evidence)];
}

function collectFailed(results) {
  return results.filter((r) => !r.ok).map((r) => `${r.kind}: ${r.detail}`);
}

function auditIssue(issue, feature, override) {
  if (!feature) {
    return {
      number: issue.number,
      title: issue.title,
      url: issue.url,
      status: "needs_review",
      summary: "No feature matcher — add to audit/features.json",
      evidence: [],
      gaps: [],
      failedChecks: [],
      closable: false,
    };
  }

  const implemented = evaluateGroup(feature.implementedWhen);
  const partial = evaluateGroup(feature.partialWhen);
  const staticEvidence = (feature.evidence ?? []).map((c) => evaluateCheck(c));

  let status = "not_implemented";
  let gaps = [];
  let failedChecks = collectFailed([...implemented.results, ...staticEvidence]);

  if (feature.status === "meta" || feature.status === "obsolete") {
    status = feature.status;
  } else if (implemented.passed) {
    status = "implemented";
    failedChecks = [];
  } else if (partial.passed) {
    status = "partial";
    gaps = [...(feature.gapsIfPartial ?? [])];
    failedChecks = collectFailed(implemented.results);
  } else {
    failedChecks = collectFailed(implemented.results);
  }

  if (override?.status) status = override.status;
  if (override?.gaps) gaps = override.gaps;

  const evidence = [
    ...collectEvidence(implemented.results),
    ...collectEvidence(partial.results),
    ...collectEvidence(staticEvidence),
  ];

  const notes = [];
  if (feature.note) notes.push(feature.note);
  if (override?.note) notes.push(override.note);

  const missingStatic = staticEvidence
    .filter((r) => !r.ok)
    .map((r) => r.detail);
  const closable =
    (status === "implemented" || status === "obsolete") &&
    status !== "meta" &&
    missingStatic.length === 0;

  return {
    number: issue.number,
    title: issue.title,
    url: issue.url,
    status,
    summary: feature.summary,
    featureId: feature.id,
    evidence,
    gaps,
    failedChecks,
    notes: notes.length ? notes : undefined,
    closable,
  };
}

function findFeature(issue, features) {
  return features.find((f) => matchesFeature(issue, f));
}

function loadIssues() {
  if (issueArg && !issueArg.startsWith("--")) {
    const n = Number(issueArg.replace(/^#/, ""));
    const raw = gh([
      "issue",
      "view",
      String(n),
      "--json",
      "number,title,body,labels,url,state",
    ]);
    const issue = JSON.parse(raw);
    if (issue.state !== "OPEN") {
      console.error(`Issue #${n} is ${issue.state}.`);
    }
    return [issue];
  }

  const raw = gh([
    "issue",
    "list",
    "--state",
    "open",
    "--limit",
    "200",
    "--json",
    "number,title,body,labels,url",
  ]);
  return JSON.parse(raw);
}

function formatCloseComment(result) {
  const lines = [
    "Automated triage via `scripts/github-issues/audit.mjs`.",
    "",
    `**Status:** ${result.status}`,
    `**Matcher:** \`${result.featureId}\``,
    `**Summary:** ${result.summary}`,
  ];
  if (result.evidence.length) {
    lines.push(
      "",
      "**Evidence:**",
      ...result.evidence.map((e) => `- \`${e}\``),
    );
  }
  if (result.gaps.length) {
    lines.push("", "**Remaining gaps:**", ...result.gaps.map((g) => `- ${g}`));
  }
  if (result.notes?.length) {
    lines.push("", "**Notes:**", ...result.notes.map((n) => `- ${n}`));
  }
  return lines.join("\n");
}

function printReport(audits) {
  const byStatus = {};
  for (const row of audits) {
    byStatus[row.status] ??= [];
    byStatus[row.status].push(row);
  }

  console.log(`Audited issues: ${audits.length}\n`);
  for (const status of [
    "implemented",
    "obsolete",
    "partial",
    "not_implemented",
    "meta",
    "needs_review",
  ]) {
    const group = byStatus[status];
    if (!group?.length) continue;
    console.log(`## ${status} (${group.length})`);
    for (const row of group.sort((a, b) => a.number - b.number)) {
      console.log(`  #${row.number} ${row.title}`);
      console.log(`    ${row.summary} [${row.featureId ?? "—"}]`);
      if (row.gaps.length) console.log(`    gaps: ${row.gaps.join("; ")}`);
      if (verbose && row.failedChecks.length)
        console.log(`    failed: ${row.failedChecks.join("; ")}`);
    }
    console.log("");
  }
}

function main() {
  const { features } = loadJson("features.json");
  const { overrides } = loadJson("overrides.json");

  const issues = loadIssues();
  const audits = issues.map((issue) => {
    const feature = findFeature(issue, features);
    const override = overrides[String(issue.number)];
    return auditIssue(issue, feature, override);
  });

  if (jsonOut) {
    console.log(
      JSON.stringify(
        {
          generatedAt: new Date().toISOString(),
          configDir: path.relative(ROOT, CONFIG_DIR),
          audits,
        },
        null,
        2,
      ),
    );
    return;
  }

  printReport(audits);

  const closable = audits.filter((a) => a.closable);
  if (!close) {
    console.log(
      `Closable now: ${closable.map((a) => `#${a.number}`).join(", ") || "(none)"}`,
    );
    console.log(
      "See .agents/skills/github-issues/SKILL.md for planning ↔ triage workflow.",
    );
    return;
  }

  for (const row of closable) {
    if (dryRun) {
      console.log(`[dry-run] would close #${row.number}: ${row.title}`);
      continue;
    }
    gh([
      "issue",
      "close",
      String(row.number),
      "--comment",
      formatCloseComment(row),
    ]);
    console.log(`Closed #${row.number}`);
  }
}

main();
