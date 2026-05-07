import { ItemQuality } from '../models/ui-models';

const qualityClasses: Record<ItemQuality, string> = {
  common: 'text-gray-300 border-gray-500/40 bg-gray-500/10',
  uncommon: 'text-green-300 border-green-500/40 bg-green-500/10',
  rare: 'text-blue-300 border-blue-500/40 bg-blue-500/10',
  epic: 'text-purple-300 border-purple-500/40 bg-purple-500/10',
  legendary: 'text-orange-300 border-orange-500/40 bg-orange-500/10',
};

export function qualityToneClasses(quality: ItemQuality): string {
  return qualityClasses[quality];
}

export function formatQuality(quality: ItemQuality): string {
  switch (quality) {
    case 'common':
      return $localize`:@@quality.common:Common`;
    case 'uncommon':
      return $localize`:@@quality.uncommon:Uncommon`;
    case 'rare':
      return $localize`:@@quality.rare:Rare`;
    case 'epic':
      return $localize`:@@quality.epic:Epic`;
    case 'legendary':
      return $localize`:@@quality.legendary:Legendary`;
  }
}
