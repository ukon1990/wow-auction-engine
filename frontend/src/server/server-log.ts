const maxErrorMessageLength = 256;
const maxStackLines = 6;

export function formatErrorForLogSafe(error: unknown): string {
  try {
    return formatErrorForLog(error);
  } catch {
    return 'unknown error';
  }
}

export function registerCompactProcessErrorLogging(): void {
  process.on('uncaughtException', (error) => {
    console.error(`uncaughtException ${formatErrorForLog(error)}`);
  });
  process.on('unhandledRejection', (reason) => {
    console.error(`unhandledRejection ${formatErrorForLog(reason)}`);
  });
}

function formatErrorForLog(error: unknown): string {
  if (error instanceof Error) {
    const details = compactObjectDetails(error);
    const stack = compactStack(error.stack);
    return [formatErrorNameAndMessage(error.name, error.message), details, stack]
      .filter(Boolean)
      .join(' ');
  }

  if (isRecord(error)) {
    return [
      formatErrorNameAndMessage(readString(error, 'name'), readString(error, 'message')),
      compactObjectDetails(error),
    ]
      .filter(Boolean)
      .join(' ');
  }

  return truncate(String(error), maxErrorMessageLength);
}

function formatErrorNameAndMessage(name: string | null, message: string | null): string {
  const errorName = name || 'Error';
  return message ? `${errorName}: ${truncate(message, maxErrorMessageLength)}` : errorName;
}

function compactObjectDetails(error: Error | Record<string, unknown>): string {
  const record = error as Record<string, unknown>;
  const details = [
    formatDetail(record, 'code'),
    formatDetail(record, 'status'),
    formatDetail(record, 'statusText'),
    formatDetail(record, 'url'),
    formatDetail(record, 'type'),
  ].filter(Boolean);

  return details.length ? `(${details.join(' ')})` : '';
}

function formatDetail(record: Record<string, unknown>, key: string): string | null {
  const value = record[key];
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    return `${key}=${truncate(String(value), 200)}`;
  }
  return null;
}

function compactStack(stack: string | undefined): string {
  if (!stack) {
    return '';
  }
  const lines = stack
    .split('\n')
    .slice(1, maxStackLines + 1)
    .map((line) => line.trim())
    .filter(Boolean);

  return lines.length ? `stack="${lines.join(' | ')}"` : '';
}

function readString(record: Record<string, unknown>, key: string): string | null {
  const value = record[key];
  return typeof value === 'string' ? value : null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function truncate(value: string, maxLength: number): string {
  return value.length > maxLength ? `${value.slice(0, maxLength - 1)}…` : value;
}
