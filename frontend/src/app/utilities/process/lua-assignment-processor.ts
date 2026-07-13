import { parse } from 'luaparse';

export type LuaPrimitive = string | number | boolean | null;
export type LuaValue = LuaPrimitive | LuaValue[] | { [key: string]: LuaValue };
export type LuaAssignments = Readonly<Record<string, LuaValue>>;

export type LuaProcessingLimits = Readonly<{
  maxDepth: number;
  maxNodes?: number;
  maxSourceCharacters?: number;
}>;

const DEFAULT_LIMITS: LuaProcessingLimits = {
  maxDepth: 64,
};

type AstNode = {
  type: string;
  body?: unknown;
  variables?: unknown;
  init?: unknown;
  name?: unknown;
  value?: unknown;
  operator?: unknown;
  argument?: unknown;
  fields?: unknown;
  key?: unknown;
  raw?: unknown;
};

export class LuaProcessingError extends Error {
  constructor(
    readonly code: 'INPUT_LIMIT_EXCEEDED' | 'MALFORMED_LUA' | 'UNSUPPORTED_LUA',
    message: string,
  ) {
    super(message);
    this.name = 'LuaProcessingError';
  }
}

/** Parses declarative SavedVariables assignments. It never evaluates or executes Lua. */
export function processLuaAssignments(
  source: string,
  limits: LuaProcessingLimits = DEFAULT_LIMITS,
  selectedAssignmentNames?: ReadonlySet<string>,
): LuaAssignments {
  if (limits.maxSourceCharacters !== undefined && source.length > limits.maxSourceCharacters) {
    throw new LuaProcessingError('INPUT_LIMIT_EXCEEDED', 'Lua source exceeds the local limit.');
  }

  let chunk: AstNode;
  try {
    chunk = parse(source, {
      luaVersion: '5.1',
      comments: false,
      scope: false,
    }) as unknown as AstNode;
  } catch (cause) {
    throw new LuaProcessingError(
      'MALFORMED_LUA',
      cause instanceof Error ? cause.message : 'Unable to parse Lua source.',
    );
  }

  const context = { nodes: 0, limits };
  const assignments: Record<string, LuaValue> = Object.create(null) as Record<string, LuaValue>;
  for (const statement of nodes(chunk.body)) {
    countNode(context);
    if (statement.type !== 'AssignmentStatement') continue;
    const variables = nodes(statement.variables);
    const values = nodes(statement.init);
    variables.forEach((variable, index) => {
      if (variable.type !== 'Identifier' || typeof variable.name !== 'string') return;
      if (selectedAssignmentNames && !selectedAssignmentNames.has(variable.name)) return;
      const value = values[index];
      if (value) {
        rejectDangerousKey(variable.name);
        assignments[variable.name] = astToValue(value, context, 0);
      }
    });
  }
  return assignments;
}

function astToValue(
  node: AstNode,
  context: { nodes: number; limits: LuaProcessingLimits },
  depth: number,
): LuaValue {
  countNode(context);
  if (depth > context.limits.maxDepth) {
    throw new LuaProcessingError('INPUT_LIMIT_EXCEEDED', 'Lua table nesting limit exceeded.');
  }
  switch (node.type) {
    case 'StringLiteral':
      return typeof node.value === 'string'
        ? node.value
        : typeof node.raw === 'string'
          ? decodeLuaString(node.raw)
          : '';
    case 'NumericLiteral':
      return typeof node.value === 'number' && Number.isFinite(node.value) ? node.value : 0;
    case 'BooleanLiteral':
      return node.value === true;
    case 'NilLiteral':
      return null;
    case 'UnaryExpression':
      if (node.operator === '-' && isNode(node.argument)) {
        const value = astToValue(node.argument, context, depth + 1);
        if (typeof value === 'number') return -value;
      }
      break;
    case 'TableConstructorExpression':
      return tableToValue(nodes(node.fields), context, depth + 1);
  }
  throw new LuaProcessingError(
    'UNSUPPORTED_LUA',
    `Unsupported executable Lua expression: ${node.type}.`,
  );
}

function tableToValue(
  fields: AstNode[],
  context: { nodes: number; limits: LuaProcessingLimits },
  depth: number,
): LuaValue {
  const entries: Array<[string, LuaValue]> = [];
  let arrayIndex = 1;
  for (const field of fields) {
    countNode(context);
    const valueNode = field.value;
    if (!isNode(valueNode)) throw unsupportedField(field.type);
    if (field.type === 'TableValue') {
      entries.push([String(arrayIndex++), astToValue(valueNode, context, depth)]);
    } else if (field.type === 'TableKeyString' && isNode(field.key)) {
      entries.push([String(field.key.name), astToValue(valueNode, context, depth)]);
    } else if (field.type === 'TableKey' && isNode(field.key)) {
      const key = astToValue(field.key, context, depth);
      if (typeof key !== 'string' && typeof key !== 'number') throw unsupportedField(field.type);
      entries.push([String(key), astToValue(valueNode, context, depth)]);
    } else {
      throw unsupportedField(field.type);
    }
  }
  const isArray = entries.every(([key], index) => key === String(index + 1));
  if (isArray) return entries.map(([, value]) => value);
  const result = Object.create(null) as Record<string, LuaValue>;
  entries.forEach(([key, value]) => {
    rejectDangerousKey(key);
    result[key] = value;
  });
  return result;
}

function rejectDangerousKey(key: string): void {
  if (key === '__proto__' || key === 'prototype' || key === 'constructor') {
    throw new LuaProcessingError('UNSUPPORTED_LUA', `Unsafe Lua table key: ${key}.`);
  }
}

function unsupportedField(type: string): LuaProcessingError {
  return new LuaProcessingError('UNSUPPORTED_LUA', `Unsupported Lua table field: ${type}.`);
}

function nodes(value: unknown): AstNode[] {
  return Array.isArray(value) ? value.filter(isNode) : [];
}

function isNode(value: unknown): value is AstNode {
  return typeof value === 'object' && value !== null && 'type' in value;
}

function countNode(context: { nodes: number; limits: LuaProcessingLimits }): void {
  context.nodes += 1;
  if (context.limits.maxNodes !== undefined && context.nodes > context.limits.maxNodes) {
    throw new LuaProcessingError('INPUT_LIMIT_EXCEEDED', 'Lua syntax node limit exceeded.');
  }
}

function decodeLuaString(raw: string): string {
  if (raw.length < 2) return raw;
  return raw.slice(1, -1).replace(/\\(\d{1,3}|[abfnrtv\\"'])/g, (_match, escape: string) => {
    if (/^\d+$/.test(escape)) return String.fromCharCode(Number(escape));
    return (
      {
        a: '\x07',
        b: '\b',
        f: '\f',
        n: '\n',
        r: '\r',
        t: '\t',
        v: '\v',
        '\\': '\\',
        '"': '"',
        "'": "'",
      } as Record<string, string>
    )[escape];
  });
}
