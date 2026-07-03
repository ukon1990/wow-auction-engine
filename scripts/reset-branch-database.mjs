#!/usr/bin/env node
/*
 Drops local MariaDB branch databases for branches that no longer exist, or for
 the current non-master branch. The master branch database is dbo.
*/

import { spawn } from 'node:child_process';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const MARIA_DB_IDENTIFIER_MAX_LENGTH = 64;
const BRANCH_DATABASE_PREFIX = 'branch_';
const DATABASE_HASH_LENGTH = 8;

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const repoRoot = resolve(__dirname, '..');

const redactArg = (arg) => (arg.startsWith('-p') ? '-p[redacted]' : arg);

const formatCommand = (cmd, args) => [cmd, ...args.map(redactArg)].join(' ');

const run = (cmd, args, opts = {}) =>
  new Promise((resolvePromise, rejectPromise) => {
    const { capture = false, quiet = false, ...spawnOptions } = opts;
    let stdout = '';
    let stderr = '';
    const child = spawn(cmd, args, {
      stdio: capture ? ['ignore', 'pipe', 'pipe'] : quiet ? 'ignore' : 'inherit',
      shell: process.platform === 'win32',
      ...spawnOptions,
    });
    if (capture) {
      child.stdout.on('data', (chunk) => {
        stdout += chunk;
      });
      child.stderr.on('data', (chunk) => {
        stderr += chunk;
      });
    }
    child.on('error', (err) => {
      rejectPromise(err);
    });
    child.on('close', (code) => {
      if (code === 0) resolvePromise({ stdout, stderr });
      else rejectPromise(new Error(stderr.trim() || `${formatCommand(cmd, args)} exited with code ${code}`));
    });
  });

const getCurrentBranch = async () => {
  try {
    const { stdout } = await run('git', ['branch', '--show-current'], {
      cwd: repoRoot,
      capture: true,
    });
    return stdout.trim();
  } catch (_) {
    return null;
  }
};

const getBranches = async () => {
  const { stdout } = await run('git', ['branch', '--all', '--format=%(refname:short)'], {
    cwd: repoRoot,
    capture: true,
  });
  return stdout
    .split('\n')
    .map((branch) => branch.trim())
    .filter(Boolean)
    .filter((branch) => !branch.includes(' -> '))
    .map((branch) => branch.replace(/^origin\//, ''))
    .filter((branch) => branch !== 'HEAD');
};

const toBranchDatabaseName = (branchName) => {
  const normalized = branchName
    .toLowerCase()
    .replace(/[^a-z0-9_$]+/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_+|_+$/g, '') || 'branch';

  const hash = createHash('sha1').update(branchName).digest('hex').slice(0, DATABASE_HASH_LENGTH);
  const suffix = `_${hash}`;
  const maxNormalizedLength = MARIA_DB_IDENTIFIER_MAX_LENGTH - BRANCH_DATABASE_PREFIX.length - suffix.length;
  const trimmed = normalized.slice(0, maxNormalizedLength).replace(/_+$/g, '') || 'branch';
  return `${BRANCH_DATABASE_PREFIX}${trimmed}${suffix}`;
};

const quoteIdentifier = (identifier) => `\`${identifier.replaceAll('`', '``')}\``;

const parseJdbcMariaDbUrl = (jdbcUrl) => {
  const withoutPrefix = jdbcUrl.replace(/^jdbc:/, '');
  const url = new URL(withoutPrefix);
  return {
    host: url.hostname,
    port: url.port || '3306',
    database: url.pathname.replace(/^\//, ''),
  };
};

const getDatabaseConfig = () => {
  const jdbcUrl =
    process.env.BRANCH_DATABASE_RESET_DB_URL ??
    process.env.SPRING_DATASOURCE_URL ??
    'jdbc:mariadb://localhost:59000/dbo?serverTimezone=UTC&cachePrepStmts=true&useServerPrepStmts=true&rewriteBatchedStatements=true';
  const parsed = parseJdbcMariaDbUrl(jdbcUrl);

  return {
    ...parsed,
    user: process.env.BRANCH_DATABASE_RESET_DB_USERNAME ?? process.env.SPRING_DATASOURCE_USERNAME ?? 'root',
    password: process.env.BRANCH_DATABASE_RESET_DB_PASSWORD ?? process.env.SPRING_DATASOURCE_PASSWORD ?? 'root',
  };
};

const getComposeCommand = async () => {
  const requestedEngine = process.env.CONTAINER_CLI;
  if (requestedEngine) {
    const command = { cmd: requestedEngine, argsPrefix: ['compose'] };
    await assertComposeProjectAvailable(command);
    return command;
  }

  const candidates = [
    { cmd: 'podman', argsPrefix: ['compose'] },
    { cmd: 'docker', argsPrefix: ['compose'] },
    { cmd: 'docker-compose', argsPrefix: [] },
  ];

  for (const candidate of candidates) {
    try {
      await assertComposeProjectAvailable(candidate);
      return candidate;
    } catch (_) {
      // Try the next supported compose engine.
    }
  }

  throw new Error(
    'Could not find a running local MariaDB compose service. Start it with docker/podman compose, or set CONTAINER_CLI=podman or CONTAINER_CLI=docker.',
  );
};

const assertComposeProjectAvailable = async (composeCommand) => {
  const { stdout } = await run(composeCommand.cmd, [
    ...composeCommand.argsPrefix,
    '-f',
    'docker-compose-db.yml',
    'ps',
    '-q',
    'mariadb',
  ], {
    cwd: repoRoot,
    capture: true,
  });
  if (!stdout.trim()) {
    throw new Error(`${composeCommand.cmd} compose project does not have a running mariadb service`);
  }
};

const queryDatabases = async (database) => {
  const composeCommand = await getComposeCommand();
  const { stdout } = await run(composeCommand.cmd, [
    ...composeCommand.argsPrefix,
    '-f',
    'docker-compose-db.yml',
    'exec',
    '-T',
    'mariadb',
    'mariadb',
    `-u${database.user}`,
    `-p${database.password}`,
    '-N',
    '-B',
    '-e',
    "SHOW DATABASES LIKE 'branch\\_%';",
  ], {
    cwd: repoRoot,
    capture: true,
  });
  return stdout.split('\n').map((line) => line.trim()).filter(Boolean);
};

const dropDatabases = async (databaseNames, database) => {
  if (databaseNames.length === 0) return;
  const composeCommand = await getComposeCommand();
  const sql = databaseNames
    .map((databaseName) => `DROP DATABASE IF EXISTS ${quoteIdentifier(databaseName)};`)
    .join(' ');
  await run(composeCommand.cmd, [
    ...composeCommand.argsPrefix,
    '-f',
    'docker-compose-db.yml',
    'exec',
    '-T',
    'mariadb',
    'mariadb',
    `-u${database.user}`,
    `-p${database.password}`,
    '-e',
    sql,
  ], {
    cwd: repoRoot,
  });
};

const main = async () => {
  const dryRun = process.argv.includes('--dry-run');
  const pruneDead = process.argv.includes('--prune-dead');
  const database = getDatabaseConfig();
  const isDocumentedLocalDb =
    ['localhost', '127.0.0.1', '::1'].includes(database.host) && database.port === '59000';
  if (!isDocumentedLocalDb) {
    console.log(
      `Refusing to reset branch database on non-local database ${database.host}:${database.port}/${database.database}.`,
    );
    return;
  }

  let databaseNames;
  if (pruneDead) {
    const liveBranchDatabases = new Set((await getBranches()).map(toBranchDatabaseName));
    databaseNames = (await queryDatabases(database)).filter((databaseName) => !liveBranchDatabases.has(databaseName));
  } else {
    const branchName = await getCurrentBranch();
    if (!branchName) {
      console.log('No git branch context detected; refusing to drop a database.');
      return;
    }

    if (branchName === 'master') {
      console.log('Current branch is master; refusing to drop dbo.');
      return;
    }

    databaseNames = [toBranchDatabaseName(branchName)];
  }

  if (databaseNames.length === 0) {
    console.log('No branch databases to drop.');
    return;
  }

  console.log(`Dropping local branch database(s): ${databaseNames.join(', ')}`);
  if (dryRun) {
    console.log('Dry run only; no database changes made.');
    return;
  }

  await dropDatabases(databaseNames, database);
  console.log('Branch database cleanup complete.');
};

main().catch((err) => {
  console.error(err.message || err);
  process.exit(1);
});
