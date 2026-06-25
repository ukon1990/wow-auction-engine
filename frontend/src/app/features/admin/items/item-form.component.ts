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
  AdminItem,
  AdminItemApiCompareResponse,
  AdminItemCreateRequest,
  AdminItemOverrideRequest,
} from '@api/generated';
import { emptyGameLocale, hasEnglishGameLocale } from '@features/admin/shared/game-locale-fields';
import { LocaleFieldsComponent } from '@features/admin/shared/locale-fields.component';
import { TextInputComponent } from '@ui';

export type ItemFormMode = 'create' | 'edit';

const standaloneModel = { standalone: true };

@Component({
  selector: 'app-item-form',
  imports: [FormsModule, TextInputComponent, LocaleFieldsComponent],
  template: `
    <form class="grid gap-4" (submit)="onSubmit($event)">
      @if (mode() === 'create') {
        <ee-text-input
          label="Item ID"
          type="number"
          [required]="true"
          [ngModel]="itemId()"
          [ngModelOptions]="standaloneModel"
          (ngModelChange)="itemId.set($event)"
        />
      } @else {
        <p class="ee-data text-outline">Editing item {{ item()?.id }}</p>
      }

      <app-locale-fields [value]="nameLocales()" (valueChange)="nameLocales.set($event)" />

      <div class="grid gap-4 md:grid-cols-2">
        <ee-text-input
          label="Level"
          type="number"
          [ngModel]="level()"
          [ngModelOptions]="standaloneModel"
          (ngModelChange)="level.set($event)"
        />
        <ee-text-input
          label="Required level"
          type="number"
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
        <ee-text-input
          label="Quality ID"
          type="number"
          [ngModel]="qualityId()"
          [ngModelOptions]="standaloneModel"
          (ngModelChange)="qualityId.set($event)"
        />
      </div>

      <ee-text-input
        label="Media URL"
        [ngModel]="mediaUrl()"
        [ngModelOptions]="standaloneModel"
        (ngModelChange)="mediaUrl.set($event)"
      />

      <ee-text-input
        label="Override note"
        [ngModel]="overrideNote()"
        [ngModelOptions]="standaloneModel"
        (ngModelChange)="overrideNote.set($event)"
      />

      @if (mode() === 'edit' && item()) {
        <p class="rounded-md border border-white/10 bg-white/5 px-3 py-2 text-sm text-outline">
          Leave fields blank to inherit from the Blizzard base row. Only changed values are stored on
          the override.
        </p>
      }

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
          {{ submitLabel() }}
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
export class ItemFormComponent {
  protected readonly standaloneModel = standaloneModel;

  readonly mode = input.required<ItemFormMode>();
  readonly item = input<AdminItem | null>(null);
  readonly submitting = input(false);
  readonly submitError = input<string | null>(null);

  readonly submitted = output<AdminItemCreateRequest | AdminItemOverrideRequest>();
  readonly cancelled = output<void>();

  protected readonly itemId = signal('');
  protected readonly nameLocales = signal(emptyGameLocale());
  protected readonly level = signal('');
  protected readonly requiredLevel = signal('');
  protected readonly expansionId = signal('');
  protected readonly qualityId = signal('');
  protected readonly mediaUrl = signal('');
  protected readonly overrideNote = signal('');

  protected readonly validationError = signal<string | null>(null);
  protected readonly submitLabel = computed(() =>
    this.mode() === 'create' ? 'Create manual item' : 'Save override',
  );

  constructor() {
    effect(() => {
      const current = this.item();
      if (!current) {
        return;
      }
      this.nameLocales.set(current.nameLocales ?? emptyGameLocale());
      this.level.set(current.level?.toString() ?? '');
      this.requiredLevel.set(current.requiredLevel?.toString() ?? '');
      this.expansionId.set(current.expansionId?.toString() ?? '');
      this.qualityId.set(current.qualityId?.toString() ?? '');
      this.mediaUrl.set(current.mediaUrl ?? '');
      this.overrideNote.set(current.overrideNote ?? '');
    });
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();
    this.validationError.set(null);

    if (this.mode() === 'create' && !hasEnglishGameLocale(this.nameLocales())) {
      this.validationError.set('At least one English name (en_US or en_GB) is required.');
      return;
    }

    const request = this.buildRequest();
    this.submitted.emit(request);
  }

  private buildRequest(): AdminItemCreateRequest | AdminItemOverrideRequest {
    const body: AdminItemOverrideRequest = {
      nameLocales: hasEnglishGameLocale(this.nameLocales()) ? this.nameLocales() : undefined,
      level: parseOptionalInt(this.level()),
      requiredLevel: parseOptionalInt(this.requiredLevel()),
      expansionId: parseOptionalInt(this.expansionId()),
      qualityId: parseOptionalInt(this.qualityId()),
      mediaUrl: this.mediaUrl().trim() || undefined,
      overrideNote: this.overrideNote().trim() || undefined,
    };

    if (this.mode() === 'create') {
      return {
        id: Number(this.itemId()),
        ...body,
        nameLocales: this.nameLocales(),
        level: body.level ?? 1,
        requiredLevel: body.requiredLevel ?? 1,
        maxCount: 0,
        purchasePrice: 0,
        purchaseQuantity: 1,
        sellPrice: 0,
        isEquippable: false,
        isStackable: true,
      } satisfies AdminItemCreateRequest;
    }

    return body;
  }
}

function parseOptionalInt(value: string): number | undefined {
  const trimmed = value.trim();
  if (!trimmed) {
    return undefined;
  }
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : undefined;
}
