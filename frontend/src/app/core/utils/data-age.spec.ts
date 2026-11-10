import { formatDataAge } from './data-age';

describe('formatDataAge', () => {
  it('uses plain units', () => {
    expect(formatDataAge(20_000)).toBe('just now');
    expect(formatDataAge(12 * 60_000)).toBe('12 min ago');
    expect(formatDataAge(3 * 3_600_000)).toBe('3 h ago');
    expect(formatDataAge(3 * 86_400_000)).toBe('3 days ago');
  });
});
