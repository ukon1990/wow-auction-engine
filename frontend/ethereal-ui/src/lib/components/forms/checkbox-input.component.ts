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
  type FormCheckboxControl,
  type ValidationError,
  type WithOptionalFieldTree,
} from '@angular/forms/signals';

@Component({
  selector: 'ee-checkbox-input',
  imports: [A11yModule],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => CheckboxInputComponent),
      multi: true,
    },
  ],
  template: `
    @if (!hidden()) {
      <div>
        <label [class]="labelClass()">
          <input
            cdkMonitorElementFocus
            type="checkbox"
            class="mt-0.5 h-4 w-4 rounded border-white/20 bg-surface text-primary accent-primary"
            [attr.aria-invalid]="ariaInvalid()"
            [checked]="checked()"
            [disabled]="isDisabled()"
            [required]="required()"
            (blur)="onBlur()"
            (change)="onChangeBox(checkboxValue($event))"
          />
          <span>
            <span class="block ee-label text-on-surface">
              {{ label() }}
              @if (required()) {
                <span class="text-error">*</span>
              }
            </span>
            @if (hint()) {
              <span class="mt-1 block ee-data text-outline">{{ hint() }}</span>
            }
          </span>
        </label>
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
export class CheckboxInputComponent implements ControlValueAccessor, FormCheckboxControl {
  readonly checked = model(false);
  readonly label = input('Checkbox');
  readonly hint = input('');
  readonly error = input('');
  readonly checkedChanged = output<boolean>();

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

  private onChange: (value: boolean) => void = () => undefined;
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

  writeValue(v: boolean | null): void {
    this.checked.set(v === true);
  }

  registerOnChange(fn: (value: boolean) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.formDisabled.set(isDisabled);
  }

  protected checkboxValue(event: Event): boolean {
    return (event.target as HTMLInputElement).checked;
  }

  protected isDisabled(): boolean {
    return this.disabled() || this.formDisabled();
  }

  protected labelClass(): string {
    const border = this.ariaInvalid()
      ? 'border-error hover:border-error'
      : 'border-white/10 hover:border-primary/40';
    return `flex cursor-pointer items-start gap-3 rounded-lg border ${border} bg-surface-container-highest p-3 transition has-[:disabled]:cursor-not-allowed has-[:disabled]:opacity-50`;
  }

  protected onBlur(): void {
    this.touched.set(true);
    this.onTouched();
  }

  protected onChangeBox(next: boolean): void {
    this.checked.set(next);
    this.checkedChanged.emit(next);
    this.onChange(next);
  }
}
