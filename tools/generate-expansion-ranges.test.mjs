import test from 'node:test';
import assert from 'node:assert/strict';
import { compressRanges, parseDataLua, renderSql } from './generate-expansion-ranges.mjs';

test('parses Data.lua item and version tables', () => {
  const lua = `
local _, AddonTable = ...
AddonTable.itemIdToVersionId = {[100]=0,[101]=0,[200]=1}
AddonTable.versionIdToVersion = {[0]={major=1,minor=0,patch=0,build=1},[1]={major=2,minor=0,patch=0,build=2}}
`;

  const result = parseDataLua(lua);

  assert.equal(result.versions.size, 2);
  assert.deepEqual(result.itemVersions, [
    { itemId: 100, versionId: 0, expansionId: 1, majorVersion: 1 },
    { itemId: 101, versionId: 0, expansionId: 1, majorVersion: 1 },
    { itemId: 200, versionId: 1, expansionId: 2, majorVersion: 2 },
  ]);
});

test('rejects unsupported major versions', () => {
  const lua = `
AddonTable.itemIdToVersionId = {[100]=0}
AddonTable.versionIdToVersion = {[0]={major=99,minor=0,patch=0,build=1}}
`;

  assert.throws(() => parseDataLua(lua), /unsupported major version 99/);
});

test('compresses contiguous ranges by expansion', () => {
  const ranges = compressRanges([
    { itemId: 5, expansionId: 1 },
    { itemId: 6, expansionId: 1 },
    { itemId: 8, expansionId: 1 },
    { itemId: 7, expansionId: 2 },
  ]);

  assert.deepEqual(ranges, [
    { expansionId: 1, startItemId: 5, endItemId: 6 },
    { expansionId: 1, startItemId: 8, endItemId: 8 },
    { expansionId: 2, startItemId: 7, endItemId: 7 },
  ]);
});

test('renders idempotent sql with generated range replacement', () => {
  const sql = renderSql([{ expansionId: 1, startItemId: 5, endItemId: 6 }], new Date('2026-06-23T00:00:00Z'));

  assert.match(sql, /INSERT INTO expansion/);
  assert.match(sql, /DELETE FROM expansion_item_range\nWHERE source = 'itemversion'/);
  assert.match(sql, /\(1, 5, 6, 'itemversion', TRUE, 'Generated from ItemVersion\/Data.lua'\)/);
});
