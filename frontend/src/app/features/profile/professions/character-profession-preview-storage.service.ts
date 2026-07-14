import { isPlatformBrowser } from '@angular/common';
import { inject, Injectable, PLATFORM_ID } from '@angular/core';

import { CharacterProfessionPreview } from '@api/generated';

const STORAGE_KEY = 'wae.character-profession-previews';
const MAX_PREVIEWS = 20;
const PREVIEW_TTL_MS = 24 * 60 * 60 * 1000;

type StoredPreview = {
  readonly preview: CharacterProfessionPreview;
  readonly cachedAt: number;
};

@Injectable({ providedIn: 'root' })
export class CharacterProfessionPreviewStorageService {
  private readonly platformId = inject(PLATFORM_ID);

  get(region: string, realmSlug: string, characterName: string): CharacterProfessionPreview | null {
    const key = previewKey(region, realmSlug, characterName);
    return this.read().find((entry) => previewKeyFor(entry.preview) === key)?.preview ?? null;
  }

  save(preview: CharacterProfessionPreview): void {
    const key = previewKeyFor(preview);
    const next = [
      { preview, cachedAt: Date.now() },
      ...this.read().filter((candidate) => previewKeyFor(candidate.preview) !== key),
    ].slice(0, MAX_PREVIEWS);
    this.write(next);
  }

  private read(): StoredPreview[] {
    if (!this.hasStorage()) return [];
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return [];
      const parsed: unknown = JSON.parse(raw);
      const now = Date.now();
      return Array.isArray(parsed)
        ? parsed.filter((entry): entry is StoredPreview => isStoredPreview(entry, now))
        : [];
    } catch {
      return [];
    }
  }

  private write(previews: readonly StoredPreview[]): void {
    if (!this.hasStorage()) return;
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(previews));
    } catch {
      // Private browsing and full browser storage should not prevent importing a character.
    }
  }

  private hasStorage(): boolean {
    return isPlatformBrowser(this.platformId) && typeof localStorage !== 'undefined';
  }
}

function previewKey(region: string, realmSlug: string, characterName: string): string {
  return [region, realmSlug, characterName].map((part) => part.trim().toLowerCase()).join('/');
}

function previewKeyFor(preview: CharacterProfessionPreview): string {
  return previewKey(preview.region, preview.realmSlug, preview.characterName);
}

function isStoredPreview(value: unknown, now: number): value is StoredPreview {
  if (!value || typeof value !== 'object') return false;
  const entry = value as Partial<StoredPreview>;
  if (
    typeof entry.cachedAt !== 'number' ||
    entry.cachedAt > now ||
    now - entry.cachedAt > PREVIEW_TTL_MS
  ) {
    return false;
  }
  const preview = entry.preview as Partial<CharacterProfessionPreview> | undefined;
  return (
    preview !== undefined &&
    typeof preview.region === 'string' &&
    typeof preview.realmSlug === 'string' &&
    typeof preview.characterName === 'string' &&
    Array.isArray(preview.professions) &&
    preview.professions.every(isProfession)
  );
}

function isProfession(value: unknown): boolean {
  if (!value || typeof value !== 'object') return false;
  const profession = value as { professionId?: unknown; professionName?: unknown; tiers?: unknown };
  return (
    typeof profession.professionId === 'number' &&
    typeof profession.professionName === 'string' &&
    Array.isArray(profession.tiers) &&
    profession.tiers.every(isTier)
  );
}

function isTier(value: unknown): boolean {
  if (!value || typeof value !== 'object') return false;
  const tier = value as {
    skillTierId?: unknown;
    skillTierName?: unknown;
    skillPoints?: unknown;
    maxSkillPoints?: unknown;
    knownRecipes?: unknown;
  };
  return (
    typeof tier.skillTierId === 'number' &&
    typeof tier.skillTierName === 'string' &&
    typeof tier.skillPoints === 'number' &&
    typeof tier.maxSkillPoints === 'number' &&
    Array.isArray(tier.knownRecipes) &&
    tier.knownRecipes.every(isRecipe)
  );
}

function isRecipe(value: unknown): boolean {
  if (!value || typeof value !== 'object') return false;
  const recipe = value as { recipeId?: unknown; recipeName?: unknown };
  return typeof recipe.recipeId === 'number' && typeof recipe.recipeName === 'string';
}
