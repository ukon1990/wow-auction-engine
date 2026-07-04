import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import {
  AdminApiService,
  AdminSqlExecuteRequest,
  AdminSqlMetadata,
  AdminSqlResult,
} from '@api/generated';
import { Observable } from 'rxjs';

export const DEFAULT_ADMIN_SQL_ROW_LIMIT = 500;

@Injectable({
  providedIn: 'root',
})
export class AdminSqlService {
  private readonly api = inject(AdminApiService);

  execute(request: AdminSqlExecuteRequest): Observable<AdminSqlResult> {
    return this.api.executeAdminSql(request);
  }

  getMetadata(): Observable<AdminSqlMetadata> {
    return this.api.getAdminSqlMetadata();
  }
}

export function readAdminSqlError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const payload = error.error as
      { readonly detail?: string; readonly message?: string } | string | null;
    if (typeof payload === 'string' && payload.trim()) {
      return payload;
    }
    const detail = typeof payload === 'object' ? payload?.detail || payload?.message : null;
    if (detail?.trim()) {
      return detail;
    }
  }
  return 'SQL request failed.';
}
