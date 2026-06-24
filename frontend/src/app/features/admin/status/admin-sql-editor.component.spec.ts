import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { AdminSqlExecuteRequest, AdminSqlResult } from '@api/generated';
import { AdminSqlEditorComponent } from './admin-sql-editor.component';
import { AdminSqlCodeEditorComponent } from './admin-sql-code-editor.component';
import { AdminSqlService } from './admin-sql.service';
import {
  AdminSavedQueryStorageService,
  SavedAdminSqlQuery,
} from './admin-saved-query-storage.service';

const resultFixture: AdminSqlResult = {
  mode: AdminSqlResult.ModeEnum.Query,
  effectiveSql: 'SELECT 1 LIMIT 500',
  columns: ['value'],
  rows: [['1']],
  rowCount: 1,
  truncated: false,
  durationMs: 3,
};

describe('AdminSqlEditorComponent', () => {
  let fixture: ComponentFixture<AdminSqlEditorComponent>;
  let service: { execute: ReturnType<typeof vitest.fn> };
  let savedQueries: ReturnType<typeof signal<readonly SavedAdminSqlQuery[]>>;
  let storage: {
    queries: ReturnType<typeof signal<readonly SavedAdminSqlQuery[]>>;
    save: ReturnType<typeof vitest.fn>;
    update: ReturnType<typeof vitest.fn>;
    delete: ReturnType<typeof vitest.fn>;
  };

  beforeEach(async () => {
    service = {
      execute: vitest.fn().mockReturnValue(of(resultFixture)),
    };
    savedQueries = signal<readonly SavedAdminSqlQuery[]>([]);
    storage = {
      queries: savedQueries,
      save: vitest.fn().mockImplementation((sql: string, name: string) => {
        const query = {
          id: 'saved-new',
          name: name || 'SELECT 1',
          sql,
          savedAt: '2026-06-24T06:00:00Z',
        };
        savedQueries.set([...savedQueries(), query]);
        return query;
      }),
      update: vitest.fn().mockImplementation((id: string, sql: string, name: string) => {
        const query = {
          id,
          name: name || 'SELECT 1',
          sql,
          savedAt: '2026-06-24T07:00:00Z',
        };
        savedQueries.set([query]);
        return query;
      }),
      delete: vitest.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [AdminSqlEditorComponent],
      providers: [
        { provide: AdminSqlService, useValue: service },
        { provide: AdminSavedQueryStorageService, useValue: storage },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminSqlEditorComponent);
    fixture.detectChanges();
  });

  it('runs read-only sql with default limiting', async () => {
    openEditor();
    setSql('SELECT 1');
    clickButton('Run');

    expect(service.execute).toHaveBeenCalledWith({
      sql: 'SELECT 1',
      mode: AdminSqlExecuteRequest.ModeEnum.Query,
      limitRows: true,
      rowLimit: 500,
    });
    expect(fixture.nativeElement.textContent).toContain('SELECT 1 LIMIT 500');
  });

  it('blocks destructive sql before submitting', () => {
    openEditor();
    setSql('DELETE FROM auction');
    clickButton('Run');

    expect(service.execute).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('DELETE statements are not allowed');
  });

  it('requires confirmation before analyze', () => {
    const confirmSpy = vitest.spyOn(window, 'confirm').mockReturnValue(false);
    openEditor();
    setSql('SELECT 1');
    clickButton('Analyze');

    expect(confirmSpy).toHaveBeenCalled();
    expect(service.execute).not.toHaveBeenCalled();
  });

  it('runs the query when pressing enter in the editor', async () => {
    openEditor();
    setSql('SELECT 1');
    codeEditor().enterPressed.emit();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(service.execute).toHaveBeenCalledWith({
      sql: 'SELECT 1',
      mode: AdminSqlExecuteRequest.ModeEnum.Query,
      limitRows: true,
      rowLimit: 500,
    });
  });

  it('formats sql from the toolbar', async () => {
    openEditor();
    setSql('select 1');
    clickButton('Format');
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.componentInstance['sql']()).not.toBe('select 1');
    expect(fixture.componentInstance['sql']()).toContain('1');
  });

  it('saves and loads local saved queries', async () => {
    openEditor();
    setSql('SELECT 1');
    const nameInput = fixture.nativeElement.querySelector('input[type="text"]') as HTMLInputElement;
    nameInput.value = 'One';
    nameInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    clickButton('Save');

    expect(storage.save).toHaveBeenCalledWith('SELECT 1', 'One');
    expect(fixture.nativeElement.textContent).toContain('Saved "One".');

    setSql('');
    clickButton('One');
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.componentInstance['sql']()).toBe('SELECT 1');
    expect(fixture.componentInstance['activeSavedQueryId']()).toBe('saved-new');
  });

  it('updates the loaded saved query instead of creating a duplicate', async () => {
    savedQueries.set([
      {
        id: 'saved-1',
        name: 'One',
        sql: 'SELECT 1',
        savedAt: '2026-06-24T06:00:00Z',
      },
    ]);

    openEditor();
    clickButton('One');
    setSql('SELECT 2');
    clickButton('Update');

    expect(storage.update).toHaveBeenCalledWith('saved-1', 'SELECT 2', 'One');
    expect(storage.save).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Updated "One".');
  });

  it('can save a loaded query as a new entry', async () => {
    savedQueries.set([
      {
        id: 'saved-1',
        name: 'One',
        sql: 'SELECT 1',
        savedAt: '2026-06-24T06:00:00Z',
      },
    ]);

    openEditor();
    clickButton('One');
    setSql('SELECT 2');

    const nameInput = fixture.nativeElement.querySelector('input[type="text"]') as HTMLInputElement;
    nameInput.value = 'Two';
    nameInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    clickButton('Save as new');

    expect(storage.save).toHaveBeenCalledWith('SELECT 2', 'Two');
    expect(storage.update).not.toHaveBeenCalled();
    expect(fixture.componentInstance['activeSavedQueryId']()).toBe('saved-new');
    expect(fixture.nativeElement.textContent).toContain('Saved "Two".');
  });

  it('opens the editor in a large modal with code editor and copy controls', () => {
    clickButton('Open SQL editor');

    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-admin-sql-code-editor')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('Copy');
    expect(fixture.nativeElement.textContent).toContain('Tables');
  });

  function openEditor(): void {
    clickButton('Open SQL editor');
  }

  function setSql(value: string): void {
    fixture.componentInstance['updateSql'](value);
    fixture.detectChanges();
  }

  function codeEditor(): AdminSqlCodeEditorComponent {
    return fixture.debugElement.query(
      (debug) => debug.componentInstance instanceof AdminSqlCodeEditorComponent,
    ).componentInstance as AdminSqlCodeEditorComponent;
  }

  function clickButton(label: string): void {
    const button = [...fixture.nativeElement.querySelectorAll('button')].find(
      (candidate: HTMLButtonElement) => candidate.textContent?.includes(label),
    ) as HTMLButtonElement;
    button.click();
    fixture.detectChanges();
  }
});
