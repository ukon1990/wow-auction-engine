import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  AuthError,
  confirmPasswordReset,
  requestPasswordReset,
  type AuthConfig,
} from './auth-session';

const awsMock = vi.hoisted(() => ({
  send: vi.fn(),
}));

vi.mock('@aws-sdk/client-cognito-identity-provider', () => {
  class MockCommand {
    constructor(readonly input: unknown) {}
  }

  class CognitoIdentityProviderClient {
    send(command: unknown): unknown {
      return awsMock.send(command);
    }
  }

  return {
    AuthFlowType: {
      USER_PASSWORD_AUTH: 'USER_PASSWORD_AUTH',
    },
    ChangePasswordCommand: MockCommand,
    CognitoIdentityProviderClient,
    ConfirmForgotPasswordCommand: MockCommand,
    ConfirmSignUpCommand: MockCommand,
    ForgotPasswordCommand: MockCommand,
    GetUserCommand: MockCommand,
    InitiateAuthCommand: MockCommand,
    SignUpCommand: MockCommand,
  };
});

describe('Cognito password reset helpers', () => {
  const config: AuthConfig = {
    clientId: 'client-123',
    hostedUiBaseUrl: 'https://example.auth.eu-west-1.amazoncognito.com',
    sessionSecret: 'secret',
  };

  beforeEach(() => {
    awsMock.send.mockReset();
  });

  it('does not reveal missing users when requesting a password reset', async () => {
    awsMock.send.mockRejectedValueOnce(namedError('UserNotFoundException'));

    await expect(requestPasswordReset({ config, email: 'missing@example.test' })).resolves.toBe(
      undefined,
    );
  });

  it('maps password reset request rate limits', async () => {
    awsMock.send.mockRejectedValueOnce(namedError('LimitExceededException'));

    await expect(
      requestPasswordReset({ config, email: 'user@example.test' }),
    ).rejects.toMatchObject({
      code: 'rate_limited',
      status: 429,
    } satisfies Partial<AuthError>);
  });

  it.each([
    ['CodeMismatchException', 'code_mismatch', 400],
    ['ExpiredCodeException', 'expired_code', 400],
    ['InvalidPasswordException', 'weak_password', 400],
    ['TooManyRequestsException', 'rate_limited', 429],
  ])('maps %s while confirming a password reset', async (name, code, status) => {
    awsMock.send.mockRejectedValueOnce(namedError(name));

    await expect(
      confirmPasswordReset({
        config,
        email: 'user@example.test',
        code: '123456',
        password: 'New-password1',
      }),
    ).rejects.toMatchObject({
      code,
      status,
    });
  });
});

function namedError(name: string): Error {
  const error = new Error(name);
  error.name = name;
  return error;
}
