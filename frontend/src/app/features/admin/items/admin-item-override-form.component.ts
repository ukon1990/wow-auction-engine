import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  AdminExpansion,
  AdminItem1,
  AdminItemFields,
  AdminItemOverrideRequest,
  GameLocale,
} from '@api/generated';
import { LocaleFieldsComponent } from '@features/admin/shared/locale-fields.component';
import {
  CheckboxInputComponent,
  SelectInputComponent,
  SelectInputOption,
  TextInputComponent,
} from '@ui';

const standaloneModel = { standalone: true };

@Component({
  selector: 'app-admin-item-override-form',
  imports: [
    FormsModule,
    CheckboxInputComponent,
    LocaleFieldsComponent,
    SelectInputComponent,
    TextInputComponent,
  ],
  template: `
    <form class="grid gap-5" (submit)="onSubmit($event)">
      @if (item(); as currentItem) {
        <section class="grid gap-2 rounded-md border border-white/10 bg-surface-container p-3">
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div class="min-w-0">
              <p class="ee-label text-outline">Effective</p>
              <h3 class="truncate font-semibold text-on-surface">
                {{ currentItem.effective.name || 'Unnamed item' }}
              </h3>
            </div>
            <div class="grid justify-items-end gap-1 ee-data">
              <p class="text-outline">#{{ currentItem.id }}</p>
              <a
                class="font-semibold text-primary-container underline-offset-4 transition hover:underline focus:outline-none focus:ring-2 focus:ring-primary-container"
                [href]="wowheadUrl(currentItem.id)"
                target="_blank"
                rel="noopener noreferrer"
              >
                Wowhead
              </a>
            </div>
          </div>
          <dl class="grid gap-2 ee-data text-outline sm:grid-cols-2">
            <div>
              <dt>Base</dt>
              <dd class="text-on-surface">{{ currentItem.hasBase ? 'Present' : 'Missing' }}</dd>
            </div>
            <div>
              <dt>Override</dt>
              <dd class="text-on-surface">
                {{ currentItem.hasOverride ? 'Present' : 'Not set' }}
              </dd>
            </div>
          </dl>
          <dl class="grid gap-2 ee-data text-outline sm:grid-cols-2">
            <div>
              <dt>Quality</dt>
              <dd class="text-on-surface">{{ referenceLabel(currentItem.effective.quality) }}</dd>
            </div>
            <div>
              <dt>Level</dt>
              <dd class="text-on-surface">{{ displayValue(currentItem.effective.level) }}</dd>
            </div>
            <div>
              <dt>Class</dt>
              <dd class="text-on-surface">
                {{ referenceLabel(currentItem.effective.itemClass) }} /
                {{ referenceLabel(currentItem.effective.itemSubclass) }}
              </dd>
            </div>
            <div>
              <dt>Expansion</dt>
              <dd class="text-on-surface">
                {{ currentItem.effective.expansion?.name ?? '—' }}
              </dd>
            </div>
            <div>
              <dt>Inventory</dt>
              <dd class="text-on-surface">
                {{ referenceLabel(currentItem.effective.inventoryType) }}
              </dd>
            </div>
            <div>
              <dt>Binding</dt>
              <dd class="text-on-surface">{{ referenceLabel(currentItem.effective.binding) }}</dd>
            </div>
          </dl>
        </section>

        <fieldset class="grid gap-4">
          <legend class="font-semibold text-on-surface">Localized name</legend>
          <ee-checkbox-input
            label="Override localized names"
            [ngModel]="nameOverride()"
            [ngModelOptions]="standaloneModel"
            (ngModelChange)="nameOverride.set($event)"
          />
          @if (nameOverride()) {
            <app-locale-fields [value]="nameLocales()" (valueChange)="nameLocales.set($event)" />
          } @else {
            <p class="ee-data text-outline">
              {{ inheritedName(currentItem) }}
            </p>
          }
        </fieldset>

        <fieldset class="grid gap-4">
          <legend class="font-semibold text-on-surface">Core fields</legend>
          <div class="grid gap-4 md:grid-cols-2">
            <ee-checkbox-input
              label="Override quality"
              [ngModel]="qualityOverride()"
              [ngModelOptions]="standaloneModel"
              (ngModelChange)="qualityOverride.set($event)"
            />
            <ee-text-input
              label="Quality type"
              placeholder="COMMON"
              [disabled]="!qualityOverride()"
              [ngModel]="qualityType()"
              [ngModelOptions]="standaloneModel"
              (ngModelChange)="qualityType.set($event)"
            />
            <ee-checkbox-input
              label="Override level"
              [ngModel]="levelOverride()"
              [ngModelOptions]="standaloneModel"
              (ngModelChange)="levelOverride.set($event)"
            />
            <ee-text-input
              label="Level"
              type="number"
              [disabled]="!levelOverride()"
              [ngModel]="level()"
              [ngModelOptions]="standaloneModel"
              (ngModelChange)="level.set($event)"
            />
            <ee-checkbox-input
              label="Override required level"
              [ngModel]="requiredLevelOverride()"
              [ngModelOptions]="standaloneModel"
              (ngModelChange)="requiredLevelOverride.set($event)"
            />
            <ee-text-input
              label="Required level"
              type="number"
              [disabled]="!requiredLevelOverride()"
              [ngModel]="requiredLevel()"
              [ngModelOptions]="standaloneModel"
              (ngModelChange)="requiredLevel.set($event)"
            />
          </div>
        </fieldset>

        <fieldset class="grid gap-4">
          <legend class="font-semibold text-on-surface">Classification</legend>
          <div class="grid gap-4 md:grid-cols-2">
            <ee-select-input
              label="Class"
              [options]="classOptions()"
              [ngModel]="itemClassId()"
              [ngModelOptions]="standaloneModel"
              (ngModelChange)="setItemClass($event)"
            />
            <ee-select-input
              label="Subclass"
              [options]="subclassOptions()"
              [ngModel]="itemSubclassId()"
              [ngModelOptions]="standaloneModel"
              (ngModelChange)="itemSubclassId.set($event)"
            />
            <ee-select-input
              label="Inventory type"
              [options]="inventoryTypeOptions()"
              [ngModel]="inventoryType()"
              [ngModelOptions]="standaloneModel"
              (ngModelChange)="inventoryType.set($event)"
            />
            <ee-select-input
              label="Binding type"
              [options]="bindingTypeOptions()"
              [ngModel]="bindingType()"
              [ngModelOptions]="standaloneModel"
              (ngModelChange)="bindingType.set($event)"
            />
          </div>
        </fieldset>

        <fieldset class="grid gap-4">
          <legend class="font-semibold text-on-surface">Media and economy</legend>
          <div class="grid gap-4 md:grid-cols-2">
            <ee-checkbox-input
              label="Override media URL"
              [ngModel]="mediaUrlOverride()"
              [ngModelOptions]="standaloneModel"
              (ngModelChange)="mediaUrlOverride.set($event)"
            />
            <ee-text-input
              label="Media URL"
              type="url"
              [disabled]="!mediaUrlOverride()"
              [ngModel]="mediaUrl()"
              [ngModelOptions]="standaloneModel"
              (ngModelChange)="mediaUrl.set($event)"
            />
            <ee-checkbox-input
              label="Override media source URL"
              [ngModel]="mediaSourceUrlOverride()"
              [ngModelOptions]="standaloneModel"
              (ngModelChange)="mediaSourceUrlOverride.set($event)"
            />
            <ee-text-input
              label="Media source URL"
              type="url"
              [disabled]="!mediaSourceUrlOverride()"
              [ngModel]="mediaSourceUrl()"
              [ngModelOptions]="standaloneModel"
              (ngModelChange)="mediaSourceUrl.set($event)"
            />
            <ee-select-input
              label="Expansion"
              [options]="expansionOptions()"
              [ngModel]="expansionId()"
              [ngModelOptions]="standaloneModel"
              (ngModelChange)="expansionId.set($event)"
            />
            <ee-checkbox-input
              label="Override purchase price"
              [ngModel]="purchasePriceOverride()"
              [ngModelOptions]="standaloneModel"
              (ngModelChange)="purchasePriceOverride.set($event)"
            />
            <ee-text-input
              label="Purchase price"
              type="number"
              [disabled]="!purchasePriceOverride()"
              [ngModel]="purchasePrice()"
              [ngModelOptions]="standaloneModel"
              (ngModelChange)="purchasePrice.set($event)"
            />
            <ee-checkbox-input
              label="Override sell price"
              [ngModel]="sellPriceOverride()"
              [ngModelOptions]="standaloneModel"
              (ngModelChange)="sellPriceOverride.set($event)"
            />
            <ee-text-input
              label="Sell price"
              type="number"
              [disabled]="!sellPriceOverride()"
              [ngModel]="sellPrice()"
              [ngModelOptions]="standaloneModel"
              (ngModelChange)="sellPrice.set($event)"
            />
          </div>
        </fieldset>

        <fieldset class="grid gap-4">
          <legend class="font-semibold text-on-surface">Stack and flags</legend>
          <div class="grid gap-4 md:grid-cols-2">
            <ee-checkbox-input
              label="Override max count"
              [ngModel]="maxCountOverride()"
              [ngModelOptions]="standaloneModel"
              (ngModelChange)="maxCountOverride.set($event)"
            />
            <ee-text-input
              label="Max count"
              type="number"
              [disabled]="!maxCountOverride()"
              [ngModel]="maxCount()"
              [ngModelOptions]="standaloneModel"
              (ngModelChange)="maxCount.set($event)"
            />
            <ee-checkbox-input
              label="Override purchase quantity"
              [ngModel]="purchaseQuantityOverride()"
              [ngModelOptions]="standaloneModel"
              (ngModelChange)="purchaseQuantityOverride.set($event)"
            />
            <ee-text-input
              label="Purchase quantity"
              type="number"
              [disabled]="!purchaseQuantityOverride()"
              [ngModel]="purchaseQuantity()"
              [ngModelOptions]="standaloneModel"
              (ngModelChange)="purchaseQuantity.set($event)"
            />
            <ee-checkbox-input
              label="Override equippable"
              [ngModel]="equippableOverride()"
              [ngModelOptions]="standaloneModel"
              (ngModelChange)="equippableOverride.set($event)"
            />
            <ee-select-input
              label="Equippable"
              [disabled]="!equippableOverride()"
              [options]="booleanOptions"
              [ngModel]="isEquippable()"
              [ngModelOptions]="standaloneModel"
              (ngModelChange)="isEquippable.set($event)"
            />
            <ee-checkbox-input
              label="Override stackable"
              [ngModel]="stackableOverride()"
              [ngModelOptions]="standaloneModel"
              (ngModelChange)="stackableOverride.set($event)"
            />
            <ee-select-input
              label="Stackable"
              [disabled]="!stackableOverride()"
              [options]="booleanOptions"
              [ngModel]="isStackable()"
              [ngModelOptions]="standaloneModel"
              (ngModelChange)="isStackable.set($event)"
            />
          </div>
        </fieldset>

        <ee-text-input
          label="Override note"
          [ngModel]="overrideNote()"
          [ngModelOptions]="standaloneModel"
          (ngModelChange)="overrideNote.set($event)"
        />

        @if (validationError()) {
          <p
            class="rounded-md border border-error/30 bg-error/10 px-3 py-2 text-sm text-error"
            role="alert"
          >
            {{ validationError() }}
          </p>
        }
        @if (submitError()) {
          <p
            class="rounded-md border border-error/30 bg-error/10 px-3 py-2 text-sm text-error"
            role="alert"
          >
            {{ submitError() }}
          </p>
        }

        <div class="flex flex-wrap gap-3">
          <button
            type="submit"
            class="h-10 rounded-md bg-primary px-4 font-semibold text-on-primary transition hover:opacity-90 focus:outline-none focus:ring-2 focus:ring-primary-container disabled:cursor-wait disabled:opacity-70"
            [disabled]="submitting()"
          >
            Save override
          </button>
          <button
            type="button"
            class="h-10 rounded-md border border-white/10 px-4 font-semibold text-on-surface transition hover:bg-white/5 focus:outline-none focus:ring-2 focus:ring-primary-container"
            (click)="cancelled.emit()"
          >
            Cancel
          </button>
        </div>
      }
    </form>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminItemOverrideFormComponent {
  protected readonly standaloneModel = standaloneModel;
  protected readonly booleanOptions = [
    { id: 'true', label: $localize`:@@common.yes:Yes` },
    { id: 'false', label: $localize`:@@common.no:No` },
  ];

  readonly item = input.required<AdminItem1>();
  readonly expansions = input<readonly AdminExpansion[]>([]);
  readonly submitting = input(false);
  readonly submitError = input<string | null>(null);
  readonly submitted = output<AdminItemOverrideRequest>();
  readonly cancelled = output<void>();

  protected readonly nameOverride = signal(false);
  protected readonly nameLocales = signal<GameLocale>({});
  protected readonly qualityOverride = signal(false);
  protected readonly qualityType = signal('');
  protected readonly levelOverride = signal(false);
  protected readonly level = signal('');
  protected readonly requiredLevelOverride = signal(false);
  protected readonly requiredLevel = signal('');
  protected readonly itemClassId = signal('');
  protected readonly itemSubclassId = signal('');
  protected readonly inventoryType = signal('');
  protected readonly bindingType = signal('');
  protected readonly mediaUrlOverride = signal(false);
  protected readonly mediaUrl = signal('');
  protected readonly mediaSourceUrlOverride = signal(false);
  protected readonly mediaSourceUrl = signal('');
  protected readonly purchasePriceOverride = signal(false);
  protected readonly purchasePrice = signal('');
  protected readonly sellPriceOverride = signal(false);
  protected readonly sellPrice = signal('');
  protected readonly maxCountOverride = signal(false);
  protected readonly maxCount = signal('');
  protected readonly equippableOverride = signal(false);
  protected readonly isEquippable = signal('false');
  protected readonly stackableOverride = signal(false);
  protected readonly isStackable = signal('false');
  protected readonly purchaseQuantityOverride = signal(false);
  protected readonly purchaseQuantity = signal('');
  protected readonly expansionId = signal('');
  protected readonly overrideNote = signal('');
  protected readonly validationError = signal<string | null>(null);
  protected readonly classOptions = computed(() =>
    mergeOptions(
      ITEM_CLASS_OPTIONS,
      referencesToOptions(
        this.item().base?.itemClass,
        this.item().override?.itemClass,
        this.item().effective.itemClass,
      ),
    ),
  );
  protected readonly subclassOptions = computed(() =>
    mergeOptions(
      ITEM_SUBCLASS_OPTIONS[this.itemClassId()] ?? [],
      referencesToOptions(
        this.item().base?.itemSubclass,
        this.item().override?.itemSubclass,
        this.item().effective.itemSubclass,
      ),
    ),
  );
  protected readonly inventoryTypeOptions = computed(() =>
    mergeOptions(
      INVENTORY_TYPE_OPTIONS,
      referencesToTypeOptions(
        this.item().base?.inventoryType,
        this.item().override?.inventoryType,
        this.item().effective.inventoryType,
      ),
    ),
  );
  protected readonly bindingTypeOptions = computed(() =>
    mergeOptions(
      BINDING_TYPE_OPTIONS,
      referencesToTypeOptions(
        this.item().base?.binding,
        this.item().override?.binding,
        this.item().effective.binding,
      ),
    ),
  );
  protected readonly expansionOptions = computed(() =>
    mergeOptions(
      this.expansions().map((expansion) => ({
        id: String(expansion.id),
        label: expansion.name,
      })),
      expansionReferencesToOptions(
        this.item().base?.expansion,
        this.item().override?.expansion,
        this.item().effective.expansion,
      ),
    ),
  );

  constructor() {
    effect(() => {
      this.initialize(this.item().override ?? {});
    });
  }

  protected inheritedName(item: AdminItem1): string {
    return item.effective.name ?? $localize`:@@admin.items.unnamed:Unnamed item`;
  }

  protected referenceLabel(
    reference: { id: number; type?: string | null; name: string | null } | undefined,
  ): string {
    if (!reference) return '—';
    return reference.name ?? reference.type ?? String(reference.id);
  }

  protected displayValue(value: number | string | boolean | null | undefined): string {
    if (value === undefined || value === null || value === '') return '—';
    return String(value);
  }

  protected wowheadUrl(itemId: number): string {
    return `https://www.wowhead.com/item=${itemId}`;
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();
    this.validationError.set(null);
    const currentItem = this.item();

    const itemClassId = this.parseOptionalInt(this.itemClassId(), 'Class ID');
    const itemSubclassId = this.parseOptionalInt(this.itemSubclassId(), 'Subclass ID');
    if (this.validationError()) return;
    if (itemClassId !== null && itemSubclassId === null) {
      this.validationError.set(
        $localize`:@@admin.items.form.classSubclassRequired:Class ID and subclass ID are required together.`,
      );
      return;
    }

    const request: AdminItemOverrideRequest = {
      nameLocales: this.nameOverride() ? this.nameLocales() : undefined,
      qualityType: this.stringField(this.qualityOverride(), this.qualityType()),
      level: this.numberField(this.levelOverride(), this.level(), 'Level'),
      requiredLevel: this.numberField(
        this.requiredLevelOverride(),
        this.requiredLevel(),
        'Required level',
      ),
      mediaUrl: this.stringField(this.mediaUrlOverride(), this.mediaUrl()),
      mediaSourceUrl: this.stringField(this.mediaSourceUrlOverride(), this.mediaSourceUrl()),
      itemClassId: this.overrideNumberField(itemClassId, currentItem.base?.itemClass?.id),
      itemSubclassId: this.overrideNumberField(itemSubclassId, currentItem.base?.itemSubclass?.id),
      inventoryType: this.overrideStringField(
        this.inventoryType(),
        currentItem.base?.inventoryType?.type,
      ),
      bindingType: this.overrideStringField(this.bindingType(), currentItem.base?.binding?.type),
      purchasePrice: this.numberField(
        this.purchasePriceOverride(),
        this.purchasePrice(),
        'Purchase price',
      ),
      sellPrice: this.numberField(this.sellPriceOverride(), this.sellPrice(), 'Sell price'),
      maxCount: this.numberField(this.maxCountOverride(), this.maxCount(), 'Max count'),
      isEquippable: this.equippableOverride() ? this.isEquippable() === 'true' : null,
      isStackable: this.stackableOverride() ? this.isStackable() === 'true' : null,
      purchaseQuantity: this.numberField(
        this.purchaseQuantityOverride(),
        this.purchaseQuantity(),
        'Purchase quantity',
      ),
      expansionId: this.overrideNumberField(
        this.parseOptionalInt(this.expansionId(), 'Expansion ID'),
        currentItem.base?.expansion?.id,
      ),
      overrideNote: this.trimmedOrNull(this.overrideNote()),
    };
    if (this.validationError()) return;

    this.submitted.emit(request);
  }

  private initialize(override: AdminItemFields): void {
    this.nameOverride.set(Boolean(override.nameLocales));
    this.nameLocales.set({ ...(override.nameLocales ?? {}) });
    this.qualityOverride.set(Boolean(override.quality));
    this.qualityType.set(override.quality?.type ?? '');
    this.levelOverride.set(override.level !== undefined && override.level !== null);
    this.level.set(valueString(override.level));
    this.requiredLevelOverride.set(
      override.requiredLevel !== undefined && override.requiredLevel !== null,
    );
    this.requiredLevel.set(valueString(override.requiredLevel));
    const item = this.item();
    this.itemClassId.set(valueString(item.effective.itemClass?.id));
    this.itemSubclassId.set(valueString(item.effective.itemSubclass?.id));
    this.inventoryType.set(item.effective.inventoryType?.type ?? '');
    this.bindingType.set(item.effective.binding?.type ?? '');
    this.mediaUrlOverride.set(override.mediaUrl !== undefined && override.mediaUrl !== null);
    this.mediaUrl.set(override.mediaUrl ?? '');
    this.mediaSourceUrlOverride.set(
      override.mediaSourceUrl !== undefined && override.mediaSourceUrl !== null,
    );
    this.mediaSourceUrl.set(override.mediaSourceUrl ?? '');
    this.purchasePriceOverride.set(
      override.purchasePrice !== undefined && override.purchasePrice !== null,
    );
    this.purchasePrice.set(valueString(override.purchasePrice));
    this.sellPriceOverride.set(override.sellPrice !== undefined && override.sellPrice !== null);
    this.sellPrice.set(valueString(override.sellPrice));
    this.maxCountOverride.set(override.maxCount !== undefined && override.maxCount !== null);
    this.maxCount.set(valueString(override.maxCount));
    this.equippableOverride.set(
      override.isEquippable !== undefined && override.isEquippable !== null,
    );
    this.isEquippable.set(String(override.isEquippable === true));
    this.stackableOverride.set(override.isStackable !== undefined && override.isStackable !== null);
    this.isStackable.set(String(override.isStackable === true));
    this.purchaseQuantityOverride.set(
      override.purchaseQuantity !== undefined && override.purchaseQuantity !== null,
    );
    this.purchaseQuantity.set(valueString(override.purchaseQuantity));
    this.expansionId.set(valueString(item.effective.expansion?.id));
    this.overrideNote.set(override.overrideNote ?? '');
    this.validationError.set(null);
  }

  protected setItemClass(value: string): void {
    this.itemClassId.set(value);
    const selectedSubclass = this.itemSubclassId();
    const options = ITEM_SUBCLASS_OPTIONS[value] ?? [];
    if (selectedSubclass && options.some((option) => option.id === selectedSubclass)) return;

    const defaultSubclassId = valueString(this.item().base?.itemSubclass?.id);
    this.itemSubclassId.set(
      options.some((option) => option.id === defaultSubclassId) ? defaultSubclassId : '',
    );
  }

  private stringField(enabled: boolean, value: string): string | null {
    if (!enabled) return null;
    return this.trimmedOrNull(value);
  }

  private numberField(enabled: boolean, value: string, label: string): number | null {
    if (!enabled) return null;
    return this.parseOptionalInt(value, label);
  }

  private overrideStringField(value: string, baseValue: string | null | undefined): string | null {
    const selected = this.trimmedOrNull(value);
    return selected === (baseValue ?? null) ? null : selected;
  }

  private overrideNumberField(
    value: number | null,
    baseValue: number | null | undefined,
  ): number | null {
    return value === (baseValue ?? null) ? null : value;
  }

  private parseOptionalInt(value: string, label: string): number | null {
    const trimmed = value.trim();
    if (trimmed.length === 0) return null;
    const parsed = Number.parseInt(trimmed, 10);
    if (!Number.isFinite(parsed) || parsed < 0) {
      this.validationError.set(`${label} must be a non-negative number.`);
      return null;
    }
    return parsed;
  }

  private trimmedOrNull(value: string): string | null {
    const trimmed = value.trim();
    return trimmed.length > 0 ? trimmed : null;
  }
}

function valueString(value: number | null | undefined): string {
  return value === undefined || value === null ? '' : String(value);
}

function option(id: string | number, label: string): SelectInputOption {
  return { id: String(id), label };
}

function mergeOptions(
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

function referencesToOptions(
  ...references: Array<{ id: number; name: string | null } | undefined>
): SelectInputOption[] {
  return references
    .filter((reference): reference is { id: number; name: string | null } => Boolean(reference))
    .map((reference) => option(reference.id, reference.name ?? String(reference.id)));
}

function referencesToTypeOptions(
  ...references: Array<{ type?: string | null; name: string | null } | undefined>
): SelectInputOption[] {
  return references
    .filter((reference): reference is { type: string; name: string | null } =>
      Boolean(reference?.type),
    )
    .map((reference) => option(reference.type, reference.name ?? reference.type));
}

function expansionReferencesToOptions(
  ...expansions: Array<{ id: number; name: string } | undefined>
): SelectInputOption[] {
  return expansions
    .filter((expansion): expansion is { id: number; name: string } => Boolean(expansion))
    .map((expansion) => option(expansion.id, expansion.name));
}

const ITEM_CLASS_OPTIONS: readonly SelectInputOption[] = [
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

const ITEM_SUBCLASS_OPTIONS: Record<string, readonly SelectInputOption[]> = {
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

const INVENTORY_TYPE_OPTIONS: readonly SelectInputOption[] = [
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

const BINDING_TYPE_OPTIONS: readonly SelectInputOption[] = [
  option('NONE', 'Not bound'),
  option('ON_ACQUIRE', 'Binds when picked up'),
  option('ON_EQUIP', 'Binds when equipped'),
  option('ON_USE', 'Binds when used'),
  option('QUEST', 'Quest item'),
  option('ACCOUNT', 'Account bound'),
  option('BNET_ACCOUNT', 'Battle.net account bound'),
];
