import { isPlatformBrowser } from '@angular/common';
import { inject, Injectable, PLATFORM_ID, signal } from '@angular/core';

const STORAGE_KEY = 'admin-sql-editor.saved-queries';

export interface SavedAdminSqlQuery {
  readonly id: string;
  readonly name: string;
  readonly sql: string;
  readonly savedAt: string;
}

@Injectable({
  providedIn: 'root',
})
export class AdminSavedQueryStorageService {
  readonly queries = signal<readonly SavedAdminSqlQuery[]>([]);

  private readonly platformId = inject(PLATFORM_ID);

  constructor() {
    this.queries.set(this.read());
  }

  save(sql: string, name: string): SavedAdminSqlQuery {
    const query: SavedAdminSqlQuery = {
      id: crypto.randomUUID(),
      name: name.trim() || fallbackName(sql),
      sql,
      savedAt: new Date().toISOString(),
    };
    const next = [query, ...this.queries()].slice(0, 50);
    this.write(next);
    return query;
  }

  delete(id: string): void {
    this.write(this.queries().filter((query) => query.id !== id));
  }

  private read(): SavedAdminSqlQuery[] {
    if (!this.hasStorage()) {
      return [];
    }
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) {
        return [];
      }
      const parsed = JSON.parse(raw) as unknown;
      if (!Array.isArray(parsed)) {
        return [];
      }
      return parsed.filter(isSavedQuery);
    } catch {
      return [];
    }
  }

  private write(queries: readonly SavedAdminSqlQuery[]): void {
    this.queries.set(queries);
    if (this.hasStorage()) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(queries));
    }
  }

  private hasStorage(): boolean {
    return isPlatformBrowser(this.platformId) && typeof localStorage !== 'undefined';
  }
}

function fallbackName(sql: string): string {
  const firstLine = sql.trim().split('\n')[0]?.trim();
  return firstLine ? firstLine.slice(0, 60) : 'Untitled query';
}

function isSavedQuery(value: unknown): value is SavedAdminSqlQuery {
  if (!value || typeof value !== 'object') {
    return false;
  }
  const candidate = value as Partial<SavedAdminSqlQuery>;
  return (
    typeof candidate.id === 'string' &&
    typeof candidate.name === 'string' &&
    typeof candidate.sql === 'string' &&
    typeof candidate.savedAt === 'string'
  );
}
