import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  Router,
  RouterStateSnapshot,
} from '@angular/router';

import { userHasRoleGuard } from './user-has-role-guard';
import { UserRole } from '@api/auth/auth.model';
import { expect, vi } from 'vitest';
import { AuthService, AuthUser } from '@core/services/auth.service';

describe('userHasRoleGuard', () => {
  const executeGuard: CanActivateFn = (...guardParameters) =>
    TestBed.runInInjectionContext(() => userHasRoleGuard(UserRole.Admin)(...guardParameters));

  const authServiceMock = {
    user: vi.fn(),
    whenReady: vi.fn(),
  };

  const route = {
    data: {},
    params: {},
    queryParams: {},
  } as unknown as ActivatedRouteSnapshot;

  const routerMock = {
    createUrlTree: vi.fn(),
  };

  const state = {
    url: '/admin',
  } as RouterStateSnapshot;
  const getAuthUserWithRoles = (roles: UserRole[]): Promise<AuthUser> =>
    Promise.resolve({
      roles,
      email: 'example@example.com',
    });

  beforeEach(() => {
    vi.clearAllMocks();
    routerMock.createUrlTree = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceMock },
        { provide: Router, useValue: routerMock },
      ],
    });
  });

  it('should be created', () => {
    expect(executeGuard).toBeTruthy();
  });

  it('should be true if the user has the required role', async () => {
    authServiceMock.whenReady.mockReturnValue(getAuthUserWithRoles([UserRole.Admin]));
    await expect(executeGuard(route, state)).resolves.toBeTruthy();
    expect(routerMock.createUrlTree).not.toHaveBeenCalled();
  });

  it('should be false if the user does not have the required role', async () => {
    authServiceMock.whenReady.mockReturnValue(getAuthUserWithRoles([]));
    await expect(executeGuard(route, state)).resolves.toBeFalsy();
    expect(routerMock.createUrlTree).toHaveBeenCalledOnce();
    expect(routerMock.createUrlTree).toHaveBeenCalledWith(['/login'], {
      queryParams: { returnTo: '/admin' },
    });
  });
});
