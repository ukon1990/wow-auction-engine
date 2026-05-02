/** Matches `--color-*` in `frontend/src/styles.css` @theme. */
export type EtherealColorToken =
  | 'background'
  | 'surface'
  | 'surface-dim'
  | 'surface-bright'
  | 'surface-container-lowest'
  | 'surface-container-low'
  | 'surface-container'
  | 'surface-container-high'
  | 'surface-container-highest'
  | 'on-surface'
  | 'on-surface-variant'
  | 'outline'
  | 'outline-variant'
  | 'primary'
  | 'on-primary'
  | 'primary-container'
  | 'secondary'
  | 'secondary-container'
  | 'tertiary'
  | 'tertiary-container'
  | 'error';

export interface ChartPoint {
  readonly x: number;
  readonly y: number;
}

export type ChartSeriesKind = 'line' | 'column';

export interface ChartSeries {
  readonly id: string;
  readonly kind: ChartSeriesKind;
  readonly yScaleKey: string;
  readonly color: EtherealColorToken;
  readonly points: readonly ChartPoint[];
}

export interface YDomain {
  readonly min: number;
  readonly max: number;
}

export interface ColumnRect {
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
}

export interface XBandHitRect {
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
}

/** Passed to `tooltipTemplate` as `$implicit` when hovering an x category. */
export interface ChartXBandHoverContext {
  readonly categoryIndex: number;
  readonly x: number;
  readonly valuesBySeriesId: Readonly<Record<string, number | undefined>>;
}

const DEFAULT_PADDING = 0.05;

export function etherealColorVar(token: EtherealColorToken): string {
  return `var(--color-${token})`;
}

export function buildXDomain(series: readonly ChartSeries[]): readonly number[] {
  const xs = new Set<number>();
  for (const s of series) {
    for (const p of s.points) {
      xs.add(p.x);
    }
  }
  return [...xs].sort((a, b) => a - b);
}

export function buildYDomainsByKey(
  series: readonly ChartSeries[],
  paddingFraction: number = DEFAULT_PADDING,
): Record<string, YDomain> {
  const byKey = new Map<string, number[]>();
  for (const s of series) {
    const ys = byKey.get(s.yScaleKey) ?? [];
    for (const p of s.points) {
      ys.push(p.y);
    }
    byKey.set(s.yScaleKey, ys);
  }
  const out: Record<string, YDomain> = {};
  for (const [key, values] of byKey) {
    if (values.length === 0) {
      continue;
    }
    const rawMin = Math.min(...values);
    const rawMax = Math.max(...values);
    const span = rawMax - rawMin;
    const pad = span > 0 ? span * paddingFraction : Math.abs(rawMin) * paddingFraction || 1;
    if (span === 0) {
      out[key] = { min: rawMin - pad, max: rawMax + pad };
    } else {
      out[key] = { min: rawMin - pad, max: rawMax + pad };
    }
  }
  return out;
}

export interface PlotMargins {
  readonly innerLeft: number;
  readonly innerRight: number;
  readonly innerTop: number;
  readonly innerBottom: number;
}

export const DEFAULT_PLOT_MARGINS: PlotMargins = {
  innerLeft: 4,
  innerRight: 96,
  innerTop: 5,
  innerBottom: 95,
};

export function xToSvg(
  x: number,
  xDomain: readonly number[],
  margins: PlotMargins = DEFAULT_PLOT_MARGINS,
): number {
  if (xDomain.length === 0) {
    return margins.innerLeft;
  }
  const xMin = xDomain[0]!;
  const xMax = xDomain[xDomain.length - 1]!;
  const spread = xMax - xMin || 1;
  const innerWidth = margins.innerRight - margins.innerLeft;
  return margins.innerLeft + ((x - xMin) / spread) * innerWidth;
}

/** Higher data `y` maps toward smaller SVG `y` (top of chart). */
export function yToSvg(
  y: number,
  domain: YDomain,
  margins: PlotMargins = DEFAULT_PLOT_MARGINS,
): number {
  const spread = domain.max - domain.min || 1;
  const t = (y - domain.min) / spread;
  return margins.innerBottom - t * (margins.innerBottom - margins.innerTop);
}

export function linePolylinePoints(
  points: readonly ChartPoint[],
  xDomain: readonly number[],
  yDomain: YDomain,
  margins: PlotMargins = DEFAULT_PLOT_MARGINS,
): string {
  if (points.length === 0 || xDomain.length === 0) {
    return '';
  }
  const sorted = [...points].sort((a, b) => a.x - b.x);
  return sorted
    .map((p) => {
      const sx = xToSvg(p.x, xDomain, margins);
      const sy = yToSvg(p.y, yDomain, margins);
      return `${sx},${sy}`;
    })
    .join(' ');
}

export function indexInXDomain(x: number, xDomain: readonly number[]): number {
  const exact = xDomain.indexOf(x);
  if (exact >= 0) {
    return exact;
  }
  return xDomain.findIndex((v) => Math.abs(v - x) < 1e-9);
}

/** Y value per series at the x category (column index in `xDomain`). */
export function valuesAtCategoryIndex(
  series: readonly ChartSeries[],
  xDomain: readonly number[],
  categoryIndex: number,
): Readonly<Record<string, number | undefined>> {
  const out: Record<string, number | undefined> = {};
  for (const s of series) {
    const j = s.points.findIndex((p) => indexInXDomain(p.x, xDomain) === categoryIndex);
    out[s.id] = j >= 0 ? s.points[j]!.y : undefined;
  }
  return out;
}

export function columnRectsForSeries(
  points: readonly ChartPoint[],
  xDomain: readonly number[],
  yDomain: YDomain,
  bandWidthFraction: number,
  margins: PlotMargins = DEFAULT_PLOT_MARGINS,
): readonly ColumnRect[] {
  if (xDomain.length === 0) {
    return [];
  }
  const n = xDomain.length;
  const innerWidth = margins.innerRight - margins.innerLeft;
  const step = innerWidth / n;
  const barW = step * bandWidthFraction;
  const rects: ColumnRect[] = [];
  for (const p of points) {
    const xi = indexInXDomain(p.x, xDomain);
    if (xi < 0) {
      continue;
    }
    const center = margins.innerLeft + (xi + 0.5) * step;
    const x = center - barW / 2;
    const topY = yToSvg(p.y, yDomain, margins);
    const baseY = margins.innerBottom;
    const h = Math.max(0, baseY - topY);
    rects.push({ x, y: topY, width: barW, height: h });
  }
  return rects;
}

/** Full-height bands for x categories (pointer hit targets + highlight geometry). */
export function xBandHitRects(
  xDomain: readonly number[],
  margins: PlotMargins = DEFAULT_PLOT_MARGINS,
): readonly XBandHitRect[] {
  if (xDomain.length === 0) {
    return [];
  }
  const n = xDomain.length;
  const innerWidth = margins.innerRight - margins.innerLeft;
  const step = innerWidth / n;
  const h = margins.innerBottom - margins.innerTop;
  return xDomain.map((_, i) => ({
    x: margins.innerLeft + i * step,
    y: margins.innerTop,
    width: step,
    height: h,
  }));
}

/** Horizontal center of band `categoryIndex` as % of SVG width (viewBox width 100). */
export function xBandCenterLeftPercent(
  categoryIndex: number,
  xDomainLength: number,
  margins: PlotMargins = DEFAULT_PLOT_MARGINS,
): number {
  if (xDomainLength <= 0 || categoryIndex < 0 || categoryIndex >= xDomainLength) {
    return 0;
  }
  const innerWidth = margins.innerRight - margins.innerLeft;
  const step = innerWidth / xDomainLength;
  const cx = margins.innerLeft + (categoryIndex + 0.5) * step;
  return cx;
}

export function svgContentWidthPx(
  xCategoryCount: number,
  minPixelsPerCategory: number,
  minWidthPx: number,
): number {
  if (xCategoryCount <= 0) {
    return minWidthPx;
  }
  return Math.max(minWidthPx, xCategoryCount * minPixelsPerCategory);
}
