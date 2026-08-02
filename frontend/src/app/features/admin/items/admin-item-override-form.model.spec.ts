import { AdminItem1 } from '@api/generated';

import {
  createItemOverrideRequest,
  initialItemOverrideFormValues,
  type ItemOverrideFormValues,
} from './admin-item-override-form.model';

describe('admin item override form model', () => {
  it('parses enabled numeric fields and trims string overrides', () => {
    const result = createItemOverrideRequest(
      formValues({
        levelOverride: true,
        level: ' 42 ',
        requiredLevelOverride: true,
        requiredLevel: '10',
        purchasePriceOverride: true,
        purchasePrice: '1250',
        mediaUrlOverride: true,
        mediaUrl: ' https://example.test/item.png ',
        overrideNote: ' reviewed ',
      }),
      itemFixture(),
    );

    expect(result.validationError).toBeNull();
    expect(result.request).toEqual(
      expect.objectContaining({
        level: 42,
        requiredLevel: 10,
        purchasePrice: 1250,
        mediaUrl: 'https://example.test/item.png',
        overrideNote: 'reviewed',
      }),
    );
  });

  it.each([
    ['Level', { levelOverride: true, level: '-1' }],
    ['Purchase quantity', { purchaseQuantityOverride: true, purchaseQuantity: 'invalid' }],
  ])('rejects an invalid %s', (label, overrides) => {
    const result = createItemOverrideRequest(formValues(overrides), itemFixture());

    expect(result.request).toBeNull();
    expect(result.validationError).toBe(`${label} must be a non-negative number.`);
  });

  it('requires a subclass when a class is selected', () => {
    const result = createItemOverrideRequest(
      formValues({ itemClassId: '4', itemSubclassId: '' }),
      itemFixture(),
    );

    expect(result.request).toBeNull();
    expect(result.validationError).toContain('Class ID and subclass ID are required together.');
  });

  it('normalizes inherited classification and expansion values to null overrides', () => {
    const result = createItemOverrideRequest(
      formValues({
        itemClassId: '2',
        itemSubclassId: '3',
        inventoryType: 'HEAD',
        bindingType: 'BOUND',
        expansionId: '11',
      }),
      itemFixture(),
    );

    expect(result.request).toEqual(
      expect.objectContaining({
        itemClassId: null,
        itemSubclassId: null,
        inventoryType: null,
        bindingType: null,
        expansionId: null,
      }),
    );
  });

  it('emits enabled flags and a changed expansion while leaving disabled flags unset', () => {
    const result = createItemOverrideRequest(
      formValues({
        equippableOverride: true,
        isEquippable: 'false',
        stackableOverride: true,
        isStackable: 'true',
        expansionId: '12',
      }),
      itemFixture(),
    );

    expect(result.request).toEqual(
      expect.objectContaining({ isEquippable: false, isStackable: true, expansionId: 12 }),
    );

    const disabled = createItemOverrideRequest(formValues(), itemFixture());
    expect(disabled.request).toEqual(
      expect.objectContaining({ isEquippable: null, isStackable: null }),
    );
  });

  it('initializes override toggles separately from effective inherited values', () => {
    const values = initialItemOverrideFormValues(
      { level: 70, isEquippable: false, purchaseQuantity: 5 },
      itemFixture(),
    );

    expect(values).toEqual(
      expect.objectContaining({
        levelOverride: true,
        level: '70',
        equippableOverride: true,
        isEquippable: 'false',
        stackableOverride: false,
        itemClassId: '2',
        itemSubclassId: '3',
        expansionId: '11',
        purchaseQuantityOverride: true,
        purchaseQuantity: '5',
      }),
    );
  });
});

function formValues(overrides: Partial<ItemOverrideFormValues> = {}): ItemOverrideFormValues {
  return {
    nameOverride: false,
    nameLocales: {},
    qualityOverride: false,
    qualityType: '',
    levelOverride: false,
    level: '',
    requiredLevelOverride: false,
    requiredLevel: '',
    itemClassId: '2',
    itemSubclassId: '3',
    inventoryType: 'HEAD',
    bindingType: 'BOUND',
    mediaUrlOverride: false,
    mediaUrl: '',
    mediaSourceUrlOverride: false,
    mediaSourceUrl: '',
    purchasePriceOverride: false,
    purchasePrice: '',
    sellPriceOverride: false,
    sellPrice: '',
    maxCountOverride: false,
    maxCount: '',
    equippableOverride: false,
    isEquippable: 'false',
    stackableOverride: false,
    isStackable: 'false',
    purchaseQuantityOverride: false,
    purchaseQuantity: '',
    expansionId: '11',
    overrideNote: '',
    ...overrides,
  };
}

function itemFixture(): AdminItem1 {
  const inheritedFields = {
    name: 'Inherited item',
    itemClass: { id: 2, name: 'Armor' },
    itemSubclass: { id: 3, name: 'Plate' },
    inventoryType: { id: 1, type: 'HEAD', name: 'Head' },
    binding: { id: 1, type: 'BOUND', name: 'Bound' },
    expansion: {
      id: 11,
      slug: 'khaz-algar',
      name: 'Khaz Algar',
      nameLocales: {},
      majorVersion: 11,
      displayOrder: 11,
    },
  };
  return {
    id: 123,
    hasBase: true,
    hasOverride: false,
    base: inheritedFields,
    effective: inheritedFields,
  };
}
