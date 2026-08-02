import { SelectInputOption } from '@ui';

function option(id: string | number, label: string): SelectInputOption {
  return { id: String(id), label };
}

export function valueString(value: number | null | undefined): string {
  return value === undefined || value === null ? '' : String(value);
}

export function mergeOptions(
  primary: readonly SelectInputOption[],
  secondary: readonly SelectInputOption[],
): SelectInputOption[] {
  const seen = new Set<string>();
  return [...primary, ...secondary].filter((item) => {
    if (seen.has(item.id)) return false;
    seen.add(item.id);
    return true;
  });
}

export function referencesToOptions(
  ...references: Array<{ id: number; name: string | null } | undefined>
): SelectInputOption[] {
  return references
    .filter((reference): reference is { id: number; name: string | null } => Boolean(reference))
    .map((reference) => option(reference.id, reference.name ?? String(reference.id)));
}

export function referencesToTypeOptions(
  ...references: Array<{ type?: string | null; name: string | null } | undefined>
): SelectInputOption[] {
  return references
    .filter((reference): reference is { type: string; name: string | null } =>
      Boolean(reference?.type),
    )
    .map((reference) => option(reference.type, reference.name ?? reference.type));
}

export function expansionReferencesToOptions(
  ...expansions: Array<{ id: number; name: string } | undefined>
): SelectInputOption[] {
  return expansions
    .filter((expansion): expansion is { id: number; name: string } => Boolean(expansion))
    .map((expansion) => option(expansion.id, expansion.name));
}

export const ITEM_CLASS_OPTIONS: readonly SelectInputOption[] = [
  option(0, 'Consumable'),
  option(1, 'Container'),
  option(2, 'Weapon'),
  option(3, 'Gem'),
  option(4, 'Armor'),
  option(5, 'Reagent'),
  option(6, 'Projectile'),
  option(7, 'Tradeskill'),
  option(9, 'Recipe'),
  option(11, 'Quiver'),
  option(12, 'Quest'),
  option(13, 'Key'),
  option(15, 'Miscellaneous'),
  option(16, 'Glyph'),
  option(17, 'Battle Pets'),
  option(19, 'WoW Token'),
];

export const ITEM_SUBCLASS_OPTIONS: Record<string, readonly SelectInputOption[]> = {
  '0': [
    option(0, 'Consumable'),
    option(1, 'Potion'),
    option(2, 'Elixir'),
    option(3, 'Flask'),
    option(4, 'Scroll'),
    option(5, 'Food & Drink'),
    option(6, 'Item Enhancement'),
    option(7, 'Bandage'),
    option(8, 'Other'),
    option(9, 'Vantus Rune'),
  ],
  '1': [
    option(0, 'Bag'),
    option(1, 'Soul Bag'),
    option(2, 'Herb Bag'),
    option(3, 'Enchanting Bag'),
    option(4, 'Engineering Bag'),
    option(5, 'Gem Bag'),
    option(6, 'Mining Bag'),
    option(7, 'Leatherworking Bag'),
    option(8, 'Inscription Bag'),
    option(9, 'Tackle Box'),
    option(10, 'Cooking Bag'),
  ],
  '2': [
    option(0, 'One-Handed Axe'),
    option(1, 'Two-Handed Axe'),
    option(2, 'Bow'),
    option(3, 'Gun'),
    option(4, 'One-Handed Mace'),
    option(5, 'Two-Handed Mace'),
    option(6, 'Polearm'),
    option(7, 'One-Handed Sword'),
    option(8, 'Two-Handed Sword'),
    option(10, 'Staff'),
    option(13, 'Fist Weapon'),
    option(14, 'Miscellaneous'),
    option(15, 'Dagger'),
    option(16, 'Thrown'),
    option(18, 'Crossbow'),
    option(19, 'Wand'),
    option(20, 'Fishing Pole'),
  ],
  '3': [
    option(0, 'Intellect'),
    option(1, 'Agility'),
    option(2, 'Strength'),
    option(3, 'Stamina'),
    option(4, 'Spirit'),
    option(5, 'Critical Strike'),
    option(6, 'Mastery'),
    option(7, 'Haste'),
    option(8, 'Versatility'),
    option(9, 'Other'),
    option(10, 'Multiple Stats'),
    option(11, 'Artifact Relic'),
  ],
  '4': [
    option(0, 'Miscellaneous'),
    option(1, 'Cloth'),
    option(2, 'Leather'),
    option(3, 'Mail'),
    option(4, 'Plate'),
    option(6, 'Shield'),
    option(7, 'Libram'),
    option(8, 'Idol'),
    option(9, 'Totem'),
    option(10, 'Sigil'),
    option(11, 'Relic'),
  ],
  '5': [option(0, 'Reagent')],
  '7': [
    option(1, 'Parts'),
    option(2, 'Explosives'),
    option(3, 'Devices'),
    option(4, 'Jewelcrafting'),
    option(5, 'Cloth'),
    option(6, 'Leather'),
    option(7, 'Metal & Stone'),
    option(8, 'Cooking'),
    option(9, 'Herb'),
    option(10, 'Elemental'),
    option(11, 'Other'),
    option(12, 'Enchanting'),
    option(16, 'Inscription'),
    option(18, 'Optional Reagents'),
    option(19, 'Finishing Reagents'),
  ],
  '9': [
    option(0, 'Book'),
    option(1, 'Leatherworking'),
    option(2, 'Tailoring'),
    option(3, 'Engineering'),
    option(4, 'Blacksmithing'),
    option(5, 'Cooking'),
    option(6, 'Alchemy'),
    option(7, 'First Aid'),
    option(8, 'Enchanting'),
    option(9, 'Fishing'),
    option(10, 'Jewelcrafting'),
    option(11, 'Inscription'),
  ],
  '12': [option(0, 'Quest')],
  '15': [
    option(0, 'Junk'),
    option(1, 'Reagent'),
    option(2, 'Companion Pets'),
    option(3, 'Holiday'),
    option(4, 'Other'),
    option(5, 'Mount'),
  ],
  '16': [
    option(1, 'Warrior'),
    option(2, 'Paladin'),
    option(3, 'Hunter'),
    option(4, 'Rogue'),
    option(5, 'Priest'),
    option(6, 'Death Knight'),
    option(7, 'Shaman'),
    option(8, 'Mage'),
    option(9, 'Warlock'),
    option(10, 'Monk'),
    option(11, 'Druid'),
    option(12, 'Demon Hunter'),
  ],
  '17': [
    option(0, 'Humanoid'),
    option(1, 'Dragonkin'),
    option(2, 'Flying'),
    option(3, 'Undead'),
    option(4, 'Critter'),
    option(5, 'Magic'),
    option(6, 'Elemental'),
    option(7, 'Beast'),
    option(8, 'Aquatic'),
    option(9, 'Mechanical'),
  ],
};

export const INVENTORY_TYPE_OPTIONS: readonly SelectInputOption[] = [
  option('NON_EQUIP', 'Non-equippable'),
  option('HEAD', 'Head'),
  option('NECK', 'Neck'),
  option('SHOULDER', 'Shoulder'),
  option('BODY', 'Shirt'),
  option('CHEST', 'Chest'),
  option('WAIST', 'Waist'),
  option('LEGS', 'Legs'),
  option('FEET', 'Feet'),
  option('WRIST', 'Wrist'),
  option('HAND', 'Hands'),
  option('FINGER', 'Finger'),
  option('TRINKET', 'Trinket'),
  option('WEAPON', 'One-hand'),
  option('SHIELD', 'Off-hand shield'),
  option('RANGED', 'Ranged'),
  option('CLOAK', 'Back'),
  option('TWOHWEAPON', 'Two-hand'),
  option('BAG', 'Bag'),
  option('TABARD', 'Tabard'),
  option('ROBE', 'Robe'),
  option('WEAPONMAINHAND', 'Main hand'),
  option('WEAPONOFFHAND', 'Off hand'),
  option('HOLDABLE', 'Held in off-hand'),
  option('AMMO', 'Ammo'),
  option('THROWN', 'Thrown'),
  option('RANGEDRIGHT', 'Ranged right'),
  option('QUIVER', 'Quiver'),
  option('RELIC', 'Relic'),
];

export const BINDING_TYPE_OPTIONS: readonly SelectInputOption[] = [
  option('NONE', 'Not bound'),
  option('ON_ACQUIRE', 'Binds when picked up'),
  option('ON_EQUIP', 'Binds when equipped'),
  option('ON_USE', 'Binds when used'),
  option('QUEST', 'Quest item'),
  option('ACCOUNT', 'Account bound'),
  option('BNET_ACCOUNT', 'Battle.net account bound'),
];
