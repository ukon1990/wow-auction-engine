import {
  AuthFlowType,
  ChangePasswordCommand,
  CognitoIdentityProviderClient,
  ConfirmForgotPasswordCommand,
  ConfirmSignUpCommand,
  ForgotPasswordCommand,
  GetUserCommand,
  InitiateAuthCommand,
  SignUpCommand,
  type AuthenticationResultType,
  type AttributeType,
} from '@aws-sdk/client-cognito-identity-provider';

import { AuthError } from './auth-error';
import {
  AuthConfig,
  PasswordAuthResult,
  SessionPayload,
  UserRole,
} from '../../app/api/auth/auth.model';
import { getUserRoleFromAccessToken } from './oauth-token-client';

export async function signUpWithPassword(input: {
  config: AuthConfig;
  email: string;
  password: string;
}): Promise<{ confirmed: boolean }> {
  try {
    const response = await cognitoClient(input.config).send(
      new SignUpCommand({
        ClientId: input.config.clientId,
        Username: input.email,
        Password: input.password,
        UserAttributes: [
          {
            Name: 'email',
            Value: input.email,
          },
        ],
      }),
    );

    return {
      confirmed: Boolean(response.UserConfirmed),
    };
  } catch (error) {
    throw mapSignUpError(error);
  }
}

export async function confirmSignUp(input: {
  config: AuthConfig;
  email: string;
  code: string;
}): Promise<void> {
  try {
    await cognitoClient(input.config).send(
      new ConfirmSignUpCommand({
        ClientId: input.config.clientId,
        Username: input.email,
        ConfirmationCode: input.code,
      }),
    );
  } catch (error) {
    throw mapConfirmSignUpError(error);
  }
}

export async function requestPasswordReset(input: {
  config: AuthConfig;
  email: string;
}): Promise<void> {
  try {
    await cognitoClient(input.config).send(
      new ForgotPasswordCommand({
        ClientId: input.config.clientId,
        Username: input.email,
      }),
    );
  } catch (error) {
    if (errorName(error) === 'UserNotFoundException') {
      return;
    }
    throw mapPasswordResetRequestError(error);
  }
}

export async function confirmPasswordReset(input: {
  config: AuthConfig;
  email: string;
  code: string;
  password: string;
}): Promise<void> {
  try {
    await cognitoClient(input.config).send(
      new ConfirmForgotPasswordCommand({
        ClientId: input.config.clientId,
        Username: input.email,
        ConfirmationCode: input.code,
        Password: input.password,
      }),
    );
  } catch (error) {
    throw mapPasswordResetConfirmError(error);
  }
}

export async function authenticateWithPassword(input: {
  config: AuthConfig;
  email: string;
  password: string;
}): Promise<PasswordAuthResult> {
  try {
    const response = await cognitoClient(input.config).send(
      new InitiateAuthCommand({
        AuthFlow: AuthFlowType.USER_PASSWORD_AUTH,
        ClientId: input.config.clientId,
        AuthParameters: {
          USERNAME: input.email,
          PASSWORD: input.password,
        },
      }),
    );

    if (response.AuthenticationResult) {
      return {
        status: 'authenticated',
        session: sessionFromAuthenticationResult(response.AuthenticationResult),
      };
    }

    return {
      status: 'challenge',
      challengeName: response.ChallengeName ?? 'UNKNOWN',
      session: response.Session,
    };
  } catch (error) {
    throw mapPasswordAuthError(error);
  }
}

export async function getUserFromAccessToken(input: {
  config: AuthConfig;
  accessToken: string;
}): Promise<{ email: string | null, roles: UserRole[] }> {
  try {
    const response = await cognitoClient(input.config).send(
      new GetUserCommand({
        AccessToken: input.accessToken,
      }),
    );
    return {
      roles: getUserRoleFromAccessToken(input.accessToken),
      email: readCognitoAttribute(response.UserAttributes ?? [], 'email'),
    };
  } catch (error) {
    throw new AuthError('current_user_unavailable', 'Unable to read current user', 502, {
      cause: error,
    });
  }
}

export async function changePassword(input: {
  config: AuthConfig;
  accessToken: string;
  previousPassword: string;
  proposedPassword: string;
}): Promise<void> {
  try {
    await cognitoClient(input.config).send(
      new ChangePasswordCommand({
        AccessToken: input.accessToken,
        PreviousPassword: input.previousPassword,
        ProposedPassword: input.proposedPassword,
      }),
    );
  } catch (error) {
    throw mapChangePasswordError(error);
  }
}

function cognitoClient(config: AuthConfig): CognitoIdentityProviderClient {
  return new CognitoIdentityProviderClient({ region: cognitoRegion(config) });
}

function cognitoRegion(config: AuthConfig): string {
  const hostedUrl = new URL(config.hostedUiBaseUrl);
  const region = hostedUrl.hostname.match(/\.auth\.([a-z0-9-]+)\.amazoncognito\.com$/)?.[1];
  if (!region) {
    throw new AuthError(
      'cognito_region_unresolved',
      'Unable to resolve Cognito region from hosted UI URL',
      500,
    );
  }
  return region;
}

function sessionFromAuthenticationResult(result: AuthenticationResultType): SessionPayload {
  if (!result.AccessToken) {
    throw new AuthError(
      'cognito_request_failed',
      'Cognito authentication response did not include tokens',
      502,
    );
  }

  return {
    accessToken: result.AccessToken,
    refreshToken: result.RefreshToken,
    expiresAt: Date.now() + (result.ExpiresIn ?? 3600) * 1000,
    roles: getUserRoleFromAccessToken(result.AccessToken),
  };
}

function mapSignUpError(error: unknown): AuthError {
  switch (errorName(error)) {
    case 'UsernameExistsException':
      return new AuthError('user_exists', 'An account already exists for this email.', 409, {
        cause: error,
      });
    case 'InvalidPasswordException':
      return new AuthError('weak_password', publicErrorMessage(error), 400, { cause: error });
    case 'TooManyRequestsException':
    case 'LimitExceededException':
      return new AuthError('rate_limited', 'Too many attempts. Please try again later.', 429, {
        cause: error,
      });
    default:
      return unknownCognitoError(error);
  }
}

function mapConfirmSignUpError(error: unknown): AuthError {
  switch (errorName(error)) {
    case 'CodeMismatchException':
      return new AuthError('code_mismatch', 'The confirmation code is incorrect.', 400, {
        cause: error,
      });
    case 'ExpiredCodeException':
      return new AuthError('expired_code', 'The confirmation code has expired.', 400, {
        cause: error,
      });
    case 'TooManyRequestsException':
    case 'LimitExceededException':
      return new AuthError('rate_limited', 'Too many attempts. Please try again later.', 429, {
        cause: error,
      });
    default:
      return unknownCognitoError(error);
  }
}

function mapPasswordResetRequestError(error: unknown): AuthError {
  switch (errorName(error)) {
    case 'TooManyRequestsException':
    case 'LimitExceededException':
      return new AuthError('rate_limited', 'Too many attempts. Please try again later.', 429, {
        cause: error,
      });
    default:
      return unknownCognitoError(error);
  }
}

function mapPasswordResetConfirmError(error: unknown): AuthError {
  switch (errorName(error)) {
    case 'CodeMismatchException':
      return new AuthError('code_mismatch', 'The confirmation code is incorrect.', 400, {
        cause: error,
      });
    case 'ExpiredCodeException':
      return new AuthError('expired_code', 'The confirmation code has expired.', 400, {
        cause: error,
      });
    case 'InvalidPasswordException':
      return new AuthError('weak_password', publicErrorMessage(error), 400, { cause: error });
    case 'TooManyRequestsException':
    case 'LimitExceededException':
      return new AuthError('rate_limited', 'Too many attempts. Please try again later.', 429, {
        cause: error,
      });
    default:
      return unknownCognitoError(error);
  }
}

function mapPasswordAuthError(error: unknown): AuthError {
  switch (errorName(error)) {
    case 'NotAuthorizedException':
    case 'UserNotFoundException':
      return new AuthError('invalid_credentials', 'Invalid email or password', 401, {
        cause: error,
      });
    case 'UserNotConfirmedException':
      return new AuthError('user_not_confirmed', 'Confirm your email before signing in.', 409, {
        cause: error,
      });
    case 'TooManyRequestsException':
    case 'LimitExceededException':
      return new AuthError('rate_limited', 'Too many attempts. Please try again later.', 429, {
        cause: error,
      });
    default:
      return unknownCognitoError(error);
  }
}

function mapChangePasswordError(error: unknown): AuthError {
  switch (errorName(error)) {
    case 'NotAuthorizedException':
      return new AuthError('current_password_incorrect', 'Current password is incorrect', 400, {
        cause: error,
      });
    case 'InvalidPasswordException':
      return new AuthError('weak_password', publicErrorMessage(error), 400, { cause: error });
    case 'TooManyRequestsException':
    case 'LimitExceededException':
      return new AuthError('rate_limited', 'Too many attempts. Please try again later.', 429, {
        cause: error,
      });
    default:
      return unknownCognitoError(error);
  }
}

function unknownCognitoError(error: unknown): AuthError {
  if (error instanceof AuthError) {
    return error;
  }
  return new AuthError('cognito_request_failed', publicErrorMessage(error), 502, { cause: error });
}

function publicErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message.trim()) {
    return error.message;
  }
  return 'Authentication request failed';
}

function errorName(error: unknown): string | null {
  if (error instanceof Error && error.name) {
    return error.name;
  }
  if (isRecord(error) && typeof error['name'] === 'string') {
    return error['name'];
  }
  return null;
}

function readCognitoAttribute(attributes: AttributeType[], name: string): string | null {
  return attributes.find((attribute) => attribute.Name === name)?.Value ?? null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}
