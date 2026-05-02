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
    <div>
      <div
        [class]="groupClass()"
        role="group"
        [attr.aria-invalid]="invalid()"
        [attr.aria-label]="label()"
        [attr.aria-required]="required()"
      >
        @for (option of options(); track option.id) {
          <button
            type="button"
            [class]="optionClass(option.id)"
            [attr.aria-pressed]="option.id === selectedId()"
            [disabled]="isDisabled()"
            (blur)="markTouched()"
            (click)="selectOption(option.id)"
          >
            {{ option.label }}
          </button>
        }
      </div>
      @if (error()) {
        <span class="mt-2 block ee-data text-error">{{ error() }}</span>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PillToggleComponent implements ControlValueAccessor {
  readonly label = input('View mode');
  readonly options = input.required<readonly PillToggleOption[]>();
  readonly activeId = input.required<string>();
  readonly disabled = input(false);
  readonly required = input(false);
  readonly invalid = input(false);
  readonly error = input('');
  readonly selected = output<string>();
  protected readonly selectedId = signal('');
  protected readonly formDisabled = signal(false);

  private formBound = false;
  private onChange: (value: string) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  constructor() {
    effect(() => {
      const activeId = this.activeId();
      if (!this.formBound) {
        this.selectedId.set(activeId);
      }
    });
  }

  writeValue(value: string | null): void {
    this.selectedId.set(value ?? this.activeId());
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

  protected optionClass(optionId: string): string {
    const base = 'rounded px-3 py-1.5 ee-label transition';
    return optionId === this.selectedId()
      ? `${base} bg-primary text-on-primary ee-arcane-glow`
      : `${base} text-on-surface-variant hover:bg-white/5 hover:text-on-surface`;
  }

  protected isDisabled(): boolean {
    return this.disabled() || this.formDisabled();
  }

  protected groupClass(): string {
    const border = this.invalid() ? 'border-error' : 'border-white/5';
    return `inline-flex rounded-lg border ${border} bg-surface-container-highest p-1`;
  }

  protected markTouched(): void {
    this.onTouched();
  }

  protected selectOption(optionId: string): void {
    if (this.isDisabled()) {
      return;
    }

    this.selectedId.set(optionId);
    this.selected.emit(optionId);
    this.onChange(optionId);
    this.onTouched();
  }
}
