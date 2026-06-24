export type AdminSqlColumnSortType = 'boolean' | 'date' | 'number' | 'string';

export function detectColumnSortTypes(
  columns: readonly string[],
  rows: readonly (readonly (string | null)[])[],
): readonly AdminSqlColumnSortType[] {
  return columns.map((_, columnIndex) => detectColumnSortType(valuesInColumn(rows, columnIndex)));
}

export function compareSqlCellValues(
  left: string | null | undefined,
  right: string | null | undefined,
  type: AdminSqlColumnSortType,
  direction: 'asc' | 'desc',
): number {
  const multiplier = direction === 'asc' ? 1 : -1;
  const leftMissing = isMissingValue(left);
  const rightMissing = isMissingValue(right);

  if (leftMissing && rightMissing) {
    return 0;
  }
  if (leftMissing) {
    return 1;
  }
  if (rightMissing) {
    return -1;
  }

  const leftValue = left!.trim();
  const rightValue = right!.trim();

  switch (type) {
    case 'boolean':
      return multiplier * (toSortableBoolean(leftValue) - toSortableBoolean(rightValue));
    case 'number':
      return multiplier * (Number(leftValue) - Number(rightValue));
    case 'date':
      return multiplier * (parseSortableDate(leftValue)! - parseSortableDate(rightValue)!);
    default:
      return (
        multiplier *
        leftValue.localeCompare(rightValue, undefined, {
          numeric: true,
          sensitivity: 'base',
        })
      );
  }
}

export function sortSqlRows(
  rows: readonly (readonly (string | null)[])[],
  columnIndex: number,
  type: AdminSqlColumnSortType,
  direction: 'asc' | 'desc',
): readonly (readonly (string | null)[])[] {
  return [...rows].sort((left, right) =>
    compareSqlCellValues(left[columnIndex], right[columnIndex], type, direction),
  );
}

function valuesInColumn(
  rows: readonly (readonly (string | null)[])[],
  columnIndex: number,
): readonly (string | null | undefined)[] {
  return rows.map((row) => row[columnIndex]);
}

function detectColumnSortType(
  values: readonly (string | null | undefined)[],
): AdminSqlColumnSortType {
  const present = values
    .filter((value): value is string => !isMissingValue(value))
    .map((value) => value.trim());

  if (present.length === 0) {
    return 'string';
  }
  if (present.every(isBooleanValue)) {
    return 'boolean';
  }
  if (present.every(isNumericValue)) {
    return 'number';
  }
  if (present.every((value) => parseSortableDate(value) !== null)) {
    return 'date';
  }
  return 'string';
}

function isMissingValue(value: string | null | undefined): boolean {
  return value === null || value === undefined || value.trim() === '';
}

function isBooleanValue(value: string): boolean {
  const normalized = value.trim().toLowerCase();
  return normalized === 'true' || normalized === 'false';
}

function isNumericValue(value: string): boolean {
  const trimmed = value.trim();
  if (trimmed === '') {
    return false;
  }
  return /^-?\d+(\.\d+)?([eE][+-]?\d+)?$/.test(trimmed);
}

function parseSortableDate(value: string): number | null {
  const trimmed = value.trim();
  if (!trimmed) {
    return null;
  }
  if (/^\d+$/.test(trimmed) && trimmed.length < 8) {
    return null;
  }
  const timestamp = Date.parse(trimmed);
  return Number.isNaN(timestamp) ? null : timestamp;
}

function toSortableBoolean(value: string): number {
  return value.trim().toLowerCase() === 'true' ? 1 : 0;
}
