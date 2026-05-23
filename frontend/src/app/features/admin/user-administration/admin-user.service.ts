import { inject, Injectable, signal } from '@angular/core';
import { finalize, tap } from 'rxjs';
import { AdminApiService, User } from '@api/generated';

@Injectable({
  providedIn: 'root',
})
export class AdminUserService {
  readonly loading = signal<boolean>(false);
  readonly users = signal<User[]>([]);
  private readonly api = inject(AdminApiService);

  getAllUsers = () => {
    this.loading.set(true);
    return this.api.listUsers().pipe(
      tap((res) => this.users.set(res)),
      finalize(() => this.loading.set(false)),
    );
  };
}
