import { computed, Directive, input } from '@angular/core';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
export type ButtonSize = 'md' | 'sm';

const BASE =
  'inline-flex shrink-0 items-center justify-center gap-2 rounded-md border font-semibold transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-45';

const VARIANTS: Record<ButtonVariant, string> = {
  primary: 'border-primary bg-primary text-on-primary hover:bg-primary-container',
  secondary: 'border-white/10 bg-surface-container text-on-surface hover:bg-surface-container-high',
  ghost:
    'border-transparent bg-transparent text-on-surface-variant hover:bg-white/5 hover:text-on-surface',
  danger: 'border-error/40 bg-transparent text-error hover:bg-error/10',
};

const SIZES: Record<ButtonSize, string> = {
  md: 'h-10 px-4 text-sm',
  sm: 'h-8 px-3 text-[13px]',
};

/** Shared button styles: `<button eeButton variant="secondary">`. */
@Directive({
  selector: 'button[eeButton], a[eeButton]',
  host: { '[class]': 'classes()' },
})
export class ButtonDirective {
  readonly variant = input<ButtonVariant>('secondary');
  readonly size = input<ButtonSize>('md');

  protected readonly classes = computed(
    () => `${BASE} ${VARIANTS[this.variant()]} ${SIZES[this.size()]}`,
  );
}
