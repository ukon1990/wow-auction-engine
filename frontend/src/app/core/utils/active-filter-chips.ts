import { ActiveFilterChip, FilterSection } from '@ui';

/** Range filters whose values are copper amounts. */
const COPPER_RANGES = new Set(['price', 'profit', 'reagentCost', 'outputPrice']);
/** Range filters whose values are percentages. */
const PERCENT_RANGES = new Set(['saleRatePercent', 'roiPercent', 'outputPriceChangePercent']);

export type ParsedChipId =
  | { readonly kind: 'option'; readonly sectionId: string; readonly optionId: string }
  | { readonly kind: 'select'; readonly sectionId: string }
  | { readonly kind: 'range'; readonly sectionId: string };

/** Builds one chip per active filter, in the order the filter panel shows them. */
export function toActiveFilterChips(sections: readonly FilterSection[]): ActiveFilterChip[] {
  const chips: ActiveFilterChip[] = [];
  for (const section of sections) {
    if (section.type === 'range') {
      const label = rangeChipLabel(section);
      if (label) chips.push({ id: `range|${section.id}`, label });
      continue;
    }
    for (const option of section.options) {
      if (option.selected) chips.push(optionChip(section, option));
    }
  }
  return chips;
}

function optionChip(
  section: FilterSection,
  option: FilterSection['options'][number],
): ActiveFilterChip {
  if (section.type === 'boolean') {
    return { id: `option|${section.id}|${option.id}`, label: section.label };
  }
  if (section.type === 'select') {
    return { id: `select|${section.id}`, label: `${section.label}: ${option.label}` };
  }
  return { id: `option|${section.id}|${option.id}`, label: `${section.label}: ${option.label}` };
}

export function parseActiveFilterChipId(id: string): ParsedChipId | null {
  const [kind, sectionId, ...rest] = id.split('|');
  if (!sectionId) return null;
  if (kind === 'range') return { kind, sectionId };
  if (kind === 'select') return { kind, sectionId };
  if (kind === 'option' && rest.length) return { kind, sectionId, optionId: rest.join('|') };
  return null;
}

function rangeChipLabel(section: FilterSection): string | null {
  const min = section.selectedMin;
  const max = section.selectedMax;
  if (min == null && max == null) return null;
  const format = (value: number) => formatRangeValue(section.id, value);
  if (min != null && max != null) return `${section.label} ${format(min)}–${format(max)}`;
  if (min != null) return `${section.label} ≥ ${format(min)}`;
  return `${section.label} ≤ ${format(max as number)}`;
}

/** Shows copper as gold/silver/copper ("12g 50s") and percentages with a % sign. */
export function formatRangeValue(sectionId: string, value: number): string {
  if (COPPER_RANGES.has(sectionId)) return formatCopper(value);
  if (PERCENT_RANGES.has(sectionId)) return `${value}%`;
  return String(value);
}

export function formatCopper(copper: number): string {
  const sign = copper < 0 ? '−' : '';
  const total = Math.abs(Math.round(copper));
  const gold = Math.floor(total / 10_000);
  const silver = Math.floor((total % 10_000) / 100);
  const rest = total % 100;
  const parts = [
    gold ? `${gold.toLocaleString('en-US')}g` : '',
    silver ? `${silver}s` : '',
    rest || total === 0 ? `${rest}c` : '',
  ].filter(Boolean);
  return sign + parts.join(' ');
}
