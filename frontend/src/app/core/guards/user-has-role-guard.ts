import { CanActivateFn, Router } from '@angular/router';
import { UserRole } from '@api/auth/auth.model';
import { inject } from '@angular/core';
import { AuthService } from '@core/services/auth.service';

export const userHasRoleGuard =
  (userRole: UserRole): CanActivateFn =>
  async (_route, state) => {
    const auth = inject(AuthService);
    const router = inject(Router);

    const user = await auth.whenReady();

    return user?.roles.includes(userRole)
      ? true
      : router.createUrlTree(['/login'], {
          queryParams: { returnUrl: state.url },
        });
  };
