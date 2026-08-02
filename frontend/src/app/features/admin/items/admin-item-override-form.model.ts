import { AdminItem1, AdminItemFields, AdminItemOverrideRequest, GameLocale } from '@api/generated';
import { valueString } from './admin-item-override-form.options';

export interface ItemOverrideFormValues {
  nameOverride: boolean;
  nameLocales: GameLocale;
  qualityOverride: boolean;
  qualityType: string;
  levelOverride: boolean;
  level: string;
  requiredLevelOverride: boolean;
  requiredLevel: string;
  itemClassId: string;
  itemSubclassId: string;
  inventoryType: string;
  bindingType: string;
  mediaUrlOverride: boolean;
  mediaUrl: string;
  mediaSourceUrlOverride: boolean;
  mediaSourceUrl: string;
  purchasePriceOverride: boolean;
  purchasePrice: string;
  sellPriceOverride: boolean;
  sellPrice: string;
  maxCountOverride: boolean;
  maxCount: string;
  equippableOverride: boolean;
  isEquippable: string;
  stackableOverride: boolean;
  isStackable: string;
  purchaseQuantityOverride: boolean;
  purchaseQuantity: string;
  expansionId: string;
  overrideNote: string;
}

export type ItemOverrideRequestResult =
  | { request: AdminItemOverrideRequest; validationError: null }
  | { request: null; validationError: string };

class NonNegativeIntegerParser {
  validationError: string | null = null;

  parseOptional(value: string, label: string): number | null {
    const trimmedValue = value.trim();
    if (trimmedValue.length === 0) return null;
    const parsedValue = Number.parseInt(trimmedValue, 10);
    if (!Number.isFinite(parsedValue) || parsedValue < 0) {
      this.validationError = `${label} must be a non-negative number.`;
      return null;
    }
    return parsedValue;
  }

  numberField(enabled: boolean, value: string, label: string): number | null {
    return enabled ? this.parseOptional(value, label) : null;
  }
}

export function createItemOverrideRequest(
  values: ItemOverrideFormValues,
  item: AdminItem1,
): ItemOverrideRequestResult {
  const parser = new NonNegativeIntegerParser();
  const itemClassId = parser.parseOptional(values.itemClassId, 'Class ID');
  const itemSubclassId = parser.parseOptional(values.itemSubclassId, 'Subclass ID');
  if (parser.validationError) return { request: null, validationError: parser.validationError };
  if (itemClassId !== null && itemSubclassId === null) {
    return {
      request: null,
      validationError: $localize`:@@admin.items.form.classSubclassRequired:Class ID and subclass ID are required together.`,
    };
  }

  const request: AdminItemOverrideRequest = {
    ...buildCoreFields(values, parser),
    ...buildClassificationFields(values, item, itemClassId, itemSubclassId),
    ...buildEconomyFields(values, parser),
    ...buildFlagFields(values, parser),
    expansionId: overrideNumberField(
      parser.parseOptional(values.expansionId, 'Expansion ID'),
      item.base?.expansion?.id,
    ),
  };
  return parser.validationError
    ? { request: null, validationError: parser.validationError }
    : { request, validationError: null };
}

function buildCoreFields(
  values: ItemOverrideFormValues,
  parser: NonNegativeIntegerParser,
): Pick<
  AdminItemOverrideRequest,
  'nameLocales' | 'qualityType' | 'level' | 'requiredLevel' | 'mediaUrl' | 'mediaSourceUrl'
> {
  return {
    nameLocales: values.nameOverride ? values.nameLocales : undefined,
    qualityType: stringField(values.qualityOverride, values.qualityType),
    level: parser.numberField(values.levelOverride, values.level, 'Level'),
    requiredLevel: parser.numberField(
      values.requiredLevelOverride,
      values.requiredLevel,
      'Required level',
    ),
    mediaUrl: stringField(values.mediaUrlOverride, values.mediaUrl),
    mediaSourceUrl: stringField(values.mediaSourceUrlOverride, values.mediaSourceUrl),
  };
}

function buildClassificationFields(
  values: ItemOverrideFormValues,
  item: AdminItem1,
  itemClassId: number | null,
  itemSubclassId: number | null,
): Pick<
  AdminItemOverrideRequest,
  'itemClassId' | 'itemSubclassId' | 'inventoryType' | 'bindingType'
> {
  return {
    itemClassId: overrideNumberField(itemClassId, item.base?.itemClass?.id),
    itemSubclassId: overrideNumberField(itemSubclassId, item.base?.itemSubclass?.id),
    inventoryType: overrideStringField(values.inventoryType, item.base?.inventoryType?.type),
    bindingType: overrideStringField(values.bindingType, item.base?.binding?.type),
  };
}

function buildEconomyFields(
  values: ItemOverrideFormValues,
  parser: NonNegativeIntegerParser,
): Pick<AdminItemOverrideRequest, 'purchasePrice' | 'sellPrice' | 'maxCount'> {
  return {
    purchasePrice: parser.numberField(
      values.purchasePriceOverride,
      values.purchasePrice,
      'Purchase price',
    ),
    sellPrice: parser.numberField(values.sellPriceOverride, values.sellPrice, 'Sell price'),
    maxCount: parser.numberField(values.maxCountOverride, values.maxCount, 'Max count'),
  };
}

function buildFlagFields(
  values: ItemOverrideFormValues,
  parser: NonNegativeIntegerParser,
): Pick<
  AdminItemOverrideRequest,
  'isEquippable' | 'isStackable' | 'purchaseQuantity' | 'overrideNote'
> {
  return {
    isEquippable: values.equippableOverride ? values.isEquippable === 'true' : null,
    isStackable: values.stackableOverride ? values.isStackable === 'true' : null,
    purchaseQuantity: parser.numberField(
      values.purchaseQuantityOverride,
      values.purchaseQuantity,
      'Purchase quantity',
    ),
    overrideNote: trimmedOrNull(values.overrideNote),
  };
}

export function initialItemOverrideFormValues(
  override: AdminItemFields,
  item: AdminItem1,
): ItemOverrideFormValues {
  return {
    ...initialCoreValues(override),
    ...initialClassificationValues(item),
    ...initialEconomyValues(override, item),
    ...initialFlagValues(override),
  };
}

function initialCoreValues(override: AdminItemFields) {
  return {
    nameOverride: Boolean(override.nameLocales),
    nameLocales: { ...(override.nameLocales ?? {}) },
    qualityOverride: Boolean(override.quality),
    qualityType: override.quality?.type ?? '',
    levelOverride: override.level !== undefined && override.level !== null,
    level: valueString(override.level),
    requiredLevelOverride: override.requiredLevel !== undefined && override.requiredLevel !== null,
    requiredLevel: valueString(override.requiredLevel),
    mediaUrlOverride: override.mediaUrl !== undefined && override.mediaUrl !== null,
    mediaUrl: override.mediaUrl ?? '',
    mediaSourceUrlOverride:
      override.mediaSourceUrl !== undefined && override.mediaSourceUrl !== null,
    mediaSourceUrl: override.mediaSourceUrl ?? '',
  };
}

function initialClassificationValues(item: AdminItem1) {
  return {
    itemClassId: valueString(item.effective.itemClass?.id),
    itemSubclassId: valueString(item.effective.itemSubclass?.id),
    inventoryType: item.effective.inventoryType?.type ?? '',
    bindingType: item.effective.binding?.type ?? '',
  };
}

function initialEconomyValues(override: AdminItemFields, item: AdminItem1) {
  return {
    purchasePriceOverride: override.purchasePrice !== undefined && override.purchasePrice !== null,
    purchasePrice: valueString(override.purchasePrice),
    sellPriceOverride: override.sellPrice !== undefined && override.sellPrice !== null,
    sellPrice: valueString(override.sellPrice),
    maxCountOverride: override.maxCount !== undefined && override.maxCount !== null,
    maxCount: valueString(override.maxCount),
    expansionId: valueString(item.effective.expansion?.id),
  };
}

function initialFlagValues(override: AdminItemFields) {
  return {
    equippableOverride: override.isEquippable !== undefined && override.isEquippable !== null,
    isEquippable: String(override.isEquippable === true),
    stackableOverride: override.isStackable !== undefined && override.isStackable !== null,
    isStackable: String(override.isStackable === true),
    purchaseQuantityOverride:
      override.purchaseQuantity !== undefined && override.purchaseQuantity !== null,
    purchaseQuantity: valueString(override.purchaseQuantity),
    overrideNote: override.overrideNote ?? '',
  };
}

export function inheritedItemName(item: AdminItem1): string {
  return item.effective.name ?? $localize`:@@admin.items.unnamed:Unnamed item`;
}

export function itemReferenceLabel(
  reference: { id: number; type?: string | null; name: string | null } | undefined,
): string {
  if (!reference) return '—';
  return reference.name ?? reference.type ?? String(reference.id);
}

export function displayItemValue(value: number | string | boolean | null | undefined): string {
  if (value === undefined || value === null || value === '') return '—';
  return String(value);
}

export function itemWowheadUrl(itemId: number): string {
  return `https://www.wowhead.com/item=${itemId}`;
}

function stringField(enabled: boolean, value: string): string | null {
  return enabled ? trimmedOrNull(value) : null;
}

function overrideStringField(value: string, baseValue: string | null | undefined): string | null {
  const selectedValue = trimmedOrNull(value);
  return selectedValue === (baseValue ?? null) ? null : selectedValue;
}

function overrideNumberField(
  value: number | null,
  baseValue: number | null | undefined,
): number | null {
  return value === (baseValue ?? null) ? null : value;
}

function trimmedOrNull(value: string): string | null {
  const trimmedValue = value.trim();
  return trimmedValue.length > 0 ? trimmedValue : null;
}
