import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ToastService } from '@core/services/toast.service';

@Component({
  selector: 'app-toast-region',
  template: `
    <div
      class="pointer-events-none fixed right-4 top-4 z-[1200] flex w-[min(24rem,calc(100vw-2rem))] flex-col gap-2"
      aria-live="polite"
      aria-atomic="true"
    >
      @for (toast of toasts.messages(); track toast.id) {
        <div
          class="pointer-events-auto rounded border bg-surface-container-high px-4 py-3 text-sm text-on-surface shadow-xl"
          [class.border-error/40]="toast.tone === 'error'"
          [class.border-primary/40]="toast.tone === 'success'"
          [attr.role]="toast.tone === 'error' ? 'alert' : 'status'"
        >
          <div class="flex items-start gap-3">
            <span
              class="mt-0.5 h-2 w-2 shrink-0 rounded-full"
              [class.bg-error]="toast.tone === 'error'"
              [class.bg-primary]="toast.tone === 'success'"
            ></span>
            <p class="min-w-0 flex-1">{{ toast.message }}</p>
            <button
              type="button"
              class="shrink-0 rounded px-1 text-on-surface-variant transition hover:bg-white/5 hover:text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/60"
              (click)="toasts.dismiss(toast.id)"
              aria-label="Dismiss notification"
            >
              x
            </button>
          </div>
        </div>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ToastRegion {
  protected readonly toasts = inject(ToastService);
}
