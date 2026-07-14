import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '@core/services/auth.service';

/** Sends anonymous visitors through sign-in while retaining the requested route. */
export const authenticatedGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return (await auth.whenReady())
    ? true
    : router.createUrlTree(['/login'], { queryParams: { returnTo: state.url } });
};
