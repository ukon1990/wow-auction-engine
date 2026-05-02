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

import { SymbolIconComponent } from './symbol-icon.component';

@Component({
  selector: 'ee-search-input',
  imports: [A11yModule, SymbolIconComponent],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => SearchInputComponent),
      multi: true,
    },
  ],
  template: `
    <label class="relative block">
      <span class="sr-only">{{ label() }}</span>
      <ee-symbol-icon
        class="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-xl text-primary"
        name="search"
      />
      <input
        cdkMonitorElementFocus
        type="search"
        [class]="inputClass()"
        [attr.aria-invalid]="invalid()"
        [attr.placeholder]="placeholder()"
        [disabled]="isDisabled()"
        [required]="required()"
        [value]="viewValue()"
        (blur)="markTouched()"
        (input)="updateValue(inputValue($event))"
      />
      @if (error()) {
        <span class="mt-2 block ee-data text-error">{{ error() }}</span>
      }
    </label>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SearchInputComponent implements ControlValueAccessor {
  readonly label = input('Search');
  readonly placeholder = input('Search items, reagents, or recipes...');
  readonly value = input('');
  readonly disabled = input(false);
  readonly required = input(false);
  readonly invalid = input(false);
  readonly error = input('');
  readonly valueChanged = output<string>();
  protected readonly viewValue = signal('');
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

  writeValue(value: string | null): void {
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

  protected inputClass(): string {
    const border = this.invalid()
      ? 'border-error focus:border-error focus:ring-error'
      : 'border-white/10 focus:border-primary focus:ring-primary';
    return `w-full rounded-lg border ${border} bg-surface-container-highest px-4 py-3 pl-12 font-inter text-sm text-on-surface placeholder:text-outline transition focus:outline-none focus:ring-1 disabled:cursor-not-allowed disabled:opacity-50`;
  }

  protected markTouched(): void {
    this.onTouched();
  }

  protected updateValue(value: string): void {
    this.viewValue.set(value);
    this.valueChanged.emit(value);
    this.onChange(value);
  }
}
