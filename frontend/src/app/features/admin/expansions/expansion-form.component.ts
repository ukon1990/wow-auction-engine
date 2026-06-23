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
import { AdminExpansion, AdminExpansionRequest } from '@api/generated';
import { emptyGameLocale, hasEnglishGameLocale } from '@features/admin/shared/game-locale-fields';
import { LocaleFieldsComponent } from '@features/admin/shared/locale-fields.component';
import { TextInputComponent } from '@ui';

export type ExpansionFormMode = 'create' | 'edit';

const standaloneModel = { standalone: true };

@Component({
  selector: 'app-expansion-form',
  imports: [FormsModule, TextInputComponent, LocaleFieldsComponent],
  template: `
    <form class="grid gap-4" (submit)="onSubmit($event)">
      @if (mode() === 'create') {
        <ee-text-input
          label="ID"
          type="number"
          [required]="true"
          [ngModel]="id()"
          [ngModelOptions]="standaloneModel"
          (ngModelChange)="id.set($event)"
        />
      }

      <ee-text-input
        label="Slug"
        [required]="true"
        [ngModel]="slug()"
        [ngModelOptions]="standaloneModel"
        (ngModelChange)="slug.set($event)"
      />

      <div class="grid gap-4 md:grid-cols-2">
        <ee-text-input
          label="Major version"
          type="number"
          [required]="true"
          [ngModel]="majorVersion()"
          [ngModelOptions]="standaloneModel"
          (ngModelChange)="majorVersion.set($event)"
        />
        <ee-text-input
          label="Display order"
          type="number"
          [required]="true"
          [ngModel]="displayOrder()"
          [ngModelOptions]="standaloneModel"
          (ngModelChange)="displayOrder.set($event)"
        />
      </div>

      <app-locale-fields [value]="nameLocales()" (valueChange)="nameLocales.set($event)" />

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
export class ExpansionFormComponent {
  protected readonly standaloneModel = standaloneModel;

  readonly mode = input<ExpansionFormMode>('create');
  readonly expansion = input<AdminExpansion | null>(null);
  readonly defaultId = input(1);
  readonly defaultDisplayOrder = input(10);
  readonly submitting = input(false);
  readonly submitError = input<string | null>(null);

  readonly submitted = output<AdminExpansionRequest>();
  readonly cancelled = output<void>();

  protected readonly id = signal('');
  protected readonly slug = signal('');
  protected readonly majorVersion = signal('');
  protected readonly displayOrder = signal('');
  protected readonly nameLocales = signal(emptyGameLocale());
  protected readonly validationError = signal<string | null>(null);

  protected readonly submitLabel = computed(() =>
    this.mode() === 'create' ? 'Create expansion' : 'Save changes',
  );

  constructor() {
    effect(() => {
      const expansion = this.expansion();
      if (this.mode() === 'edit' && expansion) {
        this.id.set(String(expansion.id));
        this.slug.set(expansion.slug);
        this.majorVersion.set(String(expansion.majorVersion));
        this.displayOrder.set(String(expansion.displayOrder));
        this.nameLocales.set({ ...expansion.nameLocales });
      } else if (this.mode() === 'create') {
        this.id.set(String(this.defaultId()));
        this.slug.set('');
        this.majorVersion.set('');
        this.displayOrder.set(String(this.defaultDisplayOrder()));
        this.nameLocales.set(emptyGameLocale());
      }
      this.validationError.set(null);
    });
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();
    this.validationError.set(null);

    const id = Number.parseInt(this.id(), 10);
    const majorVersion = Number.parseInt(this.majorVersion(), 10);
    const displayOrder = Number.parseInt(this.displayOrder(), 10);
    const slug = this.slug().trim();
    const nameLocales = this.nameLocales();

    if (!Number.isFinite(id) || id < 1) {
      this.validationError.set('ID must be at least 1.');
      return;
    }
    if (slug.length === 0) {
      this.validationError.set('Slug is required.');
      return;
    }
    if (!Number.isFinite(majorVersion) || majorVersion < 1) {
      this.validationError.set('Major version must be at least 1.');
      return;
    }
    if (!Number.isFinite(displayOrder) || displayOrder < 0) {
      this.validationError.set('Display order must be zero or greater.');
      return;
    }
    if (!hasEnglishGameLocale(nameLocales)) {
      this.validationError.set('At least one English translation (US or GB) is required.');
      return;
    }

    this.submitted.emit({
      id,
      slug,
      majorVersion,
      displayOrder,
      nameLocales,
    });
  }
}
