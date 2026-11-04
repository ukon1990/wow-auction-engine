/** Formats how old data is, in plain words: "just now", "12 min ago", "3 h ago", "2 days ago". */
export function formatDataAge(ageMs: number): string {
  const minutes = Math.max(0, Math.floor(ageMs / 60_000));
  if (minutes < 1) return $localize`:@@dataAge.justNow:just now`;
  if (minutes < 60) return $localize`:@@dataAge.minutes:${minutes}:count: min ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 48) return $localize`:@@dataAge.hours:${hours}:count: h ago`;
  const days = Math.floor(hours / 24);
  return $localize`:@@dataAge.days:${days}:count: days ago`;
}
