export type PasswordRule = {
  readonly id: string;
  readonly message: string;
  readonly test: (value: string) => boolean;
};

export const passwordRules: readonly PasswordRule[] = [
  {
    id: 'length',
    message: $localize`:@@auth.password.length:Password must be at least 8 characters`,
    test: (value) => value.length >= 8,
  },
  {
    id: 'lowercase',
    message: $localize`:@@auth.password.lowercase:Password must include a lowercase letter`,
    test: (value) => /[a-z]/.test(value),
  },
  {
    id: 'uppercase',
    message: $localize`:@@auth.password.uppercase:Password must include an uppercase letter`,
    test: (value) => /[A-Z]/.test(value),
  },
  {
    id: 'number',
    message: $localize`:@@auth.password.number:Password must include a number`,
    test: (value) => /\d/.test(value),
  },
  {
    id: 'symbol',
    message: $localize`:@@auth.password.symbol:Password must include a symbol`,
    test: (value) => /[^A-Za-z0-9]/.test(value),
  },
];

export function validateEmail(value: string): string | null {
  const email = value.trim();
  if (!email) {
    return $localize`:@@login.validation.email:Email is required`;
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return $localize`:@@auth.email.invalid:Enter a valid email address`;
  }
  return null;
}

export function validateRequiredPassword(
  value: string,
  label = $localize`:@@login.password:Password`,
): string | null {
  return value ? null : $localize`:@@auth.password.required:${label} is required`;
}

export function validatePasswordRules(
  value: string,
  label = $localize`:@@login.password:Password`,
): string | null {
  const requiredError = validateRequiredPassword(value, label);
  if (requiredError) {
    return requiredError;
  }
  const failedRule = passwordRules.find((rule) => !rule.test(value));
  return failedRule?.message.replace($localize`:@@login.password:Password`, label) ?? null;
}

export function validatePasswordMatch(
  password: string,
  confirmation: string,
  message = $localize`:@@auth.password.match:Passwords do not match`,
): string | null {
  if (!confirmation) {
    return $localize`:@@login.validation.confirmPassword:Confirm password is required`;
  }
  return password === confirmation ? null : message;
}
