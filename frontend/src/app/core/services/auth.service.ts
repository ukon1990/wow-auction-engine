import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { catchError, finalize, firstValueFrom, from, map, of, switchMap, tap } from 'rxjs';

import {
  AuthConfirmResponse,
  AuthForgotPasswordResponse,
  AuthLoginResponse,
  AuthMeResponse,
  AuthResetPasswordResponse,
  AuthSignupResponse,
  UserRole,
} from '@api/auth/auth.model';
export type AuthUser = {
  readonly email: string | null;
  readonly roles: UserRole[];
};

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  readonly user = signal<AuthUser | null>(null);
  readonly loaded = signal(false);
  private refreshPromise: Promise<AuthUser | null> | null = null;

  /** Waits for the in-flight session check, or resolves from cache once loaded. */
  whenReady(): Promise<AuthUser | null> {
    if (this.refreshPromise) return this.refreshPromise;
    if (this.loaded()) return Promise.resolve(this.user());
    return this.refresh();
  }

  async refresh(options?: { force?: boolean }): Promise<AuthUser | null> {
    if (options?.force) {
      await this.refreshPromise;
      this.loaded.set(false);
      this.refreshPromise = null;
    }
    if (this.refreshPromise) return this.refreshPromise;
    if (this.loaded()) return Promise.resolve(this.user());

    this.refreshPromise = firstValueFrom(
      this.http.get<AuthMeResponse>('/auth/me').pipe(
        map((response) =>
          response.authenticated ? { email: response.email, roles: response.roles } : null,
        ),
        tap((user) => this.user.set(user)),
        catchError(() => {
          this.user.set(null);
          return of(null);
        }),
        finalize(() => {
          this.loaded.set(true);
          this.refreshPromise = null;
        }),
      ),
    );
    return this.refreshPromise;
  }

  login(email: string, password: string) {
    return this.http
      .post<AuthLoginResponse>('/auth/login', { email, password })
      .pipe(switchMap((response) => from(this.refresh({ force: true })).pipe(map(() => response))));
  }

  requestVerificationCode(email: string, password: string) {
    return this.http.post<AuthSignupResponse>('/auth/signup', {
      email,
      password,
    });
  }

  confirmEmailCode(email: string, code: string) {
    return this.http.post<AuthConfirmResponse>('/auth/confirm', {
      email,
      code,
    });
  }

  requestPasswordReset(email: string) {
    return this.http.post<AuthForgotPasswordResponse>('/auth/forgot-password', {
      email,
    });
  }

  resetPassword(email: string, code: string, password: string) {
    return this.http.post<AuthResetPasswordResponse>('/auth/reset-password', {
      email,
      code,
      password,
    });
  }

  setSignedOut(): void {
    this.user.set(null);
    this.loaded.set(true);
    this.refreshPromise = null;
  }
}
