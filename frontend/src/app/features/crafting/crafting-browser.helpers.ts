const PROF_MIN = 1040;
const TREND_MIN = 1200;

export function activeColumnIdsForViewport(width: number): Set<string> {
  const active = new Set<string>(['itemName', 'outputPrice', 'profit', 'saleRate', 'soldPerDay']);
  if (width >= PROF_MIN) active.add('professionName');
  active.add('reagentCost');
  if (width >= TREND_MIN) {
    active.add('roiPercent');
    active.add('outputPriceChangePercent');
  }
  return active;
}
