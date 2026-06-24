import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { AdminSqlExecuteRequest, AdminSqlResult } from '@api/generated';
import {
  AdminSqlService,
  DEFAULT_ADMIN_SQL_ROW_LIMIT,
  readAdminSqlError,
} from './admin-sql.service';
import { AdminSqlResultComponent } from './admin-sql-result.component';
import { validateAdminSql } from './admin-sql-validation';
import { AdminSavedQueryStorageService } from './admin-saved-query-storage.service';
import { AdminTableBrowserComponent } from './admin-table-browser.component';

const standaloneModel = { standalone: true };

@Component({
  selector: 'app-admin-sql-editor',
  imports: [AdminTableBrowserComponent, FormsModule, AdminSqlResultComponent],
  template: `
    <section
      class="ee-glass flex flex-wrap items-center justify-between gap-3 rounded-lg p-inner-padding"
      aria-label="Admin SQL editor"
    >
      <div>
        <h2 class="font-cinzel text-xl font-bold text-primary-container">SQL editor</h2>
        <p class="ee-data text-outline">Run read-only diagnostics in a dedicated workspace.</p>
      </div>
      <button
        type="button"
        class="h-10 rounded-md bg-primary px-4 font-semibold text-on-primary transition hover:opacity-90 focus:outline-none focus:ring-2 focus:ring-primary-container"
        (click)="openEditor()"
      >
        Open SQL editor
      </button>
    </section>

    @if (open()) {
      <div
        class="fixed inset-0 z-50 grid place-items-center bg-black/75 p-3 sm:p-5"
        role="dialog"
        aria-modal="true"
        aria-labelledby="admin-sql-editor-title"
        (click)="closeEditor()"
      >
        <section
          class="ee-glass grid h-[92vh] w-full max-w-[96rem] grid-rows-[auto_1fr] overflow-hidden rounded-lg"
          (click)="$event.stopPropagation()"
        >
          <header class="flex flex-wrap items-center justify-between gap-3 border-b border-white/10 px-container-padding py-4">
            <div>
              <h2 id="admin-sql-editor-title" class="font-cinzel text-xl font-bold text-primary-container">
                SQL editor
              </h2>
              <p class="ee-data text-outline">Read-only query, explain, and confirmed analyze tools.</p>
            </div>
            <div class="flex flex-wrap items-center gap-3">
              <app-admin-table-browser />
              <button
                type="button"
                class="h-10 rounded-md border border-white/10 px-4 font-semibold text-on-surface transition hover:bg-white/5 focus:outline-none focus:ring-2 focus:ring-primary-container"
                (click)="closeEditor()"
              >
                Close
              </button>
            </div>
          </header>

          <div class="min-h-0 overflow-y-auto p-inner-padding">
            <div class="grid gap-4">
              <textarea
                class="min-h-56 w-full resize-y rounded-md border border-white/10 bg-surface-container p-3 font-mono text-sm text-on-surface outline-none transition placeholder:text-outline focus:border-primary-container focus:ring-2 focus:ring-primary-container/40"
                placeholder="SELECT * FROM item"
                [ngModel]="sql()"
                [ngModelOptions]="standaloneModel"
                (ngModelChange)="updateSql($event)"
              ></textarea>

              <div class="flex flex-wrap items-center justify-between gap-3">
                <label class="ee-label flex items-center gap-2 text-on-surface">
                  <input
                    type="checkbox"
                    class="h-4 w-4 rounded border-white/20 bg-surface-container text-primary accent-primary"
                    [ngModel]="limitRows()"
                    [ngModelOptions]="standaloneModel"
                    (ngModelChange)="limitRows.set($event)"
                  />
                  Limit results to {{ rowLimit }}
                </label>

                <div class="flex flex-wrap gap-3">
                  <button
                    type="button"
                    class="h-10 rounded-md bg-primary px-4 font-semibold text-on-primary transition hover:opacity-90 focus:outline-none focus:ring-2 focus:ring-primary-container disabled:cursor-wait disabled:opacity-70"
                    [disabled]="loading()"
                    (click)="execute(modeEnum.Query)"
                  >
                    Run
                  </button>
                  <button
                    type="button"
                    class="h-10 rounded-md border border-white/10 px-4 font-semibold text-on-surface transition hover:bg-white/5 focus:outline-none focus:ring-2 focus:ring-primary-container disabled:cursor-wait disabled:opacity-70"
                    [disabled]="loading()"
                    (click)="execute(modeEnum.Explain)"
                  >
                    Explain
                  </button>
                  <button
                    type="button"
                    class="h-10 rounded-md border border-tertiary-container/50 px-4 font-semibold text-tertiary-container transition hover:bg-tertiary-container/10 focus:outline-none focus:ring-2 focus:ring-tertiary-container disabled:cursor-wait disabled:opacity-70"
                    [disabled]="loading()"
                    (click)="execute(modeEnum.Analyze)"
                  >
                    Analyze
                  </button>
                </div>
              </div>

              <div class="grid gap-3 rounded-md border border-white/10 bg-surface-container/60 p-3">
                <div class="flex flex-wrap items-end gap-3">
                  <label class="min-w-[14rem] flex-1">
                    <span class="ee-label mb-1 block text-outline">Saved query name</span>
                    <input
                      type="text"
                      class="h-10 w-full rounded-md border border-white/10 bg-surface-container px-3 text-sm text-on-surface outline-none transition placeholder:text-outline focus:border-primary-container focus:ring-2 focus:ring-primary-container/40"
                      placeholder="Optional name"
                      [ngModel]="saveName()"
                      [ngModelOptions]="standaloneModel"
                      (ngModelChange)="saveName.set($event)"
                    />
                  </label>
                  <button
                    type="button"
                    class="h-10 rounded-md border border-white/10 px-4 font-semibold text-on-surface transition hover:bg-white/5 focus:outline-none focus:ring-2 focus:ring-primary-container disabled:cursor-not-allowed disabled:opacity-60"
                    [disabled]="!sql().trim()"
                    (click)="saveQuery()"
                  >
                    Save
                  </button>
                </div>

                @if (saveNotice()) {
                  <p class="ee-data text-primary-container">{{ saveNotice() }}</p>
                }

                @if (savedQueries().length > 0) {
                  <div class="grid gap-2">
                    <p class="ee-label text-outline">Saved queries</p>
                    <div class="grid max-h-48 gap-2 overflow-y-auto">
                      @for (query of savedQueries(); track query.id) {
                        <div
                          class="flex min-w-0 items-center gap-2 rounded-md border border-white/10 bg-surface-container px-3 py-2"
                        >
                          <button
                            type="button"
                            class="min-w-0 flex-1 truncate text-left font-mono text-sm text-on-surface transition hover:text-primary-container"
                            [title]="query.sql"
                            (click)="loadSavedQuery(query.sql)"
                          >
                            {{ query.name }}
                          </button>
                          <button
                            type="button"
                            class="rounded border border-white/10 px-2 py-1 ee-label text-outline transition hover:bg-white/5 hover:text-error focus:outline-none focus:ring-2 focus:ring-primary-container"
                            (click)="deleteSavedQuery(query.id)"
                          >
                            Delete
                          </button>
                        </div>
                      }
                    </div>
                  </div>
                }
              </div>

              @if (error()) {
                <p class="rounded-md border border-error/30 bg-error/10 px-3 py-2 text-sm text-error" role="alert">
                  {{ error() }}
                </p>
              }

              <app-admin-sql-result [result]="result()" [submittedSql]="submittedSql()" />
            </div>
          </div>
        </section>
      </div>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminSqlEditorComponent {
  protected readonly standaloneModel = standaloneModel;
  protected readonly modeEnum = AdminSqlExecuteRequest.ModeEnum;
  protected readonly rowLimit = DEFAULT_ADMIN_SQL_ROW_LIMIT;

  private readonly service = inject(AdminSqlService);
  private readonly savedQueryStorage = inject(AdminSavedQueryStorageService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly open = signal(false);
  protected readonly sql = signal('');
  protected readonly saveName = signal('');
  protected readonly saveNotice = signal<string | null>(null);
  protected readonly savedQueries = this.savedQueryStorage.queries.asReadonly();
  protected readonly limitRows = signal(true);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly result = signal<AdminSqlResult | null>(null);
  protected readonly submittedSql = signal('');

  protected openEditor(): void {
    this.open.set(true);
  }

  protected closeEditor(): void {
    this.open.set(false);
  }

  protected updateSql(value: string): void {
    this.sql.set(value);
    if (this.error()) {
      this.error.set(null);
    }
    if (this.saveNotice()) {
      this.saveNotice.set(null);
    }
  }

  protected saveQuery(): void {
    const sql = this.sql().trim();
    if (!sql) {
      return;
    }
    const saved = this.savedQueryStorage.save(sql, this.saveName());
    this.saveName.set('');
    this.saveNotice.set(`Saved "${saved.name}".`);
  }

  protected loadSavedQuery(sql: string): void {
    this.sql.set(sql);
    this.error.set(null);
    this.saveNotice.set(null);
  }

  protected deleteSavedQuery(id: string): void {
    this.savedQueryStorage.delete(id);
  }

  protected execute(mode: AdminSqlExecuteRequest.ModeEnum): void {
    const sql = this.sql().trim();
    const validationError = validateAdminSql(sql, mode);
    if (validationError) {
      this.error.set(validationError);
      return;
    }
    if (mode === AdminSqlExecuteRequest.ModeEnum.Analyze && !confirm('Analyze may execute the query. Continue?')) {
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.submittedSql.set(sql);
    this.service
      .execute({
        sql,
        mode,
        limitRows: this.limitRows(),
        rowLimit: DEFAULT_ADMIN_SQL_ROW_LIMIT,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.result.set(result);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.error.set(readAdminSqlError(error));
          this.loading.set(false);
        },
      });
  }
}
