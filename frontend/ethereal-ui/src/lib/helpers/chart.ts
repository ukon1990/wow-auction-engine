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

/** Passed to `tooltipTemplate` as `$implicit` when hovering an x category. */
export interface ChartCategoryHoverContext {
  readonly categoryIndex: number;
  readonly x: number;
  readonly valuesBySeriesId: Readonly<Record<string, number | undefined>>;
  readonly event?: PointerEvent;
}

/** @deprecated Use `ChartCategoryHoverContext`. */
export type ChartXBandHoverContext = ChartCategoryHoverContext;

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
    if (s.kind === 'column') {
      ys.push(0);
    }
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
