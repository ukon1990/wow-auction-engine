import { formatQuality, qualityToneClasses } from './quality';

describe('quality helpers', () => {
  it('formats quality labels', () => {
    expect(formatQuality('epic')).toBe('Epic');
  });

  it('returns a stable tone class for each quality', () => {
    expect(qualityToneClasses('rare')).toContain('text-blue-300');
  });
});
