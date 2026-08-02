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
import { CheckboxInputComponent, SelectInputComponent, TextInputComponent } from '@ui';
import {
  BINDING_TYPE_OPTIONS,
  expansionReferencesToOptions,
  INVENTORY_TYPE_OPTIONS,
  ITEM_CLASS_OPTIONS,
  ITEM_SUBCLASS_OPTIONS,
  mergeOptions,
  referencesToOptions,
  referencesToTypeOptions,
  valueString,
} from './admin-item-override-form.options';
import {
  createItemOverrideRequest,
  displayItemValue,
  inheritedItemName,
  initialItemOverrideFormValues,
  itemReferenceLabel,
  ItemOverrideFormValues,
  itemWowheadUrl,
} from './admin-item-override-form.model';

export { ITEM_CLASS_OPTIONS, ITEM_SUBCLASS_OPTIONS } from './admin-item-override-form.options';

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

  protected readonly inheritedName = inheritedItemName;
  protected readonly referenceLabel = itemReferenceLabel;
  protected readonly displayValue = displayItemValue;
  protected readonly wowheadUrl = itemWowheadUrl;

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

  protected onSubmit(event: Event): void {
    event.preventDefault();
    this.validationError.set(null);
    const result = createItemOverrideRequest(this.formValues(), this.item());
    this.validationError.set(result.validationError);
    if (result.request) this.submitted.emit(result.request);
  }

  private initialize(override: AdminItemFields): void {
    const values = initialItemOverrideFormValues(override, this.item());
    this.nameOverride.set(values.nameOverride);
    this.nameLocales.set(values.nameLocales);
    this.qualityOverride.set(values.qualityOverride);
    this.qualityType.set(values.qualityType);
    this.levelOverride.set(values.levelOverride);
    this.level.set(values.level);
    this.requiredLevelOverride.set(values.requiredLevelOverride);
    this.requiredLevel.set(values.requiredLevel);
    this.itemClassId.set(values.itemClassId);
    this.itemSubclassId.set(values.itemSubclassId);
    this.inventoryType.set(values.inventoryType);
    this.bindingType.set(values.bindingType);
    this.mediaUrlOverride.set(values.mediaUrlOverride);
    this.mediaUrl.set(values.mediaUrl);
    this.mediaSourceUrlOverride.set(values.mediaSourceUrlOverride);
    this.mediaSourceUrl.set(values.mediaSourceUrl);
    this.purchasePriceOverride.set(values.purchasePriceOverride);
    this.purchasePrice.set(values.purchasePrice);
    this.sellPriceOverride.set(values.sellPriceOverride);
    this.sellPrice.set(values.sellPrice);
    this.maxCountOverride.set(values.maxCountOverride);
    this.maxCount.set(values.maxCount);
    this.equippableOverride.set(values.equippableOverride);
    this.isEquippable.set(values.isEquippable);
    this.stackableOverride.set(values.stackableOverride);
    this.isStackable.set(values.isStackable);
    this.purchaseQuantityOverride.set(values.purchaseQuantityOverride);
    this.purchaseQuantity.set(values.purchaseQuantity);
    this.expansionId.set(values.expansionId);
    this.overrideNote.set(values.overrideNote);
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

  private formValues(): ItemOverrideFormValues {
    return {
      nameOverride: this.nameOverride(),
      nameLocales: this.nameLocales(),
      qualityOverride: this.qualityOverride(),
      qualityType: this.qualityType(),
      levelOverride: this.levelOverride(),
      level: this.level(),
      requiredLevelOverride: this.requiredLevelOverride(),
      requiredLevel: this.requiredLevel(),
      itemClassId: this.itemClassId(),
      itemSubclassId: this.itemSubclassId(),
      inventoryType: this.inventoryType(),
      bindingType: this.bindingType(),
      mediaUrlOverride: this.mediaUrlOverride(),
      mediaUrl: this.mediaUrl(),
      mediaSourceUrlOverride: this.mediaSourceUrlOverride(),
      mediaSourceUrl: this.mediaSourceUrl(),
      purchasePriceOverride: this.purchasePriceOverride(),
      purchasePrice: this.purchasePrice(),
      sellPriceOverride: this.sellPriceOverride(),
      sellPrice: this.sellPrice(),
      maxCountOverride: this.maxCountOverride(),
      maxCount: this.maxCount(),
      equippableOverride: this.equippableOverride(),
      isEquippable: this.isEquippable(),
      stackableOverride: this.stackableOverride(),
      isStackable: this.isStackable(),
      purchaseQuantityOverride: this.purchaseQuantityOverride(),
      purchaseQuantity: this.purchaseQuantity(),
      expansionId: this.expansionId(),
      overrideNote: this.overrideNote(),
    };
  }
}
