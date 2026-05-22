import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { UserRole } from '@api/auth/auth.model';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  const httpMock = {
    get: vi.fn(),
    post: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [AuthService, { provide: HttpClient, useValue: httpMock }],
    });
  });

  it('reuses the in-flight /auth/me request for concurrent whenReady callers', async () => {
    httpMock.get.mockReturnValue(
      of({
        authenticated: true,
        email: 'admin@example.com',
        roles: [UserRole.Admin],
      }),
    );
    const auth = TestBed.inject(AuthService);

    const [first, second] = await Promise.all([auth.whenReady(), auth.whenReady()]);

    expect(first?.roles).toEqual([UserRole.Admin]);
    expect(second).toEqual(first);
    expect(httpMock.get).toHaveBeenCalledTimes(1);
  });

  it('force refresh fetches /auth/me again after an earlier unauthenticated result', async () => {
    httpMock.get
      .mockReturnValueOnce(of({ authenticated: false }))
      .mockReturnValueOnce(
        of({
          authenticated: true,
          email: 'admin@example.com',
          roles: [UserRole.Admin],
        }),
      );
    const auth = TestBed.inject(AuthService);

    await expect(auth.whenReady()).resolves.toBeNull();
    await expect(auth.refresh({ force: true })).resolves.toMatchObject({
      email: 'admin@example.com',
      roles: [UserRole.Admin],
    });
    expect(httpMock.get).toHaveBeenCalledTimes(2);
  });

  it('treats /auth/me failures as signed out', async () => {
    httpMock.get.mockReturnValue(throwError(() => new Error('network')));
    const auth = TestBed.inject(AuthService);

    await expect(auth.whenReady()).resolves.toBeNull();
    expect(auth.loaded()).toBe(true);
    expect(auth.user()).toBeNull();
  });
});
