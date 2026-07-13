import {
  genericSavedVariablesSource as genericSource,
  malformedSavedVariablesSource as malformedSource,
} from './testing/fixture-sources';

import { LuaProcessingError, processLuaAssignments } from './lua-assignment-processor';

describe('processLuaAssignments', () => {
  it('converts declarative assignments and nested Lua tables without executing code', () => {
    const assignments = processLuaAssignments(genericSource);

    expect(assignments['ExampleDB']).toEqual({
      enabled: true,
      count: 3,
      label: 'safe',
      values: ['one', 'two'],
      '42': { nested: -7 },
    });
  });

  it.each(['__proto__', 'prototype', 'constructor'])('rejects the dangerous key %s', (key) => {
    expect(() =>
      processLuaAssignments(`ExampleDB = { ["${key}"] = { safe = false } }`),
    ).toThrowError(/Unsafe Lua table key/);
    expect(Object.prototype).not.toHaveProperty('safe');
  });

  it('reports malformed Lua', () => {
    expect(() => processLuaAssignments(malformedSource)).toThrowError(LuaProcessingError);
  });

  it('rejects executable expressions instead of evaluating them', () => {
    expect(() => processLuaAssignments('UnsafeDB = os.execute("no")')).toThrowError(
      /Unsupported executable Lua expression/,
    );
  });

  it('enforces source and syntax limits', () => {
    expect(() =>
      processLuaAssignments('ExampleDB = {}', {
        maxDepth: 10,
        maxNodes: 1,
        maxSourceCharacters: 100,
      }),
    ).toThrowError(/node limit/);
  });
});
