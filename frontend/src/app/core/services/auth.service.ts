import { Injectable, signal } from '@angular/core';

export type AuthUser = {
  readonly email: string | null;
};

type AuthMeResponse = {
  readonly authenticated: boolean;
  readonly email?: string | null;
};

@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly user = signal<AuthUser | null>(null);
  readonly loaded = signal(false);

  async refresh(): Promise<AuthUser | null> {
    try {
      const response = await fetch('/auth/me', {
        headers: {
          Accept: 'application/json',
        },
      });
      if (!response.ok) {
        this.user.set(null);
        return null;
      }
      const payload = (await response.json()) as AuthMeResponse;
      const user = payload.authenticated ? { email: payload.email ?? null } : null;
      this.user.set(user);
      return user;
    } catch {
      this.user.set(null);
      return null;
    } finally {
      this.loaded.set(true);
    }
  }

  setSignedOut(): void {
    this.user.set(null);
    this.loaded.set(true);
  }
}
