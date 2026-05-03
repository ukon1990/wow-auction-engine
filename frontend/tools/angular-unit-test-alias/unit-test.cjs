const { createBuilder } = require('@angular-devkit/architect');
const { dirname, join, normalize } = require('node:path');

const angularBuildRoot = dirname(require.resolve('@angular/build/package.json'));
const { execute } = require(join(angularBuildRoot, 'src/builders/unit-test/index.js'));

function asArray(value) {
  return Array.isArray(value) ? value : value === undefined ? [] : [value];
}

function normalizeIncludePath(value) {
  let entry = normalize(String(value));
  const workspaceRoot = normalize(process.cwd());

  if (entry.startsWith(`${workspaceRoot}/`)) {
    entry = entry.slice(workspaceRoot.length + 1);
  }

  const absoluteWorkspaceMarker = '/frontend/';
  const absoluteWorkspaceIndex = entry.indexOf(absoluteWorkspaceMarker);
  if (absoluteWorkspaceIndex >= 0) {
    return entry.slice(absoluteWorkspaceIndex + absoluteWorkspaceMarker.length);
  }

  const relativeWorkspaceMarker = 'frontend/';
  const relativeWorkspaceIndex = entry.indexOf(relativeWorkspaceMarker);
  if (relativeWorkspaceIndex >= 0) {
    return entry.slice(relativeWorkspaceIndex + relativeWorkspaceMarker.length);
  }

  return entry;
}

function normalizeIncludes(include) {
  const includes = asArray(include);
  return includes.length ? includes.map(normalizeIncludePath) : include;
}

function shouldSkipProject(projectRoot, include) {
  const includes = asArray(include).map((entry) => normalize(String(entry)));
  if (!includes.length) {
    return false;
  }

  if (projectRoot) {
    const root = normalize(projectRoot);
    return !includes.some(
      (entry) => entry === root || entry.startsWith(`${root}/`) || entry.includes(`/${root}/`),
    );
  }

  return includes.every(
    (entry) => entry.startsWith('ethereal-ui/') || entry.includes('/ethereal-ui/'),
  );
}

module.exports = createBuilder(async function* unitTestAliasBuilder(options, context) {
  const { config: _config, ...angularOptions } = options;
  angularOptions.include = normalizeIncludes(angularOptions.include);

  const project = await context.getProjectMetadata(context.target);

  if (shouldSkipProject(String(project.root || ''), angularOptions.include)) {
    yield { success: true };
    return;
  }

  yield* execute(angularOptions, context);
});
