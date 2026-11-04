const TREND_MIN = 1200;

export function activeColumnIdsForViewport(width: number): Set<string> {
  const active = new Set<string>([
    'itemName',
    'profit',
    'reagentCost',
    'outputPrice',
    'saleRate',
    'soldPerDay',
  ]);
  if (width >= TREND_MIN) {
    active.add('roiPercent');
    active.add('outputPriceChangePercent');
  }
  return active;
}
