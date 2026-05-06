import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import {
  email,
  form,
  FormField,
  required,
  submit as submitForm,
  validate,
} from '@angular/forms/signals';
import type { ValidationError } from '@angular/forms/signals';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { TextInputComponent } from '@ui';
import { AuthErrorResponse } from '@api/auth/auth.model';
import { AuthService } from '@core/services/auth.service';
import { validatePasswordRules } from '@core/utils/auth-validation';
import { LoginAndRegistrationModel, LoginMode } from '@features/login/login.model';

@Component({
  selector: 'app-login-page',
  imports: [FormsModule, FormField, TextInputComponent],
  templateUrl: './login.page.html',
  host: {
    class: 'flex min-h-0 flex-1 flex-col',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  private readonly authService = inject(AuthService);
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly mode = signal<LoginMode>('login');
  protected readonly loading = signal(false);
  protected readonly notice = signal<string | null>(null);

  private getMessage(message: string) {
    return { message };
  }
  private readonly registerModel = signal<LoginAndRegistrationModel>({
    email: '',
    password: '',
    confirmPassword: '',
    confirmationCode: '',
  });
  readonly loginForm = form<LoginAndRegistrationModel>(this.registerModel, (schemaPath) => {
    email(schemaPath.email, this.getMessage('Email is required'));
    required(schemaPath.password, {
      message: 'Password is required',
      when: () => this.mode() !== 'confirm' && this.mode() !== 'forgot',
    });
    validate(schemaPath.password, ({ value }) => {
      if (this.mode() !== 'reset') {
        return undefined;
      }
      if (!value()) {
        return undefined;
      }
      const message = validatePasswordRules(value(), 'New password');
      return message ? { kind: 'password_rules', message } : undefined;
    });

    required(schemaPath.confirmPassword, {
      message: 'The passwords must match',
      when: ({ value, valueOf }) =>
        (this.mode() === 'signup' || this.mode() === 'reset') &&
        valueOf(schemaPath.password) !== value(),
    });

    required(schemaPath.confirmationCode, {
      message: 'Confirmation Code is required',
      when: () => this.mode() === 'confirm' || this.mode() === 'reset',
    });
  });

  protected setMode(mode: LoginMode): void {
    this.mode.set(mode);
    this.notice.set(null);
  }

  protected async submit(event: SubmitEvent): Promise<void> {
    event.preventDefault();

    this.notice.set(null);
    this.loading.set(true);
    try {
      await submitForm(this.loginForm, async () => {
        try {
          if (this.mode() === 'signup') {
            await this.signup();
          } else if (this.mode() === 'confirm') {
            await this.confirm();
          } else if (this.mode() === 'forgot') {
            await this.requestPasswordReset();
          } else if (this.mode() === 'reset') {
            await this.resetPassword();
          } else {
            await this.login();
          }
          return undefined;
        } catch (error) {
          return this.toAuthValidationErrors(error);
        }
      });
    } finally {
      this.loading.set(false);
    }
  }

  private async login(): Promise<void> {
    const { email, password } = this.loginForm().value() as LoginAndRegistrationModel;
    await firstValueFrom(this.authService.login(email, password));
    await this.router.navigateByUrl(this.returnTo());
  }

  private async signup(): Promise<void> {
    const { email, password } = this.loginForm().value() as LoginAndRegistrationModel;
    const response = await firstValueFrom(
      this.authService.requestVerificationCode(email, password),
    );

    if (response.confirmed) {
      await this.login();
      return;
    }

    this.notice.set('Enter the confirmation code sent to your email.');
    this.mode.set('confirm');
  }

  private async confirm(): Promise<void> {
    const { email, confirmationCode: code } = this.loginForm().value() as LoginAndRegistrationModel;
    await firstValueFrom(this.authService.confirmEmailCode(email, code));
    await this.login();
  }

  private async requestPasswordReset(): Promise<void> {
    const { email } = this.loginForm().value() as LoginAndRegistrationModel;
    await firstValueFrom(this.authService.requestPasswordReset(email));
    this.notice.set('If an account exists for this email, a password reset code has been sent.');
    this.mode.set('reset');
  }

  private async resetPassword(): Promise<void> {
    const {
      email,
      confirmationCode: code,
      password,
    } = this.loginForm().value() as LoginAndRegistrationModel;
    await firstValueFrom(this.authService.resetPassword(email, code, password));
    this.registerModel.update((value) => ({
      ...value,
      password: '',
      confirmPassword: '',
      confirmationCode: '',
    }));
    this.mode.set('login');
    this.notice.set('Password reset. Sign in with your new password.');
  }

  private toAuthValidationErrors(error: unknown): ValidationError.WithOptionalFieldTree[] {
    const authError = this.getAuthErrorResponse(error);
    const message = authError?.error ?? 'Unable to complete the request. Please try again.';

    if (!authError) {
      this.notice.set(message);
      return [{ kind: 'server', message }];
    }

    switch (authError.code) {
      case 'invalid_credentials':
        return [
          { kind: authError.code, message, fieldTree: this.loginForm.email },
          { kind: authError.code, message, fieldTree: this.loginForm.password },
        ];
      case 'user_exists':
      case 'user_not_confirmed':
        if (authError.code === 'user_not_confirmed') {
          this.notice.set('Enter the confirmation code sent to your email.');
          this.mode.set('confirm');
        }
        return [{ kind: authError.code, message, fieldTree: this.loginForm.email }];
      case 'weak_password':
        return [{ kind: authError.code, message, fieldTree: this.loginForm.password }];
      case 'code_mismatch':
      case 'expired_code':
        return [{ kind: authError.code, message, fieldTree: this.loginForm.confirmationCode }];
      default:
        this.notice.set(message);
        return [{ kind: authError.code, message }];
    }
  }

  private getAuthErrorResponse(error: unknown): AuthErrorResponse | null {
    if (!(error instanceof HttpErrorResponse)) return null;

    const value = error.error;
    if (!value || typeof value !== 'object') return null;
    if (!('error' in value) || !('code' in value)) return null;
    if (typeof value.error !== 'string' || typeof value.code !== 'string') return null;

    return value as AuthErrorResponse;
  }

  private returnTo(): string {
    const value = this.activatedRoute.snapshot.queryParamMap.get('returnTo');
    if (!value || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/auth/')) {
      return '/';
    }
    return value;
  }
}
