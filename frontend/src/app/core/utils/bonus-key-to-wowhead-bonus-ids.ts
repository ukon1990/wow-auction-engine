/**
 * Parses Blizzard auction `bonusKey` strings into Wowhead tooltip `bonus=` id list.
 * Keys are often colon-separated decimal ids (e.g. "6652:1472") or a single id.
 */
export function bonusKeyToWowheadBonusIds(bonusKey: string | null | undefined): readonly number[] {
  if (bonusKey == null) return [];
  const trimmedKey = String(bonusKey).trim();
  if (!trimmedKey.length) return [];
  const parts = trimmedKey.split(':');
  const bonusIds: number[] = [];
  for (const part of parts) {
    const bonusId = Number.parseInt(part, 10);
    if (Number.isFinite(bonusId) && bonusId > 0) bonusIds.push(bonusId);
  }
  return bonusIds;
}
