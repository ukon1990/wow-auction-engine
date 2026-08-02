import { AdminSqlExecuteRequest } from '@api/generated';

const allowedQueryStarts = new Set(['SELECT', 'WITH', 'SHOW', 'DESCRIBE', 'DESC', 'EXPLAIN']);
const explainableStarts = new Set(['SELECT', 'WITH']);
const blockedKeywords = new Set([
  'INSERT',
  'UPDATE',
  'DELETE',
  'REPLACE',
  'CREATE',
  'ALTER',
  'DROP',
  'TRUNCATE',
  'RENAME',
  'GRANT',
  'REVOKE',
  'CALL',
  'DO',
  'SET',
  'LOCK',
  'UNLOCK',
  'LOAD',
  'KILL',
  'USE',
  'START',
  'COMMIT',
  'ROLLBACK',
  'OPTIMIZE',
  'REPAIR',
]);

export function validateAdminSql(
  sql: string,
  mode: AdminSqlExecuteRequest.ModeEnum,
): string | null {
  const normalized = normalizeSql(sql);
  const first = normalized.keywords[0];
  const second = normalized.keywords[1];

  if (!sql.trim()) {
    return 'SQL is required.';
  }
  if (normalized.sqlWithoutLiterals.includes(';')) {
    return 'Only one SQL statement is allowed.';
  }

  const blocked = normalized.keywords.find((keyword) => blockedKeywords.has(keyword));
  if (blocked) {
    return `${blocked} statements are not allowed.`;
  }

  if (mode === AdminSqlExecuteRequest.ModeEnum.Query) {
    if (!first || !allowedQueryStarts.has(first)) {
      return 'Only read-only SQL diagnostics are allowed.';
    }
    if (first === 'ANALYZE' && second === 'TABLE') {
      return 'ANALYZE TABLE is not allowed.';
    }
    return null;
  }

  if (!first || !explainableStarts.has(first)) {
    return 'Explain and analyze can only run SELECT or WITH queries.';
  }
  return null;
}

export function isExplainableAdminSql(sql: string | null | undefined): boolean {
  if (!sql?.trim()) {
    return false;
  }
  return validateAdminSql(sql, AdminSqlExecuteRequest.ModeEnum.Explain) === null;
}

interface NormalizedSql {
  readonly sqlWithoutLiterals: string;
  readonly keywords: readonly string[];
}

function normalizeSql(sql: string): NormalizedSql {
  let cleaned = '';
  let index = 0;
  while (index < sql.length) {
    const current = sql[index];
    const next = sql[index + 1];

    if (isQuote(current)) {
      cleaned += ' ';
      index = skipQuotedValue(sql, index, current);
      cleaned += ' ';
      continue;
    }

    if ((current === '-' && next === '-') || current === '#') {
      cleaned += ' ';
      index = skipLineComment(sql, index + (current === '#' ? 1 : 2));
      continue;
    }

    if (current === '/' && next === '*') {
      cleaned += ' ';
      index = skipBlockComment(sql, index + 2);
      continue;
    }

    cleaned += current;
    index += 1;
  }

  return {
    sqlWithoutLiterals: cleaned,
    keywords: [...cleaned.matchAll(/[A-Za-z_][A-Za-z0-9_]*/g)].map((match) =>
      match[0].toUpperCase(),
    ),
  };
}

function isQuote(value: string | undefined): value is "'" | '"' | '`' {
  return value === "'" || value === '"' || value === '`';
}

function skipQuotedValue(sql: string, start: number, quote: string): number {
  let index = start + 1;
  while (index < sql.length) {
    if (sql[index] === '\\') {
      index += 2;
    } else if (sql[index] !== quote) {
      index += 1;
    } else if (sql[index + 1] === quote) {
      index += 2;
    } else {
      return index + 1;
    }
  }
  return index;
}

function skipLineComment(sql: string, start: number): number {
  const newline = sql.indexOf('\n', start);
  return newline === -1 ? sql.length : newline;
}

function skipBlockComment(sql: string, start: number): number {
  const commentEnd = sql.indexOf('*/', start);
  return commentEnd === -1 ? sql.length : commentEnd + 2;
}
