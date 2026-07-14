import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  Router,
  RouterStateSnapshot,
} from '@angular/router';
import { expect, vi } from 'vitest';

import { AuthService } from '@core/services/auth.service';

import { authenticatedGuard } from './authenticated.guard';

describe('authenticatedGuard', () => {
  const router = { createUrlTree: vi.fn() };
  const auth = { whenReady: vi.fn() };
  const route = {} as ActivatedRouteSnapshot;
  const state = { url: '/profile/professions' } as RouterStateSnapshot;
  const execute = (guard: CanActivateFn) =>
    TestBed.runInInjectionContext(() => guard(route, state));

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });
  });

  it('allows an authenticated user', async () => {
    auth.whenReady.mockResolvedValue({ email: 'mage@example.test', roles: [] });

    await expect(execute(authenticatedGuard)).resolves.toBe(true);
  });

  it('redirects an anonymous user and preserves returnTo', async () => {
    const redirect = {};
    auth.whenReady.mockResolvedValue(null);
    router.createUrlTree.mockReturnValue(redirect);

    await expect(execute(authenticatedGuard)).resolves.toBe(redirect);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/login'], {
      queryParams: { returnTo: '/profile/professions' },
    });
  });
});
