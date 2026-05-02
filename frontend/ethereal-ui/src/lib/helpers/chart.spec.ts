import {
  buildXDomain,
  buildYDomainsByKey,
  columnRectsForSeries,
  DEFAULT_PLOT_MARGINS,
  etherealColorVar,
  indexInXDomain,
  linePolylinePoints,
  svgContentWidthPx,
  valuesAtCategoryIndex,
  xBandCenterLeftPercent,
  xBandHitRects,
  xToSvg,
  yToSvg,
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

  it('xToSvg and yToSvg map to margins', () => {
    const xd = [0, 100];
    expect(xToSvg(0, xd)).toBeCloseTo(DEFAULT_PLOT_MARGINS.innerLeft);
    expect(xToSvg(100, xd)).toBeCloseTo(DEFAULT_PLOT_MARGINS.innerRight);
    const yd = { min: 0, max: 100 };
    expect(yToSvg(0, yd)).toBeCloseTo(DEFAULT_PLOT_MARGINS.innerBottom);
    expect(yToSvg(100, yd)).toBeCloseTo(DEFAULT_PLOT_MARGINS.innerTop);
  });

  it('linePolylinePoints sorts by x and joins coordinates', () => {
    const pts = [
      { x: 10, y: 0 },
      { x: 0, y: 100 },
    ];
    const xd = [0, 10];
    const yd = { min: 0, max: 100 };
    const s = linePolylinePoints(pts, xd, yd);
    expect(s.startsWith('4,')).toBe(true);
    expect(s.includes(',')).toBe(true);
    const first = s.split(' ')[0]!;
    const second = s.split(' ')[1]!;
    expect(parseFloat(first.split(',')[0]!)).toBeLessThan(parseFloat(second.split(',')[0]!));
  });

  it('columnRectsForSeries centers bars on band', () => {
    const xd = [0, 1, 2];
    const yd = { min: 0, max: 100 };
    const rects = columnRectsForSeries([{ x: 1, y: 50 }], xd, yd, 0.6);
    expect(rects.length).toBe(1);
    const r = rects[0]!;
    const innerW = DEFAULT_PLOT_MARGINS.innerRight - DEFAULT_PLOT_MARGINS.innerLeft;
    const step = innerW / 3;
    const center = DEFAULT_PLOT_MARGINS.innerLeft + (1 + 0.5) * step;
    expect(r.x + r.width / 2).toBeCloseTo(center);
    expect(r.height).toBeGreaterThan(0);
  });

  it('indexInXDomain matches near-floats', () => {
    expect(indexInXDomain(0.1 + 0.2, [0, 0.3000000001, 1])).toBe(1);
  });

  it('svgContentWidthPx scales with categories', () => {
    expect(svgContentWidthPx(0, 12, 400)).toBe(400);
    expect(svgContentWidthPx(10, 12, 100)).toBe(120);
    expect(svgContentWidthPx(50, 8, 100)).toBe(400);
  });

  it('xBandHitRects covers plot height in full-width steps', () => {
    const xd = [0, 1, 2];
    const bands = xBandHitRects(xd);
    expect(bands.length).toBe(3);
    const innerW = DEFAULT_PLOT_MARGINS.innerRight - DEFAULT_PLOT_MARGINS.innerLeft;
    expect(bands[0]!.width).toBeCloseTo(innerW / 3);
    expect(bands[0]!.y).toBe(DEFAULT_PLOT_MARGINS.innerTop);
    expect(bands[0]!.height).toBe(DEFAULT_PLOT_MARGINS.innerBottom - DEFAULT_PLOT_MARGINS.innerTop);
  });

  it('xBandCenterLeftPercent matches band center in viewBox units', () => {
    const xd = [10, 20, 30];
    expect(xBandCenterLeftPercent(1, xd.length)).toBeCloseTo(
      DEFAULT_PLOT_MARGINS.innerLeft +
        (1.5 * (DEFAULT_PLOT_MARGINS.innerRight - DEFAULT_PLOT_MARGINS.innerLeft)) / 3,
    );
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
