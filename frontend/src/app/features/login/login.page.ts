import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { email, form, FormField, required } from '@angular/forms/signals';
import { ActivatedRoute, Router } from '@angular/router';

import { TextInputComponent } from '@ui';
import { AuthService } from '@core/services/auth.service';
import { LoginAndRegistrationModel, LoginMode } from '@features/login/login.model';
import { catchError, finalize, tap } from 'rxjs';

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
    required(schemaPath.password, this.getMessage('Password is required'));

    required(schemaPath.confirmPassword, {
      message: 'The passwords must match',
      when: ({ value, valueOf }) =>
        this.mode() === 'signup' && valueOf(schemaPath.password) !== value(),
    });

    required(schemaPath.confirmationCode, {
      message: 'Confirmation Code is required',
      when: () => this.mode() === 'confirm',
    });
  });

  protected setMode(mode: LoginMode): void {
    this.mode.set(mode);
    this.notice.set(null);
  }

  protected async submit(event: SubmitEvent): Promise<void> {
    event.preventDefault();

    this.notice.set(null);
    if (this.loginForm().invalid()) return;

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
      console.error(error);
    } finally {
      this.loading.set(false);
    }
  }

  private async login(): Promise<void> {
    if (this.mode() === 'login' && !this.loginForm().valid()) return;

    const { email, password } = this.loginForm().value() as LoginAndRegistrationModel;
    this.authService.login(email, password).pipe(
      tap(() => {
        this.router.navigateByUrl(this.returnTo());
      }),
      catchError((error) => {
        // TODO: Check the response type. If I can infer used email etc
        console.log('login error', error);
        return error;
      }),
      finalize(() => this.loading.set(false)),
    );
  }

  private async signup(): Promise<void> {
    const { email, password } = this.loginForm().value() as LoginAndRegistrationModel;
    this.authService.requestVerificationCode(email, password).pipe(
      tap((response) => {
        if (response.confirmed) {
          this.login();
          return;
        }
        this.notice.set('Enter the confirmation code sent to your email.');
        this.mode.set('confirm');
      }),
      catchError((error) => {
        console.log('Invalid code error', error);
        return error;
      }),
    );
  }

  private async confirm(): Promise<void> {
    const { email, confirmationCode: code } = this.loginForm().value() as LoginAndRegistrationModel;
    this.authService.confirmEmailCode(email, code).pipe(tap(() => this.login()));
  }

  private returnTo(): string {
    const value = this.activatedRoute.snapshot.queryParamMap.get('returnTo');
    if (!value || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/auth/')) {
      return '/';
    }
    return value;
  }
}
