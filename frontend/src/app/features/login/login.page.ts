import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';

import { AuthService } from '@core/services/auth.service';
import {
  validateEmail,
  validatePasswordMatch,
  validatePasswordRules,
} from '@core/utils/auth-validation';

type LoginMode = 'login' | 'signup' | 'confirm';

@Component({
  selector: 'app-login-page',
  imports: [FormsModule],
  templateUrl: './login.page.html',
  host: {
    class: 'flex min-h-0 flex-1 flex-col',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  protected readonly mode = signal<LoginMode>('login');
  protected readonly confirmPassword = signal('');
  protected readonly confirmationCode = signal('');
  protected readonly emailTouched = signal(false);
  protected readonly passwordTouched = signal(false);
  protected readonly confirmPasswordTouched = signal(false);
  protected readonly confirmationCodeTouched = signal(false);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly loginModel = signal<{
    email: string;
    password: string;
  }>({ // TODO: https://angular.dev/essentials/signal-forms
    email: '',
    password: '',
  });
  protected readonly loginForm = form<{
    email: string;
    password: string;
  }>(loginModel, (schemaPath) => {
    required(schemaPath.email, {});
  });

  protected readonly emailError = computed(() =>
    this.emailTouched() ? validateEmail(this.email()) : null,
  );
  protected readonly passwordError = computed(() =>
    this.passwordTouched() && this.mode() !== 'confirm'
      ? validatePasswordRules(this.password())
      : null,
  );
  protected readonly confirmPasswordError = computed(() =>
    this.confirmPasswordTouched() && this.mode() === 'signup'
      ? validatePasswordMatch(this.password(), this.confirmPassword())
      : null,
  );
  protected readonly confirmationCodeError = computed(() =>
    this.confirmationCodeTouched() && this.mode() === 'confirm' && !this.confirmationCode().trim()
      ? 'Confirmation code is required'
      : null,
  );

  protected setMode(mode: LoginMode): void {
    this.mode.set(mode);
    this.error.set(null);
    this.notice.set(null);
    this.passwordTouched.set(false);
    this.confirmPasswordTouched.set(false);
    this.confirmationCodeTouched.set(false);
  }

  protected async submit(): Promise<void> {
    this.error.set(null);
    this.notice.set(null);
    const validationError = this.validateInput();
    if (validationError) {
      this.error.set(validationError);
      return;
    }
    this.loading.set(true);
    try {
      if (this.mode() === 'signup') {
        await this.signup();
      } else if (this.mode() === 'confirm') {
        await this.confirm();
      } else {
        await this.login();
      }
    } catch (error) {
      this.error.set(error instanceof Error ? error.message : 'Authentication failed');
    } finally {
      this.loading.set(false);
    }
  }

  private async login(): Promise<void> {
    await requestAuth('/auth/login', {
      email: this.email(),
      password: this.password(),
    });
    await this.auth.refresh();
    window.location.assign(this.returnTo());
  }

  private async signup(): Promise<void> {
    const response = await requestAuth<{ confirmed: boolean }>('/auth/signup', {
      email: this.email(),
      password: this.password(),
    });
    if (response.confirmed) {
      this.notice.set('Account created. You can sign in now.');
      this.mode.set('login');
      return;
    }
    this.notice.set('Enter the confirmation code sent to your email.');
    this.mode.set('confirm');
  }

  private async confirm(): Promise<void> {
    await requestAuth('/auth/confirm', {
      email: this.email(),
      code: this.confirmationCode(),
    });
    this.notice.set('Email confirmed. You can sign in now.');
    this.mode.set('login');
  }

  private returnTo(): string {
    const value = this.route.snapshot.queryParamMap.get('returnTo');
    if (!value || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/auth/')) {
      return '/';
    }
    return value;
  }

  private validateInput(): string | null {
    const emailError = validateEmail(this.email());
    if (emailError) return emailError;
    if (this.mode() === 'confirm') {
      return this.confirmationCode().trim() ? null : 'Confirmation code is required';
    }
    const passwordError = validatePasswordRules(this.password());
    if (passwordError) return passwordError;
    if (this.mode() === 'signup') {
      return validatePasswordMatch(this.password(), this.confirmPassword());
    }
    return null;
  }
}

async function requestAuth<T = unknown>(url: string, body: unknown): Promise<T> {
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  });
  const payload = (await response.json().catch(() => ({}))) as { error?: string } & T;
  if (!response.ok) {
    throw new Error(payload.error ?? 'Authentication failed');
  }
  return payload;
}
