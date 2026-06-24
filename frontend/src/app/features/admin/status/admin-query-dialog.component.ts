import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { AdminRunningQuery, AdminSqlExecuteRequest, AdminSqlResult } from '@api/generated';
import {
  AdminSqlService,
  DEFAULT_ADMIN_SQL_ROW_LIMIT,
  readAdminSqlError,
} from './admin-sql.service';
import { AdminSqlResultComponent } from './admin-sql-result.component';
import { isExplainableAdminSql, validateAdminSql } from './admin-sql-validation';

const standaloneModel = { standalone: true };

@Component({
  selector: 'app-admin-query-dialog',
  imports: [FormsModule, AdminSqlResultComponent],
  template: `
    @if (query(); as query) {
      <div
        class="fixed inset-0 z-50 grid place-items-center bg-black/70 p-4"
        role="dialog"
        aria-modal="true"
        aria-labelledby="query-sql-dialog-title"
        (click)="closed.emit()"
      >
        <section
          class="ee-glass grid max-h-[85vh] w-full max-w-5xl gap-4 overflow-hidden rounded-lg p-inner-padding"
          (click)="$event.stopPropagation()"
        >
          <header class="flex items-center justify-between gap-3">
            <div>
              <h2 id="query-sql-dialog-title" class="font-cinzel text-xl font-bold text-primary-container">
                SQL query
              </h2>
              <p class="ee-data text-outline">
                Started {{ startedAtLabel()(query.startedAt) }} · {{ durationLabel()(query.timeMs) }}
              </p>
            </div>
            <button
              type="button"
              class="h-10 rounded-md border border-white/10 px-4 font-semibold text-on-surface transition hover:bg-white/5 focus:outline-none focus:ring-2 focus:ring-primary-container"
              (click)="closed.emit()"
            >
              Close
            </button>
          </header>

          <pre
            class="max-h-[32vh] overflow-auto whitespace-pre-wrap rounded-md border border-white/10 bg-surface-container p-4 font-mono text-sm text-on-surface"
            >{{ query.info }}</pre
          >

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
                class="h-10 rounded-md border border-white/10 px-4 font-semibold text-on-surface transition hover:bg-white/5 focus:outline-none focus:ring-2 focus:ring-primary-container disabled:cursor-not-allowed disabled:opacity-60"
                [disabled]="!canExplain(query.info) || loading()"
                (click)="execute(query.info, modeEnum.Explain)"
              >
                Explain
              </button>
              <button
                type="button"
                class="h-10 rounded-md border border-tertiary-container/50 px-4 font-semibold text-tertiary-container transition hover:bg-tertiary-container/10 focus:outline-none focus:ring-2 focus:ring-tertiary-container disabled:cursor-not-allowed disabled:opacity-60"
                [disabled]="!canExplain(query.info) || loading()"
                (click)="execute(query.info, modeEnum.Analyze)"
              >
                Analyze
              </button>
            </div>
          </div>

          @if (!canExplain(query.info)) {
            <p class="rounded-md border border-white/10 bg-surface-container px-3 py-2 text-sm text-on-surface-variant">
              Only SELECT or WITH queries can be explained or analyzed.
            </p>
          }

          @if (error()) {
            <p class="rounded-md border border-error/30 bg-error/10 px-3 py-2 text-sm text-error" role="alert">
              {{ error() }}
            </p>
          }

          <app-admin-sql-result [result]="result()" [submittedSql]="submittedSql()" />
        </section>
      </div>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminQueryDialogComponent {
  readonly query = input<AdminRunningQuery | null>(null);
  readonly startedAtLabel = input.required<(value: string) => string>();
  readonly durationLabel = input.required<(timeMs: number) => string>();
  readonly closed = output<void>();

  protected readonly standaloneModel = standaloneModel;
  protected readonly modeEnum = AdminSqlExecuteRequest.ModeEnum;
  protected readonly rowLimit = DEFAULT_ADMIN_SQL_ROW_LIMIT;
  protected readonly limitRows = signal(true);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly result = signal<AdminSqlResult | null>(null);
  protected readonly submittedSql = signal('');

  private readonly service = inject(AdminSqlService);
  private readonly destroyRef = inject(DestroyRef);

  protected canExplain(sql: string | null | undefined): boolean {
    return isExplainableAdminSql(sql);
  }

  protected execute(sql: string | null | undefined, mode: AdminSqlExecuteRequest.ModeEnum): void {
    const statement = sql?.trim() ?? '';
    const validationError = validateAdminSql(statement, mode);
    if (validationError) {
      this.error.set(validationError);
      return;
    }
    if (mode === AdminSqlExecuteRequest.ModeEnum.Analyze && !confirm('Analyze may execute the query. Continue?')) {
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.submittedSql.set(statement);
    this.service
      .execute({
        sql: statement,
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
