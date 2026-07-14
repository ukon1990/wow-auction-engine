import {
  copperToCurrencyAmount,
  formatCopperCurrency,
  formatCurrencyAmount,
  formatCurrencyPart,
  hasCurrencyValue,
} from './currency';

describe('currency helpers', () => {
  it('formats currency parts with thousands separators', () => {
    expect(formatCurrencyPart(3450)).toBe('3,450');
  });

  it('converts copper into gold, silver and copper parts', () => {
    expect(copperToCurrencyAmount(12_345_678)).toEqual({
      gold: 1234,
      silver: 56,
      copper: 78,
    });
  });

  it('formats copper as a game currency label', () => {
    expect(formatCopperCurrency(12_345_678)).toBe('1,234g 56s 78c');
    expect(formatCurrencyAmount({ silver: 1 })).toBe('1s');
    expect(formatCopperCurrency(null)).toBe('—');
  });

  it('converts negative copper into signed currency parts', () => {
    expect(copperToCurrencyAmount(-158_500)).toEqual({
      negative: true,
      gold: 15,
      silver: 85,
    });
    expect(formatCopperCurrency(-158_500)).toBe('-15g 85s');
  });

  it('preserves zero copper as a displayable amount', () => {
    expect(copperToCurrencyAmount(0)).toEqual({ copper: 0 });
    expect(formatCopperCurrency(0)).toBe('0c');
  });

  it('detects empty currency amounts', () => {
    expect(hasCurrencyValue({})).toBe(false);
    expect(hasCurrencyValue({ gold: 1 })).toBe(true);
    expect(hasCurrencyValue({ copper: 0 })).toBe(true);
  });
});
