import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { branchNameForIssue } from "./branches.mjs";
import { gh, ghSpawn } from "./gh.mjs";
import { formatLabelWarning, resolveLabels } from "./labels.mjs";

export function getRepo() {
  return JSON.parse(gh(["repo", "view", "--json", "nameWithOwner"]))
    .nameWithOwner;
}

export function getIssue(number) {
  return JSON.parse(
    gh([
      "issue",
      "view",
      String(number),
      "--json",
      "number,title,body,url,state,labels",
    ]),
  );
}

/** @param {number} issueNumber */
function getIssueRest(issueNumber) {
  const repo = getRepo();
  return JSON.parse(
    gh(["api", `repos/${repo}/issues/${issueNumber}`]),
  );
}

/**
 * Parse Parent / Blocked by refs from issue body Dependencies section.
 * @param {string} body
 */
export function parseBodyDependencies(body = "") {
  const parentMatch = body.match(/\*\*Parent:\*\*\s*#(\d+)/i);
  const blockedMatch = body.match(/\*\*Blocked by:\*\*\s*([^\n]+)/i);
  const blockedBy = blockedMatch
    ? [...blockedMatch[1].matchAll(/#(\d+)/g)].map((m) => Number(m[1]))
    : [];
  return {
    parentNumber: parentMatch ? Number(parentMatch[1]) : null,
    blockedBy,
  };
}

/** @param {string | undefined} parentIssueUrl */
export function parentNumberFromUrl(parentIssueUrl) {
  if (!parentIssueUrl) return null;
  const m = parentIssueUrl.match(/\/issues\/(\d+)$/);
  return m ? Number(m[1]) : null;
}

/**
 * @param {number} parentNumber
 * @returns {Array<{ number: number, title: string, state: string, url: string, labels: string[] }>}
 */
export function listSubIssues(parentNumber) {
  const repo = getRepo();
  let raw;
  try {
    raw = JSON.parse(
      gh(["api", `repos/${repo}/issues/${parentNumber}/sub_issues`]),
    );
  } catch {
    raw = [];
  }
  if (!Array.isArray(raw)) return [];

  return raw.map((issue) => ({
    number: issue.number,
    title: issue.title,
    state: issue.state,
    url: issue.html_url,
    labels: (issue.labels ?? []).map((l) => l.name),
  }));
}

function slimIssue(issue) {
  return {
    number: issue.number,
    title: issue.title,
    state: issue.state,
    url: issue.url ?? issue.html_url,
    labels: (issue.labels ?? []).map((l) => (typeof l === "string" ? l : l.name)),
    body: issue.body,
  };
}

/**
 * Load issue with parent, sub-issues, and dependency refs.
 * @param {number} issueNumber
 * @param {{ includeBody?: boolean }} [opts]
 */
export function getIssueWithRelations(issueNumber, opts = {}) {
  const includeBody = opts.includeBody !== false;
  const ghIssue = getIssue(issueNumber);
  const rest = getIssueRest(issueNumber);
  const bodyDeps = parseBodyDependencies(ghIssue.body ?? "");

  const parentFromApi = parentNumberFromUrl(rest.parent_issue_url);
  const parentNumber = parentFromApi ?? bodyDeps.parentNumber;

  let parent = null;
  if (parentNumber && parentNumber !== issueNumber) {
    try {
      const p = getIssue(parentNumber);
      parent = {
        number: p.number,
        title: p.title,
        state: p.state,
        url: p.url,
      };
    } catch {
      parent = { number: parentNumber, title: null, state: null, url: null };
    }
  }

  const subIssues = listSubIssues(issueNumber);

  const bodySubIssues = [...(ghIssue.body ?? "").matchAll(/#(\d+)\s+[^\n]+/g)]
    .map((m) => Number(m[1]))
    .filter((n) => n !== issueNumber && n !== parentNumber);

  const subIssueNumbers = new Set([
    ...subIssues.map((s) => s.number),
    ...bodySubIssues,
  ]);

  const result = {
    ...slimIssue(ghIssue),
    suggestedBranch: branchNameForIssue({
      number: ghIssue.number,
      title: ghIssue.title,
      labels: ghIssue.labels,
    }),
    parent,
    subIssues,
    dependencies: {
      parentNumber: parent?.number ?? parentNumber,
      blockedBy: bodyDeps.blockedBy,
      bodySubIssueNumbers: [...subIssueNumbers].filter(
        (n) => !subIssues.some((s) => s.number === n),
      ),
    },
  };

  if (!includeBody) delete result.body;

  return result;
}

export function getIssueRestId(repo, issueNumber) {
  return Number(
    gh(["api", `repos/${repo}/issues/${issueNumber}`, "--jq", ".id"]),
  );
}

export function linkSubIssue(parentNumber, childNumber, dryRun = false) {
  if (dryRun) return { linked: false, skipped: true };

  const repo = getRepo();
  const childRestId = getIssueRestId(repo, childNumber);
  const payload = JSON.stringify({ sub_issue_id: childRestId });

  const result = ghSpawn(
    [
      "api",
      `repos/${repo}/issues/${parentNumber}/sub_issues`,
      "-X",
      "POST",
      "--input",
      "-",
    ],
    { input: payload },
  );

  if (result.status !== 0) {
    return {
      linked: false,
      error:
        result.stderr?.trim() ||
        `Failed to link #${childNumber} under #${parentNumber}`,
    };
  }

  return { linked: true };
}

export function createIssue({
  title,
  body,
  labels = [],
  ensureLabels,
  parentNumber,
  dryRun = false,
}) {
  const { applied, unknown } = resolveLabels(
    labels,
    ensureLabels !== undefined ? { ensure: ensureLabels } : {},
  );
  const labelMeta = {
    applied,
    unknown,
    warning: formatLabelWarning({ applied, unknown }),
  };

  if (dryRun) {
    return {
      number: null,
      url: null,
      title,
      dryRun: true,
      labels: labelMeta,
      parentLink: parentNumber
        ? { attempted: true, linked: false, dryRun: true }
        : null,
    };
  }

  if (!applied.length) {
    throw new Error(
      `No valid labels to apply. Unknown: ${unknown.join(", ") || "(none)"}. Run: gh label list`,
    );
  }

  const tmp = path.join(os.tmpdir(), `wow-auction-engine-issue-${Date.now()}.md`);
  fs.writeFileSync(tmp, body, "utf8");

  try {
    const args = [
      "issue",
      "create",
      "--title",
      title,
      "--body-file",
      tmp,
      ...applied.flatMap((l) => ["--label", l]),
    ];
    const url = gh(args);
    const number = Number(url.split("/").pop());

    let parentLink = null;
    if (parentNumber) {
      parentLink = { attempted: true, ...linkSubIssue(parentNumber, number) };
      if (!parentLink.linked) {
        parentLink.fallback =
          "Parent relationship is in issue body (Dependencies). Sub-issue API link failed.";
      }
    }

    return {
      number,
      url,
      title,
      dryRun: false,
      labels: labelMeta,
      parentLink,
    };
  } finally {
    fs.unlinkSync(tmp);
  }
}

export function updateIssueBody(issueNumber, body, dryRun = false) {
  if (dryRun) return;

  const tmp = path.join(os.tmpdir(), `wow-auction-engine-issue-edit-${Date.now()}.md`);
  fs.writeFileSync(tmp, body, "utf8");
  try {
    gh(["issue", "edit", String(issueNumber), "--body-file", tmp]);
  } finally {
    fs.unlinkSync(tmp);
  }
}

export function addIssueLabels(issueNumber, labels, dryRun = false) {
  const { applied, unknown } = resolveLabels(labels);
  if (dryRun) return { applied, unknown };

  if (applied.length) {
    gh([
      "issue",
      "edit",
      String(issueNumber),
      ...applied.flatMap((l) => ["--add-label", l]),
    ]);
  }

  return {
    applied,
    unknown,
    warning: formatLabelWarning({ applied, unknown }),
  };
}
