export type LoginAndRegistrationModel = {
  email: string;
  password: string;
  confirmPassword: string;
  confirmationCode: string;
};

export type LoginMode = 'login' | 'signup' | 'confirm' | 'forgot' | 'reset';
