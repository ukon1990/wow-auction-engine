import { compareQuality, toQuality } from './quality-order';

describe('quality-order', () => {
  it('maps Blizzard quality types to item qualities', () => {
    expect(toQuality('RARE')).toBe('rare');
    expect(toQuality('ARTIFACT')).toBe('artifact');
    expect(toQuality('Selten')).toBe('common');
  });

  it('sorts qualities from common to artifact', () => {
    expect(compareQuality('epic', 'rare')).toBeGreaterThan(0);
    expect(compareQuality('common', 'legendary')).toBeLessThan(0);
    expect(compareQuality('artifact', 'legendary')).toBeGreaterThan(0);
  });
});
