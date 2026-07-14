import { CurrencyAmount } from '../models/ui-models';

export function copperToCurrencyAmount(copper: number | null | undefined): CurrencyAmount {
  if (copper == null || !Number.isFinite(copper)) return {};
  if (copper === 0) return { copper: 0 };
  const negative = copper < 0;
  const value = Math.abs(Math.round(copper));
  return {
    negative: negative || undefined,
    gold: Math.floor(value / 10_000) || undefined,
    silver: Math.floor((value % 10_000) / 100) || undefined,
    copper: value % 100 || undefined,
  };
}

export function hasCurrencyValue(
  amount: CurrencyAmount | null | undefined,
): amount is CurrencyAmount {
  if (!amount) return false;
  return amount.gold != null || amount.silver != null || amount.copper != null;
}

export function formatCurrencyPart(value: number | null | undefined): string {
  return (value ?? 0).toLocaleString('en-US');
}

export function formatCurrencyAmount(amount: CurrencyAmount | null | undefined): string {
  if (!hasCurrencyValue(amount)) return '—';
  const prefix = amount.negative ? '-' : '';
  return (
    prefix +
    [
      amount.gold != null ? `${formatCurrencyPart(amount.gold)}g` : '',
      amount.silver != null ? `${formatCurrencyPart(amount.silver)}s` : '',
      amount.copper != null ? `${formatCurrencyPart(amount.copper)}c` : '',
    ]
      .filter(Boolean)
      .join(' ')
  );
}

export function formatCopperCurrency(copper: number | null | undefined): string {
  return formatCurrencyAmount(copperToCurrencyAmount(copper));
}
