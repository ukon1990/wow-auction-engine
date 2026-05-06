import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { catchError, finalize, tap } from 'rxjs';

export type AuthUser = {
  readonly email: string | null;
};

type AuthMeResponse = {
  readonly authenticated: boolean;
  readonly email?: string | null;
};

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  readonly user = signal<AuthUser | null>(null);
  readonly loaded = signal(false);

  async refresh() {
    return this.http.get<AuthMeResponse>('/auth/me').pipe(
      tap((response) => {
        const user = response.authenticated ? { email: response.email ?? null } : null;
        this.user.set(user);
      }),
      catchError((error) => {
        this.user.set(null);
        return error;
      }),
      finalize(() => this.loaded.set(true)),
    );
  }

  login(email: string, password: string) {
    return this.http
      .post('/auth/login', {
        email,
        password,
      })
      .pipe(tap(() => this.refresh()));
  }

  requestVerificationCode(email: string, password: string) {
    return this.http.post<{ confirmed: boolean }>('/auth/signup', {
      email,
      password,
    });
  }

  confirmEmailCode(email: string, code: string) {
    return this.http.post('/auth/confirm', {
      email,
      code,
    });
  }

  setSignedOut(): void {
    this.user.set(null);
    this.loaded.set(true);
  }
}
