import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { PageFrameComponent, SkeletonDirective } from '@ui';

import { AuthService } from '@core/services/auth.service';
import {
  validatePasswordMatch,
  validatePasswordRules,
  validateRequiredPassword,
} from '@core/utils/auth-validation';

@Component({
  selector: 'app-profile-page',
  imports: [FormsModule, PageFrameComponent, SkeletonDirective],
  templateUrl: './profile.page.html',
  host: {
    class: 'flex min-h-0 flex-1 flex-col',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfilePage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly user = this.auth.user;
  protected readonly loading = signal(true);
  protected readonly currentPassword = signal('');
  protected readonly newPassword = signal('');
  protected readonly confirmPassword = signal('');
  protected readonly currentPasswordTouched = signal(false);
  protected readonly newPasswordTouched = signal(false);
  protected readonly confirmPasswordTouched = signal(false);
  protected readonly changingPassword = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly currentPasswordLabel = $localize`:@@profile.currentPassword:Current password`;
  protected readonly newPasswordLabel = $localize`:@@login.newPassword:New password`;
  protected readonly confirmNewPasswordLabel = $localize`:@@login.confirmNewPassword:Confirm new password`;

  protected readonly currentPasswordError = computed(() =>
    this.currentPasswordTouched()
      ? validateRequiredPassword(this.currentPassword(), this.currentPasswordLabel)
      : null,
  );
  protected readonly newPasswordError = computed(() =>
    this.newPasswordTouched()
      ? validatePasswordRules(this.newPassword(), this.newPasswordLabel)
      : null,
  );
  protected readonly confirmPasswordError = computed(() =>
    this.confirmPasswordTouched()
      ? validatePasswordMatch(
          this.newPassword(),
          this.confirmPassword(),
          $localize`:@@profile.validation.newPasswordsMatch:New passwords do not match`,
        )
      : null,
  );

  protected unknownLabel(): string {
    return $localize`:@@profile.unknown:Unknown`;
  }

  constructor() {
    afterNextRender(() => {
      void this.loadUser();
    });
  }

  protected async changePassword(): Promise<void> {
    this.error.set(null);
    this.notice.set(null);
    const validationError = this.validatePasswordChange();
    if (validationError) {
      this.error.set(validationError);
      return;
    }

    this.changingPassword.set(true);
    try {
      await requestAuth('/auth/change-password', {
        currentPassword: this.currentPassword(),
        newPassword: this.newPassword(),
      });
      this.currentPassword.set('');
      this.newPassword.set('');
      this.confirmPassword.set('');
      this.notice.set($localize`:@@profile.notice.passwordUpdated:Password updated.`);
    } catch (error) {
      this.error.set(
        error instanceof Error
          ? error.message
          : $localize`:@@profile.error.changePassword:Unable to change password`,
      );
    } finally {
      this.changingPassword.set(false);
    }
  }

  protected signOut(): void {
    this.auth.setSignedOut();
    window.location.assign('/auth/logout');
  }

  private async loadUser(): Promise<void> {
    const user = await this.auth.whenReady();
    this.loading.set(false);
    if (!user) {
      await this.router.navigate(['/login'], {
        queryParams: {
          returnTo: '/profile',
        },
      });
    }
  }

  private validatePasswordChange(): string | null {
    return (
      validateRequiredPassword(this.currentPassword(), this.currentPasswordLabel) ??
      validatePasswordRules(this.newPassword(), this.newPasswordLabel) ??
      validatePasswordMatch(
        this.newPassword(),
        this.confirmPassword(),
        $localize`:@@profile.validation.newPasswordsMatch:New passwords do not match`,
      )
    );
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
    throw new Error(
      payload.error ?? $localize`:@@profile.error.authRequest:Authentication request failed`,
    );
  }
  return payload;
}
