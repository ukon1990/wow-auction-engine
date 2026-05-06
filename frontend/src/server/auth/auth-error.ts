import type { AuthErrorCode, AuthErrorResponse } from '../../app/api/auth/auth.model';

export class AuthError extends Error {
  constructor(
    readonly code: AuthErrorCode,
    message: string,
    readonly status = 400,
    options?: ErrorOptions,
  ) {
    super(message, options);
    this.name = 'AuthError';
  }
}

export function authErrorResponse(
  error: unknown,
  fallback: { code: AuthErrorCode; message: string },
): AuthErrorResponse {
  if (error instanceof AuthError) {
    return {
      error: error.message,
      code: error.code,
    };
  }

  return {
    error: fallback.message,
    code: fallback.code,
  };
}

export function authErrorStatus(error: unknown, fallbackStatus: number): number {
  return error instanceof AuthError ? error.status : fallbackStatus;
}
