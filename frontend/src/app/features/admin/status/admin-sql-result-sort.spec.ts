import { compareSqlCellValues, detectColumnSortTypes, sortSqlRows } from './admin-sql-result-sort';

describe('admin-sql-result-sort', () => {
  it('detects numeric columns', () => {
    const types = detectColumnSortTypes(
      ['amount', 'label'],
      [
        ['10', 'alpha'],
        ['-3', 'beta'],
        ['2.5', 'gamma'],
      ],
    );

    expect(types).toEqual(['number', 'string']);
  });

  it('detects date columns from parseable values', () => {
    const types = detectColumnSortTypes(
      ['created_at'],
      [
        ['2024-01-02T10:00:00Z', '2024-01-01T10:00:00Z'],
        ['2024-01-03 12:00:00', '2024-01-02 12:00:00'],
      ],
    );

    expect(types).toEqual(['date']);
  });

  it('detects boolean columns', () => {
    const types = detectColumnSortTypes(
      ['enabled'],
      [
        ['true', 'false'],
        ['false', 'true'],
      ],
    );

    expect(types).toEqual(['boolean']);
  });

  it('uses string sorting when a column mixes numbers and text', () => {
    const types = detectColumnSortTypes(['label'], [['10'], ['beta']]);

    expect(types).toEqual(['string']);
  });

  it('sorts numbers numerically', () => {
    const sorted = sortSqlRows(
      [
        ['10', 'item-b'],
        ['2', 'item-a'],
        ['30', 'item-c'],
      ],
      0,
      'number',
      'asc',
    );

    expect(sorted.map((row) => row[0])).toEqual(['2', '10', '30']);
  });

  it('sorts dates by timestamp', () => {
    const sorted = sortSqlRows(
      [
        ['2024-02-01', 'late'],
        ['2024-01-01', 'early'],
        ['2024-03-01', 'latest'],
      ],
      0,
      'date',
      'desc',
    );

    expect(sorted.map((row) => row[0])).toEqual(['2024-03-01', '2024-02-01', '2024-01-01']);
  });

  it('places null values last when ascending', () => {
    const comparison = compareSqlCellValues(null, '1', 'number', 'asc');
    expect(comparison).toBeGreaterThan(0);
  });
});
