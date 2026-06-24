import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AdminRunningQuery, AdminSqlExecuteRequest, AdminSqlResult } from '@api/generated';
import { AdminQueryDialogComponent } from './admin-query-dialog.component';
import { AdminSqlService } from './admin-sql.service';

const resultFixture: AdminSqlResult = {
  mode: AdminSqlResult.ModeEnum.Explain,
  effectiveSql: 'EXPLAIN FORMAT=JSON SELECT 1 LIMIT 500',
  columns: ['EXPLAIN'],
  rows: [['{"query_block":{}}']],
  rowCount: 1,
  truncated: false,
  durationMs: 4,
};

describe('AdminQueryDialogComponent', () => {
  let fixture: ComponentFixture<AdminQueryDialogComponent>;
  let service: { execute: ReturnType<typeof vitest.fn> };

  beforeEach(async () => {
    service = {
      execute: vitest.fn().mockReturnValue(of(resultFixture)),
    };

    await TestBed.configureTestingModule({
      imports: [AdminQueryDialogComponent],
      providers: [{ provide: AdminSqlService, useValue: service }],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminQueryDialogComponent);
    fixture.componentRef.setInput('startedAtLabel', () => '06:00:00');
    fixture.componentRef.setInput('durationLabel', () => '1.0 s');
  });

  it('explains captured select query', () => {
    fixture.componentRef.setInput('query', queryFixture('SELECT 1'));
    fixture.detectChanges();
    clickButton('Explain');

    expect(service.execute).toHaveBeenCalledWith({
      sql: 'SELECT 1',
      mode: AdminSqlExecuteRequest.ModeEnum.Explain,
      limitRows: true,
      rowLimit: 500,
    });
    expect(fixture.nativeElement.textContent).toContain('query_block');
  });

  it('disables diagnostics for captured update query', () => {
    fixture.componentRef.setInput('query', queryFixture('UPDATE auction SET item_id = 1'));
    fixture.detectChanges();

    const explain = button('Explain');
    expect(explain.disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Only SELECT or WITH queries');
  });

  it('requires confirmation before analyze', () => {
    const confirmSpy = vitest.spyOn(window, 'confirm').mockReturnValue(false);
    fixture.componentRef.setInput('query', queryFixture('SELECT 1'));
    fixture.detectChanges();
    clickButton('Analyze');

    expect(confirmSpy).toHaveBeenCalled();
    expect(service.execute).not.toHaveBeenCalled();
  });

  it('renders a scrollable body and copy control for the captured sql', () => {
    fixture.componentRef.setInput('query', queryFixture('SELECT 1'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.min-h-0.overflow-y-auto')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-admin-sql-code-editor')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('Copy');
  });

  function button(label: string): HTMLButtonElement {
    return [...fixture.nativeElement.querySelectorAll('button')].find(
      (candidate: HTMLButtonElement) => candidate.textContent?.includes(label),
    ) as HTMLButtonElement;
  }

  function clickButton(label: string): void {
    button(label).click();
    fixture.detectChanges();
  }
});

function queryFixture(info: string): AdminRunningQuery {
  return {
    id: 1,
    queryId: 2,
    tid: 3,
    command: 'Query',
    state: 'Running',
    time: 1,
    timeMs: 1000,
    startedAt: '2026-06-23T06:00:00Z',
    info,
  };
}
