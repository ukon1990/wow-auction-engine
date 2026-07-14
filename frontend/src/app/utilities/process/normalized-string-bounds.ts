export const NORMALIZED_TALENT_NAME_MAX_LENGTH = 128;
export const NORMALIZED_TALENT_DESCRIPTION_MAX_LENGTH = 4096;

export function boundedName(value: string | null | undefined): string | undefined {
  const trimmed = value?.trim();
  if (!trimmed) return undefined;
  return trimmed.length <= NORMALIZED_TALENT_NAME_MAX_LENGTH
    ? trimmed
    : trimmed.slice(0, NORMALIZED_TALENT_NAME_MAX_LENGTH);
}

export function boundedDescription(value: string | null | undefined): string | undefined {
  const trimmed = value?.trim();
  if (!trimmed) return undefined;
  return trimmed.length <= NORMALIZED_TALENT_DESCRIPTION_MAX_LENGTH
    ? trimmed
    : trimmed.slice(0, NORMALIZED_TALENT_DESCRIPTION_MAX_LENGTH);
}
