/**
 * Addon entry definitions often report one rank above the spendable cap
 * (for example 31 vs 30 spendable points on a Midnight specialization node).
 */
export function normalizeSpendableLimit(limit: number): number {
  if (limit > 1 && limit % 10 === 1) return limit - 1;
  return limit;
}
