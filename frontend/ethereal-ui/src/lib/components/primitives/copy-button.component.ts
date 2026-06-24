import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';

import { SymbolIconComponent } from './symbol-icon.component';

@Component({
  selector: 'ee-copy-button',
  imports: [SymbolIconComponent],
  template: `
    <button
      type="button"
      class="inline-flex h-8 items-center gap-1.5 rounded border border-white/10 px-2.5 ee-label text-outline transition hover:bg-white/5 hover:text-on-surface focus:outline-none focus:ring-2 focus:ring-primary-container disabled:cursor-not-allowed disabled:opacity-50"
      [attr.aria-label]="copied() ? 'Copied' : ariaLabel()"
      [disabled]="!text().trim()"
      (click)="copy()"
    >
      <ee-symbol-icon class="text-[16px]" [name]="copied() ? 'check' : 'content_copy'" />
      {{ copied() ? 'Copied' : label() }}
    </button>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CopyButtonComponent {
  readonly text = input.required<string>();
  readonly label = input('Copy');
  readonly ariaLabel = input('Copy to clipboard');

  protected readonly copied = signal(false);

  private resetHandle: ReturnType<typeof setTimeout> | null = null;

  protected async copy(): Promise<void> {
    const value = this.text().trim();
    if (!value) {
      return;
    }
    try {
      await navigator.clipboard.writeText(value);
      this.copied.set(true);
      if (this.resetHandle) {
        clearTimeout(this.resetHandle);
      }
      this.resetHandle = setTimeout(() => this.copied.set(false), 2000);
    } catch {
      // Clipboard access can fail in unsupported contexts.
    }
  }
}
