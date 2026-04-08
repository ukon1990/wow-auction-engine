import test from 'node:test';
import assert from 'node:assert/strict';

import {
  analyzeAuctionField,
  formatAnalysis,
  getValueAtPath,
  mergeAnalyses,
  parseCliArgs,
} from './analyze-auction-field.mjs';

test('parseCliArgs reads required urls and optional auth token', () => {
  const parsed = parseCliArgs([
    '--url', 'https://example.test/one',
    '--url', 'https://example.test/two',
    '--bearer-token', 'secret',
    '--field-path', 'item.bonus_lists',
    '--top', '10',
  ]);

  assert.deepEqual(parsed.urls, ['https://example.test/one', 'https://example.test/two']);
  assert.equal(parsed.bearerToken, 'secret');
  assert.equal(parsed.fieldPath, 'item.bonus_lists');
  assert.equal(parsed.topN, 10);
});

test('parseCliArgs allows help without requiring urls', () => {
  const parsed = parseCliArgs(['--help']);

  assert.equal(parsed.help, true);
  assert.deepEqual(parsed.urls, []);
});

test('getValueAtPath walks nested auction paths', () => {
  const auction = {
    item: {
      bonus_lists: [123, 456],
    },
  };

  assert.deepEqual(getValueAtPath(auction, 'item.bonus_lists'), [123, 456]);
  assert.equal(getValueAtPath(auction, 'item.context'), undefined);
});

test('analyzeAuctionField summarizes array-backed bonus list values', () => {
  const payload = {
    auctions: [
      { item: { bonus_lists: [10, 20] } },
      { item: { bonus_lists: [20, 10] } },
      { item: { bonus_lists: [30] } },
      { item: { bonus_lists: [] } },
      { item: { context: 55 } },
    ],
  };

  const analysis = analyzeAuctionField(payload, 'item.bonus_lists', { topN: 10 });

  assert.equal(analysis.auctionsAnalyzed, 5);
  assert.equal(analysis.auctionsWithField, 4);
  assert.equal(analysis.auctionsWithoutField, 1);
  assert.equal(analysis.arrayFieldCount, 4);
  assert.equal(analysis.emptyArrayCount, 1);
  assert.equal(analysis.totalExtractedValues, 5);
  assert.equal(analysis.distinctValueCount, 3);
  assert.deepEqual(analysis.topValues, [
    { value: 10, count: 2 },
    { value: 20, count: 2 },
    { value: 30, count: 1 },
  ]);
  assert.deepEqual(analysis.topCombinations, [
    { values: [10, 20], count: 2 },
    { values: [30], count: 1 },
  ]);
  assert.deepEqual(analysis.arrayLengthDistribution, [
    { length: 0, count: 1 },
    { length: 1, count: 1 },
    { length: 2, count: 2 },
  ]);
});

test('mergeAnalyses combines per-source counters', () => {
  const first = analyzeAuctionField({ auctions: [{ item: { bonus_lists: [1, 2] } }] }, 'item.bonus_lists', { topN: 10 });
  const second = analyzeAuctionField({ auctions: [{ item: { bonus_lists: [2] } }] }, 'item.bonus_lists', { topN: 10 });

  const merged = mergeAnalyses([first, second], { topN: 10 });

  assert.equal(merged.auctionsAnalyzed, 2);
  assert.equal(merged.totalExtractedValues, 3);
  assert.deepEqual(merged.topValues, [
    { value: 2, count: 2 },
    { value: 1, count: 1 },
  ]);
});

test('formatAnalysis renders a readable summary', () => {
  const analysis = analyzeAuctionField({ auctions: [{ item: { bonus_lists: [1, 2] } }] }, 'item.bonus_lists', { topN: 10 });
  const text = formatAnalysis(analysis, 'Aggregate');

  assert.match(text, /Aggregate/);
  assert.match(text, /topValues:/);
  assert.match(text, /topCombinations:/);
});
