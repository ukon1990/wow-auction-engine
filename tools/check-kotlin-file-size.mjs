#!/usr/bin/env node

import assert from 'node:assert/strict';
import { readdir, readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const PRODUCTION_LIMIT = 600;
const TEST_LIMIT = 1000;
const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

export function countCodeBearingLines(source) {
  const lines = source.split(/\r?\n/);
  let blockCommentDepth = 0;
  let inTripleString = false;
  let codeBearingLines = 0;

  for (const line of lines) {
    let hasCode = false;
    let inString = false;
    let inCharacter = false;
    let escaped = false;

    for (let index = 0; index < line.length; index += 1) {
      const character = line[index];
      const nextCharacter = line[index + 1];
      const nextTwoCharacters = line.slice(index, index + 3);

      if (blockCommentDepth > 0) {
        if (character === '/' && nextCharacter === '*') {
          blockCommentDepth += 1;
          index += 1;
        } else if (character === '*' && nextCharacter === '/') {
          blockCommentDepth -= 1;
          index += 1;
        }
        continue;
      }

      if (inTripleString) {
        if (nextTwoCharacters === '"""') {
          hasCode = true;
          inTripleString = false;
          index += 2;
        } else if (!/\s/.test(character)) {
          hasCode = true;
        }
        continue;
      }

      if (inString || inCharacter) {
        hasCode = true;
        if (escaped) {
          escaped = false;
        } else if (character === '\\') {
          escaped = true;
        } else if ((inString && character === '"') || (inCharacter && character === "'")) {
          inString = false;
          inCharacter = false;
        }
        continue;
      }

      if (character === '/' && nextCharacter === '/') {
        break;
      }
      if (character === '/' && nextCharacter === '*') {
        blockCommentDepth = 1;
        index += 1;
        continue;
      }
      if (nextTwoCharacters === '"""') {
        hasCode = true;
        inTripleString = true;
        index += 2;
        continue;
      }
      if (character === '"') {
        hasCode = true;
        inString = true;
        continue;
      }
      if (character === "'") {
        hasCode = true;
        inCharacter = true;
        continue;
      }
      if (!/\s/.test(character)) {
        hasCode = true;
      }
    }

    if (hasCode) {
      codeBearingLines += 1;
    }
  }

  return codeBearingLines;
}

async function findKotlinFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = await Promise.all(
    entries.map(async (entry) => {
      const entryPath = path.join(directory, entry.name);
      if (entry.isDirectory()) {
        return findKotlinFiles(entryPath);
      }
      return entry.isFile() && entry.name.endsWith('.kt') ? [entryPath] : [];
    }),
  );
  return files.flat();
}

function exceedsLimit(lineCount, limit) {
  return lineCount > limit;
}

async function checkSourceRoot(relativeRoot, limit) {
  const sourceRoot = path.join(repositoryRoot, relativeRoot);
  const files = await findKotlinFiles(sourceRoot);
  const violations = [];

  for (const file of files) {
    const source = await readFile(file, 'utf8');
    const lineCount = countCodeBearingLines(source);
    if (exceedsLimit(lineCount, limit)) {
      violations.push({ file: path.relative(repositoryRoot, file), lineCount, limit });
    }
  }

  return { checked: files.length, violations };
}

function runSelfTests() {
  assert.equal(countCodeBearingLines(''), 0);
  assert.equal(countCodeBearingLines('\n  \n\t'), 0);
  assert.equal(countCodeBearingLines('// comment\n  // comment'), 0);
  assert.equal(countCodeBearingLines('/* comment\n * still comment\n */'), 0);
  assert.equal(countCodeBearingLines('/* outer /* nested */ comment */'), 0);
  assert.equal(countCodeBearingLines('val answer = 42 // inline comment'), 1);
  assert.equal(countCodeBearingLines('/* comment */ val answer = 42'), 1);
  assert.equal(countCodeBearingLines('val url = "https://example.com/*path*/"'), 1);
  assert.equal(countCodeBearingLines("val slash = '/' // comment"), 1);
  assert.equal(
    countCodeBearingLines('val query = """\n  // literal content\n  /* also literal content */\n"""'),
    4,
  );
  assert.equal(countCodeBearingLines('val text = "escaped \\\" // still a string"'), 1);
  assert.equal(exceedsLimit(600, PRODUCTION_LIMIT), false);
  assert.equal(exceedsLimit(601, PRODUCTION_LIMIT), true);
  assert.equal(exceedsLimit(1000, TEST_LIMIT), false);
  assert.equal(exceedsLimit(1001, TEST_LIMIT), true);
  assert.equal(
    path
      .join(repositoryRoot, 'backend/target/generated-sources/openapi/example.kt')
      .startsWith(path.join(repositoryRoot, 'backend/src/')),
    false,
  );
  console.log('Kotlin file-size checker self-tests passed.');
}

async function main() {
  if (process.argv.includes('--test')) {
    runSelfTests();
    return;
  }

  const results = await Promise.all([
    checkSourceRoot('backend/src/main/kotlin', PRODUCTION_LIMIT),
    checkSourceRoot('backend/src/test/kotlin', TEST_LIMIT),
  ]);
  const violations = results.flatMap((result) => result.violations);

  if (violations.length > 0) {
    for (const violation of violations) {
      console.error(`${violation.file}: ${violation.lineCount} code-bearing lines (max ${violation.limit})`);
    }
    process.exitCode = 1;
    return;
  }

  const checkedFiles = results.reduce((total, result) => total + result.checked, 0);
  console.log(`Kotlin file-size check passed (${checkedFiles} files).`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await main();
}
