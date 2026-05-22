import { CanActivateFn, Router } from '@angular/router';
import { UserRole } from '@api/auth/auth.model';
import { inject } from '@angular/core';
import { AuthService } from '@core/services/auth.service';

export const userHasRoleGuard: (userRole: UserRole) => CanActivateFn =
  (userRole: UserRole) => () => {
    const service = inject(AuthService);
    const user = service.user();
    if (!userRole || !user) return false;
    const hasRole = service.user()?.roles?.includes(userRole) === true;
    const router = inject(Router);

    return hasRole ? true : router.createUrlTree(['']);
  };
