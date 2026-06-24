import { AdminSqlExecuteRequest } from '@api/generated';
import { isExplainableAdminSql, validateAdminSql } from './admin-sql-validation';

describe('admin SQL validation', () => {
  it('allows read-only diagnostics for query mode', () => {
    expect(
      validateAdminSql('SELECT * FROM auction', AdminSqlExecuteRequest.ModeEnum.Query),
    ).toBeNull();
    expect(
      validateAdminSql(
        'WITH rows AS (SELECT 1) SELECT * FROM rows',
        AdminSqlExecuteRequest.ModeEnum.Query,
      ),
    ).toBeNull();
    expect(validateAdminSql('SHOW TABLES', AdminSqlExecuteRequest.ModeEnum.Query)).toBeNull();
    expect(validateAdminSql('DESCRIBE auction', AdminSqlExecuteRequest.ModeEnum.Query)).toBeNull();
  });

  it('blocks destructive sql', () => {
    expect(
      validateAdminSql('DELETE FROM auction', AdminSqlExecuteRequest.ModeEnum.Query),
    ).toContain('DELETE');
    expect(
      validateAdminSql('UPDATE auction SET item_id = 1', AdminSqlExecuteRequest.ModeEnum.Query),
    ).toContain('UPDATE');
    expect(validateAdminSql('SELECT 1; SELECT 2', AdminSqlExecuteRequest.ModeEnum.Query)).toContain(
      'one SQL statement',
    );
  });

  it('allows explain and analyze only for select-like queries', () => {
    expect(
      validateAdminSql('SELECT * FROM auction', AdminSqlExecuteRequest.ModeEnum.Explain),
    ).toBeNull();
    expect(
      validateAdminSql(
        'WITH rows AS (SELECT 1) SELECT * FROM rows',
        AdminSqlExecuteRequest.ModeEnum.Analyze,
      ),
    ).toBeNull();
    expect(validateAdminSql('SHOW TABLES', AdminSqlExecuteRequest.ModeEnum.Explain)).toContain(
      'SELECT or WITH',
    );
    expect(isExplainableAdminSql('UPDATE auction SET item_id = 1')).toBe(false);
  });

  it('ignores blocked keywords in strings and comments', () => {
    expect(
      validateAdminSql(
        `
        SELECT 'delete from auction'
        -- update auction
        FROM auction
        `,
        AdminSqlExecuteRequest.ModeEnum.Query,
      ),
    ).toBeNull();
  });
});
