export type AuthConfig = {
  clientId: string;
  hostedUiBaseUrl: string;
  sessionSecret: string;
};

export type SessionPayload = {
  accessToken: string;
  idToken?: string;
  refreshToken?: string;
  expiresAt: number;
};

export type OAuthStatePayload = {
  state: string;
  codeVerifier: string;
  returnTo: string;
};

export type PasswordAuthResult =
  | {
      status: 'authenticated';
      session: SessionPayload;
    }
  | {
      status: 'challenge';
      challengeName: string;
      session?: string;
    };

export type AuthErrorCode =
  | 'auth_not_configured'
  | 'invalid_request'
  | 'invalid_credentials'
  | 'user_not_confirmed'
  | 'unsupported_challenge'
  | 'user_exists'
  | 'weak_password'
  | 'code_mismatch'
  | 'expired_code'
  | 'current_password_incorrect'
  | 'session_required'
  | 'invalid_callback'
  | 'current_user_unavailable'
  | 'rate_limited'
  | 'cognito_region_unresolved'
  | 'cognito_request_failed'
  | 'token_exchange_failed'
  | 'unknown';

export type AuthErrorResponse = {
  error: string;
  code: AuthErrorCode;
};

export type AuthLoginResponse = {
  authenticated: true;
};

export type AuthSignupResponse = {
  confirmed: boolean;
  email: string;
};

export type AuthConfirmResponse = {
  confirmed: true;
};

export type AuthForgotPasswordResponse = {
  requested: true;
};

export type AuthResetPasswordResponse = {
  reset: true;
};

export type AuthMeResponse =
  | {
      authenticated: true;
      email: string | null;
    }
  | {
      authenticated: false;
    };

export type AuthChangePasswordResponse = {
  changed: true;
};
