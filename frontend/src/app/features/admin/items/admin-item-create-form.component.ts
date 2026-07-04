import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminItemCreateRequest, GameLocale } from '@api/generated';
import { emptyGameLocale, hasEnglishGameLocale } from '@features/admin/shared/game-locale-fields';
import { LocaleFieldsComponent } from '@features/admin/shared/locale-fields.component';
import { SelectInputComponent, TextInputComponent } from '@ui';

const standaloneModel = { standalone: true };

@Component({
  selector: 'app-admin-item-create-form',
  imports: [FormsModule, LocaleFieldsComponent, SelectInputComponent, TextInputComponent],
  template: `
    <form class="grid gap-5" (submit)="onSubmit($event)">
      <fieldset class="grid gap-4">
        <legend class="font-semibold text-on-surface">Identity</legend>
        <ee-text-input
          label="Item ID"
          type="number"
          [required]="true"
          [ngModel]="id()"
          [ngModelOptions]="standaloneModel"
          (ngModelChange)="id.set($event)"
        />
        <app-locale-fields [value]="nameLocales()" (valueChange)="nameLocales.set($event)" />
      </fieldset>

      <fieldset class="grid gap-4">
        <legend class="font-semibold text-on-surface">Core item data</legend>
        <div class="grid gap-4 md:grid-cols-2">
          <ee-text-input
            label="Quality type"
            placeholder="COMMON"
            [required]="true"
            [ngModel]="qualityType()"
            [ngModelOptions]="standaloneModel"
            (ngModelChange)="qualityType.set($event)"
          />
          <ee-text-input
            label="Level"
            type="number"
            [required]="true"
            [ngModel]="level()"
            [ngModelOptions]="standaloneModel"
            (ngModelChange)="level.set($event)"
          />
          <ee-text-input
            label="Required level"
            type="number"
            [required]="true"
            [ngModel]="requiredLevel()"
            [ngModelOptions]="standaloneModel"
            (ngModelChange)="requiredLevel.set($event)"
          />
          <ee-text-input
            label="Expansion ID"
            type="number"
            [ngModel]="expansionId()"
            [ngModelOptions]="standaloneModel"
            (ngModelChange)="expansionId.set($event)"
          />
        </div>
      </fieldset>

      <fieldset class="grid gap-4">
        <legend class="font-semibold text-on-surface">Classification</legend>
        <div class="grid gap-4 md:grid-cols-2">
          <ee-text-input
            label="Class ID"
            type="number"
            [required]="true"
            [ngModel]="itemClassId()"
            [ngModelOptions]="standaloneModel"
            (ngModelChange)="itemClassId.set($event)"
          />
          <ee-text-input
            label="Subclass ID"
            type="number"
            [required]="true"
            [ngModel]="itemSubclassId()"
            [ngModelOptions]="standaloneModel"
            (ngModelChange)="itemSubclassId.set($event)"
          />
          <ee-text-input
            label="Inventory type"
            [required]="true"
            [ngModel]="inventoryType()"
            [ngModelOptions]="standaloneModel"
            (ngModelChange)="inventoryType.set($event)"
          />
          <ee-text-input
            label="Binding type"
            [ngModel]="bindingType()"
            [ngModelOptions]="standaloneModel"
            (ngModelChange)="bindingType.set($event)"
          />
        </div>
      </fieldset>

      <fieldset class="grid gap-4">
        <legend class="font-semibold text-on-surface">Media and economy</legend>
        <ee-text-input
          label="Media URL"
          type="url"
          [required]="true"
          [ngModel]="mediaUrl()"
          [ngModelOptions]="standaloneModel"
          (ngModelChange)="mediaUrl.set($event)"
        />
        <ee-text-input
          label="Media source URL"
          type="url"
          [ngModel]="mediaSourceUrl()"
          [ngModelOptions]="standaloneModel"
          (ngModelChange)="mediaSourceUrl.set($event)"
        />
        <div class="grid gap-4 md:grid-cols-2">
          <ee-text-input
            label="Purchase price"
            type="number"
            [required]="true"
            [ngModel]="purchasePrice()"
            [ngModelOptions]="standaloneModel"
            (ngModelChange)="purchasePrice.set($event)"
          />
          <ee-text-input
            label="Sell price"
            type="number"
            [required]="true"
            [ngModel]="sellPrice()"
            [ngModelOptions]="standaloneModel"
            (ngModelChange)="sellPrice.set($event)"
          />
          <ee-text-input
            label="Max count"
            type="number"
            [required]="true"
            [ngModel]="maxCount()"
            [ngModelOptions]="standaloneModel"
            (ngModelChange)="maxCount.set($event)"
          />
          <ee-text-input
            label="Purchase quantity"
            type="number"
            [required]="true"
            [ngModel]="purchaseQuantity()"
            [ngModelOptions]="standaloneModel"
            (ngModelChange)="purchaseQuantity.set($event)"
          />
          <ee-select-input
            label="Equippable"
            [options]="booleanOptions"
            [ngModel]="isEquippable()"
            [ngModelOptions]="standaloneModel"
            (ngModelChange)="isEquippable.set($event)"
          />
          <ee-select-input
            label="Stackable"
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

      <div class="flex flex-wrap gap-3">
        <button
          type="submit"
          class="h-10 rounded-md bg-primary px-4 font-semibold text-on-primary transition hover:opacity-90 focus:outline-none focus:ring-2 focus:ring-primary-container"
          [disabled]="submitting()"
        >
          Create item
        </button>
        <button
          type="button"
          class="h-10 rounded-md border border-white/10 px-4 font-semibold text-on-surface transition hover:bg-white/5 focus:outline-none focus:ring-2 focus:ring-primary-container"
          (click)="cancelled.emit()"
        >
          Cancel
        </button>
      </div>
    </form>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminItemCreateFormComponent {
  protected readonly standaloneModel = standaloneModel;
  protected readonly booleanOptions = [
    { id: 'true', label: $localize`:@@common.yes:Yes` },
    { id: 'false', label: $localize`:@@common.no:No` },
  ];

  readonly submitting = input(false);
  readonly submitted = output<AdminItemCreateRequest>();
  readonly cancelled = output<void>();

  protected readonly id = signal('');
  protected readonly nameLocales = signal<GameLocale>(emptyGameLocale());
  protected readonly qualityType = signal('COMMON');
  protected readonly level = signal('1');
  protected readonly requiredLevel = signal('1');
  protected readonly mediaUrl = signal('');
  protected readonly mediaSourceUrl = signal('');
  protected readonly itemClassId = signal('');
  protected readonly itemSubclassId = signal('');
  protected readonly inventoryType = signal('NON_EQUIP');
  protected readonly bindingType = signal('');
  protected readonly purchasePrice = signal('0');
  protected readonly sellPrice = signal('0');
  protected readonly maxCount = signal('1');
  protected readonly isEquippable = signal('false');
  protected readonly isStackable = signal('true');
  protected readonly purchaseQuantity = signal('1');
  protected readonly expansionId = signal('');
  protected readonly overrideNote = signal('');
  protected readonly validationError = signal<string | null>(null);

  protected onSubmit(event: Event): void {
    event.preventDefault();
    this.validationError.set(null);

    if (!hasEnglishGameLocale(this.nameLocales())) {
      this.validationError.set(
        $localize`:@@admin.items.create.englishRequired:English (US) or English (GB) name is required.`,
      );
      return;
    }

    const request: AdminItemCreateRequest = {
      id: this.requiredInt(this.id(), 'Item ID'),
      nameLocales: this.nameLocales(),
      qualityType: this.requiredString(this.qualityType(), 'Quality type'),
      level: this.requiredInt(this.level(), 'Level'),
      requiredLevel: this.requiredInt(this.requiredLevel(), 'Required level'),
      mediaUrl: this.requiredString(this.mediaUrl(), 'Media URL'),
      mediaSourceUrl: this.optionalString(this.mediaSourceUrl()),
      itemClassId: this.requiredInt(this.itemClassId(), 'Class ID'),
      itemSubclassId: this.requiredInt(this.itemSubclassId(), 'Subclass ID'),
      inventoryType: this.requiredString(this.inventoryType(), 'Inventory type'),
      bindingType: this.optionalString(this.bindingType()),
      purchasePrice: this.requiredInt(this.purchasePrice(), 'Purchase price'),
      sellPrice: this.requiredInt(this.sellPrice(), 'Sell price'),
      maxCount: this.requiredInt(this.maxCount(), 'Max count'),
      isEquippable: this.isEquippable() === 'true',
      isStackable: this.isStackable() === 'true',
      purchaseQuantity: this.requiredInt(this.purchaseQuantity(), 'Purchase quantity'),
      expansionId: this.optionalInt(this.expansionId(), 'Expansion ID'),
      overrideNote: this.optionalString(this.overrideNote()),
    };
    if (this.validationError()) return;

    this.submitted.emit(request);
  }

  private requiredString(value: string, label: string): string {
    const trimmed = value.trim();
    if (trimmed.length === 0) {
      this.validationError.set(`${label} is required.`);
    }
    return trimmed;
  }

  private optionalString(value: string): string | null {
    const trimmed = value.trim();
    return trimmed.length > 0 ? trimmed : null;
  }

  private requiredInt(value: string, label: string): number {
    const parsed = this.optionalInt(value, label);
    if (parsed === null) {
      this.validationError.set(`${label} is required.`);
      return 0;
    }
    return parsed;
  }

  private optionalInt(value: string, label: string): number | null {
    const trimmed = value.trim();
    if (trimmed.length === 0) return null;
    const parsed = Number.parseInt(trimmed, 10);
    if (!Number.isFinite(parsed) || parsed < 0) {
      this.validationError.set(`${label} must be a non-negative number.`);
      return null;
    }
    return parsed;
  }
}
