/**
 * Branch names for issue work: <issue-type>/<issue-id>-<issue-title-slug>
 * e.g. bug/123-users-cannot-log-in
 */

/** @param {string} text */
export function slugify(text) {
  return text
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 72);
}

/**
 * @param {string} title
 * @param {Array<string | { name: string }>} [labels]
 */
export function inferIssueType(title, labels = []) {
  const lower = title.toLowerCase();
  if (/^(fix|bug)(\(|:)/.test(lower)) return "bug";

  const names = labels.map((l) =>
    (typeof l === "string" ? l : l.name).toLowerCase(),
  );
  if (names.includes("bug")) return "bug";
  if (/^chore(\(|:)/.test(lower)) return "chore";
  if (/^docs(\(|:)/.test(lower)) return "docs";

  return "feat";
}

/** Strip conventional-commit style prefix before slugging. */
export function titleSlug(title) {
  const stripped = title.replace(/^[a-z]+(\([^)]+\))?:\s*/i, "");
  return slugify(stripped || title);
}

/**
 * @param {{ number: number, title: string, labels?: Array<string | { name: string }> }} issue
 */
export function branchNameForIssue(issue) {
  const issueType = inferIssueType(issue.title, issue.labels ?? []);
  const slug = titleSlug(issue.title);
  return `${issueType}/${issue.number}-${slug}`;
}
