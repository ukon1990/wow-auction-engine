import { formatCurrencyPart, hasCurrencyValue } from './currency';

describe('currency helpers', () => {
  it('formats currency parts with thousands separators', () => {
    expect(formatCurrencyPart(3450)).toBe('3,450');
  });

  it('detects empty currency amounts', () => {
    expect(hasCurrencyValue({})).toBe(false);
    expect(hasCurrencyValue({ gold: 1 })).toBe(true);
  });
});
