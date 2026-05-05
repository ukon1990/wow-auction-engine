import { A11yModule } from '@angular/cdk/a11y';
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
  type ValidationError,
  type WithOptionalFieldTree,
} from '@angular/forms/signals';

export interface SelectInputOption {
  readonly id: string;
  readonly label: string;
  readonly disabled?: boolean;
}

@Component({
  selector: 'ee-select-input',
  imports: [A11yModule],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => SelectInputComponent),
      multi: true,
    },
  ],
  template: `
    @if (!hidden()) {
      <label class="block">
        <span class="mb-2 block ee-label text-outline">
          {{ label() }}
          @if (required()) {
            <span class="text-error">*</span>
          }
        </span>
        <select
          cdkMonitorElementFocus
          [class]="selectClass()"
          [attr.aria-invalid]="ariaInvalid()"
          [attr.name]="name() || null"
          [disabled]="isDisabled()"
          [required]="required()"
          [value]="value()"
          (blur)="onBlur()"
          (change)="onSelectChange(selectValue($event))"
        >
          @if (placeholder()) {
            <option value="" disabled>{{ placeholder() }}</option>
          }
          @for (option of options(); track option.id) {
            <option [value]="option.id" [disabled]="option.disabled === true">
              {{ option.label }}
            </option>
          }
        </select>
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
        @if (hint()) {
          <span class="mt-2 block ee-data text-outline">{{ hint() }}</span>
        }
      </label>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SelectInputComponent implements ControlValueAccessor, FormValueControl<string> {
  readonly value = model('');
  readonly label = input('Select');
  readonly placeholder = input('');
  readonly hint = input('');
  readonly options = input<readonly SelectInputOption[]>([]);
  readonly error = input('');
  readonly valueChanged = output<string>();

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

  protected selectValue(event: Event): string {
    return (event.target as HTMLSelectElement).value;
  }

  protected isDisabled(): boolean {
    return this.disabled() || this.formDisabled();
  }

  protected selectClass(): string {
    const border = this.ariaInvalid()
      ? 'border-error focus:border-error focus:ring-error'
      : 'border-white/10 focus:border-primary focus:ring-primary';
    return `w-full rounded-lg border ${border} bg-surface-container-highest px-4 py-3 font-inter text-sm text-on-surface transition focus:outline-none focus:ring-1 disabled:cursor-not-allowed disabled:opacity-50`;
  }

  protected onBlur(): void {
    this.touched.set(true);
    this.onTouched();
  }

  protected onSelectChange(next: string): void {
    this.value.set(next);
    this.valueChanged.emit(next);
    this.onChange(next);
  }
}
