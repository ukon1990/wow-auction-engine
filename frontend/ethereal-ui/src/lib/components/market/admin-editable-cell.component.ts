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

@Component({
  selector: 'ee-admin-editable-cell',
  imports: [A11yModule],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => AdminEditableCellComponent),
      multi: true,
    },
  ],
  template: `
    <label [class]="labelClass()">
      <span class="sr-only">{{ label() }}</span>
      <input
        cdkMonitorElementFocus
        type="number"
        class="w-full border-none bg-transparent p-1 text-center font-space-mono text-sm text-on-surface focus:outline-none disabled:cursor-not-allowed disabled:opacity-50"
        [class.text-primary]="highlighted()"
        [attr.aria-invalid]="invalid()"
        [disabled]="isDisabled()"
        [attr.placeholder]="placeholder()"
        [required]="required()"
        [value]="viewValue()"
        (blur)="markTouched()"
        (input)="updateValue(inputValue($event))"
      />
    </label>
    @if (error()) {
      <span class="mt-1 block ee-data text-error">{{ error() }}</span>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminEditableCellComponent implements ControlValueAccessor {
  readonly label = input.required<string>();
  readonly value = input<number | string>('');
  readonly placeholder = input('---');
  readonly highlighted = input(false);
  readonly disabled = input(false);
  readonly required = input(false);
  readonly invalid = input(false);
  readonly error = input('');
  readonly valueChanged = output<string>();
  protected readonly viewValue = signal<number | string>('');
  protected readonly formDisabled = signal(false);

  private formBound = false;
  private onChange: (value: string) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  constructor() {
    effect(() => {
      const value = this.value();
      if (!this.formBound) {
        this.viewValue.set(value);
      }
    });
  }

  writeValue(value: number | string | null): void {
    this.viewValue.set(value ?? '');
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

  protected inputValue(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected isDisabled(): boolean {
    return this.disabled() || this.formDisabled();
  }

  protected markTouched(): void {
    this.onTouched();
  }

  protected updateValue(value: string): void {
    this.viewValue.set(value);
    this.valueChanged.emit(value);
    this.onChange(value);
  }

  protected labelClass(): string {
    const border = this.invalid()
      ? 'border-error'
      : this.highlighted()
        ? 'border-primary'
        : 'border-white/10';
    return `block rounded border bg-black/50 transition focus-within:border-primary focus-within:shadow-[0_0_15px_rgba(236,185,19,0.45)] ${border}`;
  }
}
