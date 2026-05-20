import {
  buildXDomain,
  buildYDomainsByKey,
  etherealColorVar,
  indexInXDomain,
  svgContentWidthPx,
  valuesAtCategoryIndex,
} from './chart';

describe('chart helpers', () => {
  it('etherealColorVar maps token to CSS variable', () => {
    expect(etherealColorVar('primary')).toBe('var(--color-primary)');
    expect(etherealColorVar('primary-container')).toBe('var(--color-primary-container)');
  });

  it('buildXDomain returns sorted unique x from all series', () => {
    expect(buildXDomain([])).toEqual([]);
    const s = [
      {
        id: 'a',
        kind: 'line' as const,
        yScaleKey: 'p',
        color: 'primary' as const,
        points: [
          { x: 10, y: 1 },
          { x: 0, y: 2 },
        ],
      },
      {
        id: 'b',
        kind: 'column' as const,
        yScaleKey: 'q',
        color: 'tertiary' as const,
        points: [{ x: 5, y: 3 }],
      },
    ];
    expect(buildXDomain(s)).toEqual([0, 5, 10]);
  });

  it('buildYDomainsByKey groups by yScaleKey with padding', () => {
    const s = [
      {
        id: 'price',
        kind: 'line' as const,
        yScaleKey: 'price',
        color: 'primary' as const,
        points: [
          { x: 0, y: 100 },
          { x: 1, y: 200 },
        ],
      },
      {
        id: 'roi',
        kind: 'line' as const,
        yScaleKey: 'roi',
        color: 'tertiary' as const,
        points: [
          { x: 0, y: 0.1 },
          { x: 1, y: 0.2 },
        ],
      },
    ];
    const d = buildYDomainsByKey(s, 0.05);
    expect(d['price']!.min).toBeLessThan(100);
    expect(d['price']!.max).toBeGreaterThan(200);
    expect(d['roi']!.min).toBeLessThan(0.1);
    expect(d['roi']!.max).toBeGreaterThan(0.2);
  });

  it('buildYDomainsByKey handles flat series', () => {
    const s = [
      {
        id: 'flat',
        kind: 'line' as const,
        yScaleKey: 'k',
        color: 'outline' as const,
        points: [
          { x: 0, y: 5 },
          { x: 1, y: 5 },
        ],
      },
    ];
    const d = buildYDomainsByKey(s);
    expect(d['k']!.max).toBeGreaterThan(d['k']!.min);
  });

  it('indexInXDomain matches near-floats', () => {
    expect(indexInXDomain(0.1 + 0.2, [0, 0.3000000001, 1])).toBe(1);
  });

  it('svgContentWidthPx scales with categories', () => {
    expect(svgContentWidthPx(0, 12, 400)).toBe(400);
    expect(svgContentWidthPx(10, 12, 100)).toBe(120);
    expect(svgContentWidthPx(50, 8, 100)).toBe(400);
  });

  it('valuesAtCategoryIndex maps series points at category', () => {
    const series = [
      {
        id: 'a',
        kind: 'line' as const,
        yScaleKey: 'k',
        color: 'primary' as const,
        points: [
          { x: 0, y: 1 },
          { x: 1, y: 2 },
        ],
      },
      {
        id: 'b',
        kind: 'column' as const,
        yScaleKey: 'k',
        color: 'tertiary' as const,
        points: [{ x: 1, y: 99 }],
      },
    ];
    const xd = [0, 1];
    expect(valuesAtCategoryIndex(series, xd, 0)).toEqual({ a: 1, b: undefined });
    expect(valuesAtCategoryIndex(series, xd, 1)).toEqual({ a: 2, b: 99 });
  });
});
