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

export type TextInputType = 'text' | 'email' | 'password' | 'number' | 'search' | 'url';

@Component({
  selector: 'ee-text-input',
  imports: [A11yModule],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => TextInputComponent),
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
        <input
          cdkMonitorElementFocus
          [type]="type()"
          [class]="inputClass()"
          [attr.aria-invalid]="ariaInvalid()"
          [attr.placeholder]="placeholder()"
          [attr.name]="name() || null"
          [disabled]="isDisabled()"
          [readonly]="readonly()"
          [required]="required()"
          [value]="value()"
          (blur)="onBlur()"
          (input)="onInput(inputValue($event))"
        />
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
export class TextInputComponent implements ControlValueAccessor, FormValueControl<string> {
  readonly value = model('');
  readonly label = input('Label');
  readonly placeholder = input('');
  readonly hint = input('');
  readonly type = input<TextInputType>('text');
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

  protected inputValue(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected isDisabled(): boolean {
    return this.disabled() || this.formDisabled();
  }

  protected inputClass(): string {
    const border = this.ariaInvalid()
      ? 'border-error focus:border-error focus:ring-error'
      : 'border-white/10';
    return `w-full rounded-lg border ${border} bg-surface-container-highest px-4 py-3 font-inter text-sm text-on-surface placeholder:text-outline transition focus:outline-none focus:ring-1 disabled:cursor-not-allowed disabled:opacity-50`;
  }

  protected onBlur(): void {
    this.touched.set(true);
    this.onTouched();
  }

  protected onInput(next: string): void {
    this.value.set(next);
    this.valueChanged.emit(next);
    this.onChange(next);
  }
}
