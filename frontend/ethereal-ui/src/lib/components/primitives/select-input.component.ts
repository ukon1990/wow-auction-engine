import { A11yModule } from '@angular/cdk/a11y';
import {
  ChangeDetectionStrategy,
  Component,
  effect,
  forwardRef,
  input,
  output,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

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
        [attr.aria-invalid]="invalid()"
        [disabled]="isDisabled()"
        [required]="required()"
        [value]="selectedValue()"
        (blur)="markTouched()"
        (change)="updateValue(selectValue($event))"
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
      @if (error()) {
        <span class="mt-2 block ee-data text-error">{{ error() }}</span>
      }
      @if (hint()) {
        <span class="mt-2 block ee-data text-outline">{{ hint() }}</span>
      }
    </label>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SelectInputComponent implements ControlValueAccessor {
  readonly label = input('Select');
  readonly placeholder = input('');
  readonly hint = input('');
  readonly options = input<readonly SelectInputOption[]>([]);
  readonly value = input('');
  readonly disabled = input(false);
  readonly required = input(false);
  readonly invalid = input(false);
  readonly error = input('');
  readonly valueChanged = output<string>();
  protected readonly selectedValue = signal('');
  protected readonly formDisabled = signal(false);

  private formBound = false;
  private onChange: (value: string) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  constructor() {
    effect(() => {
      const value = this.value();
      if (!this.formBound) {
        this.selectedValue.set(value);
      }
    });
  }

  writeValue(value: string | null): void {
    this.selectedValue.set(value ?? '');
  }

  registerOnChange(onChange: (value: string) => void): void {
    this.formBound = true;
    this.onChange = onChange;
  }

  registerOnTouched(onTouched: () => void): void {
    this.onTouched = onTouched;
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
    const border = this.invalid()
      ? 'border-error focus:border-error focus:ring-error'
      : 'border-white/10 focus:border-primary focus:ring-primary';
    return `w-full rounded-lg border ${border} bg-surface-container-highest px-4 py-3 font-inter text-sm text-on-surface transition focus:outline-none focus:ring-1 disabled:cursor-not-allowed disabled:opacity-50`;
  }

  protected markTouched(): void {
    this.onTouched();
  }

  protected updateValue(value: string): void {
    this.selectedValue.set(value);
    this.valueChanged.emit(value);
    this.onChange(value);
  }
}
