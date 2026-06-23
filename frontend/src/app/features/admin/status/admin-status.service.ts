import { inject, Injectable, signal } from '@angular/core';
import { EMPTY, finalize, Observable, tap } from 'rxjs';
import { AdminApiService, AdminStatus } from '@api/generated';

@Injectable({
  providedIn: 'root',
})
export class AdminStatusService {
  readonly loading = signal(false);
  readonly status = signal<AdminStatus | null>(null);
  readonly error = signal<string | null>(null);
  readonly lastUpdated = signal<Date | null>(null);

  private readonly api = inject(AdminApiService);
  private inFlight = false;

  refresh(showLoading = true): Observable<AdminStatus> {
    if (this.inFlight) {
      return EMPTY;
    }

    this.inFlight = true;
    if (showLoading) {
      this.loading.set(true);
    }

    return this.api.getAdminStatus().pipe(
      tap({
        next: (status) => {
          this.status.set(status);
          this.error.set(null);
          this.lastUpdated.set(new Date());
        },
        error: () => {
          this.error.set('Unable to load admin status.');
        },
      }),
      finalize(() => {
        this.inFlight = false;
        if (showLoading) {
          this.loading.set(false);
        }
      }),
    );
  }
}
