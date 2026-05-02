import { CurrencyAmount } from '../models/ui-models';

export function hasCurrencyValue(amount: CurrencyAmount): boolean {
  return Boolean(amount.gold || amount.silver || amount.copper);
}

export function formatCurrencyPart(value: number | undefined): string {
  return (value ?? 0).toLocaleString('en-US');
}
