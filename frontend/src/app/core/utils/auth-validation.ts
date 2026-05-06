export type PasswordRule = {
  readonly id: string;
  readonly message: string;
  readonly test: (value: string) => boolean;
};

export const passwordRules: readonly PasswordRule[] = [
  {
    id: 'length',
    message: 'Password must be at least 8 characters',
    test: (value) => value.length >= 8,
  },
  {
    id: 'lowercase',
    message: 'Password must include a lowercase letter',
    test: (value) => /[a-z]/.test(value),
  },
  {
    id: 'uppercase',
    message: 'Password must include an uppercase letter',
    test: (value) => /[A-Z]/.test(value),
  },
  {
    id: 'number',
    message: 'Password must include a number',
    test: (value) => /\d/.test(value),
  },
  {
    id: 'symbol',
    message: 'Password must include a symbol',
    test: (value) => /[^A-Za-z0-9]/.test(value),
  },
];

export function validateEmail(value: string): string | null {
  const email = value.trim();
  if (!email) {
    return 'Email is required';
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return 'Enter a valid email address';
  }
  return null;
}

export function validateRequiredPassword(value: string, label = 'Password'): string | null {
  return value ? null : `${label} is required`;
}

export function validatePasswordRules(value: string, label = 'Password'): string | null {
  const requiredError = validateRequiredPassword(value, label);
  if (requiredError) {
    return requiredError;
  }
  const failedRule = passwordRules.find((rule) => !rule.test(value));
  return failedRule?.message.replace('Password', label) ?? null;
}

export function validatePasswordMatch(
  password: string,
  confirmation: string,
  message = 'Passwords do not match',
): string | null {
  if (!confirmation) {
    return 'Confirm password is required';
  }
  return password === confirmation ? null : message;
}
