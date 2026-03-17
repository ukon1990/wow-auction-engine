import test from 'node:test';
import assert from 'node:assert/strict';
import { gzipSync } from 'node:zlib';

import {
  collectEnumCandidates,
  createSchema,
  decompressIfNeeded,
  flattenSchemaPaths,
  formatEnumCandidates,
  parseCliArgs,
} from './map-auction-json.mjs';

test('parseCliArgs falls back to the built-in URLs and defaults', () => {
  const parsed = parseCliArgs([]);

  assert.equal(parsed.urls.length, 2);
  assert.equal(parsed.enumThreshold, 20);
  assert.equal(parsed.sampleSize, Number.POSITIVE_INFINITY);
  assert.match(parsed.outDir, /target[\\/]auction-json-map$/);
});

test('parseCliArgs accepts all as the sample size sentinel', () => {
  const parsed = parseCliArgs(['--sample-size', 'all']);

  assert.equal(parsed.sampleSize, Number.POSITIVE_INFINITY);
});

test('parseCliArgs accepts a custom enum threshold', () => {
  const parsed = parseCliArgs(['--enum-threshold', '12']);

  assert.equal(parsed.enumThreshold, 12);
});

test('decompressIfNeeded only expands gzipped content', () => {
  const source = Buffer.from('{"hello":"world"}', 'utf8');
  const compressed = gzipSync(source);

  assert.equal(decompressIfNeeded(compressed, 'https://example.test/data.json.gz').toString('utf8'), source.toString('utf8'));
  assert.equal(decompressIfNeeded(source, 'https://example.test/data.json').toString('utf8'), source.toString('utf8'));
});

test('createSchema merges nested object and array shapes', () => {
  const fixture = {
    url: 'https://example.test/source',
    auctions: [
      {
        id: 1,
        buyout: 10,
        item: {
          id: 19019,
          context: 55,
          bonus_lists: [123, 456],
        },
      },
      {
        id: 2,
        quantity: 4,
        item: {
          id: 19019,
          pet_breed_id: 7,
        },
      },
    ],
  };

  const schema = createSchema(fixture, { sampleSize: 10 });
  const lines = flattenSchemaPaths(schema);

  assert.equal(schema.type, 'object');
  assert.deepEqual(schema.requiredKeys, ['auctions', 'url']);
  assert.equal(schema.properties.auctions.type, 'array');
  assert.equal(schema.properties.auctions.items.type, 'object');
  assert.deepEqual(schema.properties.auctions.items.requiredKeys, ['id', 'item']);
  assert.deepEqual(schema.properties.auctions.items.optionalKeys, ['buyout', 'quantity']);
  assert.equal(schema.properties.auctions.items.properties.item.type, 'object');
  assert.deepEqual(schema.properties.auctions.items.properties.item.optionalKeys, ['bonus_lists', 'context', 'pet_breed_id']);
  assert.ok(lines.includes('$.auctions[]: object (requiredKeys: 2, optionalKeys: 2)'));
  assert.ok(lines.some((line) => line.startsWith('$.auctions[].item.bonus_lists[]: number')));
});

test('createSchema flags low-cardinality primitive fields as enum candidates', () => {
  const fixture = {
    auctions: [
      {
        modifiers: [
          { type: '9', value: 45 },
          { type: '28', value: 335 },
        ],
        time_left: 'SHORT',
      },
      {
        modifiers: [
          { type: '6', value: 70 },
        ],
        time_left: 'LONG',
      },
      {
        modifiers: [
          { type: '9', value: 45 },
        ],
        time_left: 'MEDIUM',
      },
    ],
  };

  const schema = createSchema(fixture, { sampleSize: Number.POSITIVE_INFINITY, enumThreshold: 20 });
  const candidates = collectEnumCandidates(schema);
  const formatted = formatEnumCandidates(candidates);

  assert.deepEqual(schema.properties.auctions.items.properties.time_left.enumCandidate.values, ['SHORT', 'LONG', 'MEDIUM']);
  assert.deepEqual(schema.properties.auctions.items.properties.modifiers.items.properties.type.enumCandidate.values, ['9', '28', '6']);
  assert.ok(candidates.some((candidate) => candidate.path === '$.auctions[].modifiers[].type'));
  assert.ok(formatted.some((line) => line.includes('$.auctions[].modifiers[].type: string (3 values) -> "9", "28", "6"')));
});

test('createSchema does not mark fields with too many unique values as enum candidates', () => {
  const fixture = {
    values: Array.from({ length: 21 }, (_, index) => ({ code: `VALUE_${index}` })),
  };

  const schema = createSchema(fixture, { sampleSize: Number.POSITIVE_INFINITY, enumThreshold: 20 });

  assert.equal(schema.properties.values.items.properties.code.enumCandidate, undefined);
});




