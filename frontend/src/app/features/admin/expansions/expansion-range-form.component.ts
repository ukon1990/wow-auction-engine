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
  AdminExpansionItemRange,
  AdminExpansionItemRangeRequest,
} from '@api/generated';
import { type CreateExpansionRangeDefaults } from '@features/admin/expansions/expansion-range-filters';
import { CheckboxInputComponent, SelectInputComponent, TextInputComponent } from '@ui';

export type ExpansionRangeFormMode = 'create' | 'edit';

const standaloneModel = { standalone: true };

@Component({
  selector: 'app-expansion-range-form',
  imports: [FormsModule, SelectInputComponent, TextInputComponent, CheckboxInputComponent],
  template: `
    <form class="grid gap-4" (submit)="onSubmit($event)">
      <ee-select-input
        label="Expansion"
        placeholder="Select expansion"
        [required]="true"
        [options]="expansionOptions()"
        [ngModel]="expansionId()"
        [ngModelOptions]="standaloneModel"
        (ngModelChange)="expansionId.set($event)"
      />

      <div class="grid gap-4 md:grid-cols-2">
        <ee-text-input
          label="Start item ID"
          type="number"
          [required]="true"
          [ngModel]="startItemId()"
          [ngModelOptions]="standaloneModel"
          (ngModelChange)="startItemId.set($event)"
        />
        <ee-text-input
          label="End item ID"
          type="number"
          [required]="true"
          [ngModel]="endItemId()"
          [ngModelOptions]="standaloneModel"
          (ngModelChange)="endItemId.set($event)"
        />
      </div>

      <ee-checkbox-input
        label="Enabled"
        [ngModel]="enabled()"
        [ngModelOptions]="standaloneModel"
        (ngModelChange)="enabled.set($event)"
      />

      <ee-text-input
        label="Note"
        [ngModel]="note()"
        [ngModelOptions]="standaloneModel"
        (ngModelChange)="note.set($event)"
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
export class ExpansionRangeFormComponent {
  protected readonly standaloneModel = standaloneModel;

  readonly expansions = input.required<readonly AdminExpansion[]>();
  readonly createDefaults = input<CreateExpansionRangeDefaults>({
    expansionId: '',
    startItemId: '',
  });
  readonly mode = input<ExpansionRangeFormMode>('create');
  readonly range = input<AdminExpansionItemRange | null>(null);
  readonly submitting = input(false);
  readonly submitError = input<string | null>(null);

  readonly submitted = output<AdminExpansionItemRangeRequest>();
  readonly cancelled = output<void>();

  protected readonly expansionId = signal('');
  protected readonly startItemId = signal('');
  protected readonly endItemId = signal('');
  protected readonly enabled = signal(true);
  protected readonly note = signal('');
  protected readonly validationError = signal<string | null>(null);

  protected readonly expansionOptions = computed(() =>
    this.expansions().map((expansion) => ({
      id: String(expansion.id),
      label: expansion.name,
    })),
  );

  protected readonly submitLabel = computed(() =>
    this.mode() === 'create' ? 'Create range' : 'Save changes',
  );

  constructor() {
    effect(() => {
      const range = this.range();
      if (this.mode() === 'edit' && range) {
        this.expansionId.set(String(range.expansion.id));
        this.startItemId.set(String(range.startItemId));
        this.endItemId.set(String(range.endItemId));
        this.enabled.set(range.enabled);
        this.note.set(range.note ?? '');
      } else if (this.mode() === 'create') {
        const defaults = this.createDefaults();
        this.expansionId.set(defaults.expansionId);
        this.startItemId.set(defaults.startItemId);
        this.endItemId.set('');
        this.enabled.set(true);
        this.note.set('');
      }
      this.validationError.set(null);
    });
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();
    this.validationError.set(null);

    const expansionId = Number.parseInt(this.expansionId(), 10);
    const startItemId = Number.parseInt(this.startItemId(), 10);
    const endItemId = Number.parseInt(this.endItemId(), 10);

    if (!Number.isFinite(expansionId)) {
      this.validationError.set('Expansion is required.');
      return;
    }
    if (!Number.isFinite(startItemId) || startItemId < 1) {
      this.validationError.set('Start item ID must be at least 1.');
      return;
    }
    if (!Number.isFinite(endItemId) || endItemId < 1) {
      this.validationError.set('End item ID must be at least 1.');
      return;
    }
    if (startItemId > endItemId) {
      this.validationError.set('Start item ID must be less than or equal to end item ID.');
      return;
    }

    const trimmedNote = this.note().trim();
    this.submitted.emit({
      expansionId,
      startItemId,
      endItemId,
      source: this.mode() === 'edit' ? (this.range()?.source ?? 'manual') : 'manual',
      enabled: this.enabled(),
      note: trimmedNote.length > 0 ? trimmedNote : null,
    });
  }
}
