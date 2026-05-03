import {
  ChangeDetectionStrategy,
  Component,
  computed,
  forwardRef,
  input,
  model,
  output,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import {
  type DisabledReason,
  type FormValueControl,
  ValidationError,
  type WithOptionalFieldTree,
} from '@angular/forms/signals';

export interface PillToggleOption {
  readonly id: string;
  readonly label: string;
}

@Component({
  selector: 'ee-pill-toggle',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => PillToggleComponent),
      multi: true,
    },
  ],
  template: `
    @if (!hidden()) {
      <div>
        <div
          [class]="groupClass()"
          role="group"
          [attr.aria-invalid]="ariaInvalid()"
          [attr.aria-label]="label()"
          [attr.aria-required]="required()"
        >
          @for (option of options(); track option.id) {
            <button
              type="button"
              [class]="optionClass(option.id)"
              [attr.aria-pressed]="option.id === value()"
              [disabled]="isDisabled()"
              (blur)="onBlur()"
              (click)="selectOption(option.id)"
            >
              {{ option.label }}
            </button>
          }
        </div>
        @for (msg of errorLines(); track $index) {
          <span class="mt-2 block ee-data text-error" role="alert">{{ msg }}</span>
        }
        @if (disabled() && disabledReasons().length > 0) {
          <span class="mt-2 block ee-data text-outline">
            @for (reason of disabledReasons(); track $index) {
              <span>{{ reason.message }}</span>
            }
          </span>
        }
      </div>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PillToggleComponent implements ControlValueAccessor, FormValueControl<string> {
  readonly value = model('');
  readonly label = input('View mode');
  readonly options = input.required<readonly PillToggleOption[]>();
  readonly error = input('');
  readonly selected = output<string>();

  readonly errors = input<readonly ValidationError.WithOptionalFieldTree[]>([]);
  readonly disabled = input(false);
  readonly disabledReasons = input<readonly WithOptionalFieldTree<DisabledReason>[]>([]);
  readonly readonly = input(false);
  readonly hidden = input(false);
  readonly invalid = input(false);
  readonly pending = input(false);
  readonly touched = model(false);
  readonly dirty = input(false);
  readonly name = input('');
  readonly required = input(false);
  readonly min = input<number | undefined>(undefined);
  readonly max = input<number | undefined>(undefined);
  readonly minLength = input<number | undefined>(undefined);
  readonly maxLength = input<number | undefined>(undefined);
  readonly pattern = input<readonly RegExp[]>([]);

  protected readonly formDisabled = signal(false);

  private onChange: (value: string) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  readonly ariaInvalid = computed(
    () => this.invalid() || this.errors().length > 0 || this.error().length > 0,
  );

  readonly errorLines = computed(() => {
    const fieldErrors = this.errors();
    if (fieldErrors.length > 0) {
      return fieldErrors.map((e) => e.message).filter((m): m is string => !!m);
    }
    const manual = this.error();
    return manual ? [manual] : [];
  });

  writeValue(v: string | null): void {
    this.value.set(v ?? '');
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.formDisabled.set(isDisabled);
  }

  protected optionClass(optionId: string): string {
    const base = 'rounded px-3 py-1.5 ee-label transition';
    return optionId === this.value()
      ? `${base} bg-primary text-on-primary ee-arcane-glow`
      : `${base} text-on-surface-variant hover:bg-white/5 hover:text-on-surface`;
  }

  protected isDisabled(): boolean {
    return this.disabled() || this.formDisabled();
  }

  protected groupClass(): string {
    const border = this.ariaInvalid() ? 'border-error' : 'border-white/5';
    return `inline-flex rounded-lg border ${border} bg-surface-container-highest p-1`;
  }

  protected onBlur(): void {
    this.touched.set(true);
    this.onTouched();
  }

  protected selectOption(optionId: string): void {
    if (this.isDisabled() || this.readonly()) {
      return;
    }

    this.value.set(optionId);
    this.selected.emit(optionId);
    this.onChange(optionId);
    this.onTouched();
  }
}
