import { Injectable, signal } from '@angular/core';

import {
  pointerOffsetFromEvent,
  TooltipOverlayMode,
  TooltipPointerOffsetOptions,
  TooltipPointerPosition,
} from '../helpers/tooltip-position';

export type TooltipOverlayState = TooltipPointerPosition;

@Injectable({
  providedIn: 'root',
})
export class TooltipOverlayService {
  private autoDismissTimer: ReturnType<typeof setTimeout> | null = null;

  readonly active = signal<TooltipOverlayState | null>(null);

  showAtPointer(
    event: MouseEvent | PointerEvent | FocusEvent,
    options: TooltipPointerOffsetOptions & { readonly autoDismissMs?: number } = {},
  ): void {
    this.clearTimer();
    this.active.set(pointerOffsetFromEvent(event, options));

    if (options.autoDismissMs && options.autoDismissMs > 0) {
      this.autoDismissTimer = setTimeout(() => {
        this.autoDismissTimer = null;
        this.clear();
      }, options.autoDismissMs);
    }
  }

  show(position: TooltipOverlayState): void {
    this.clearTimer();
    this.active.set(position);
  }

  clear(): void {
    this.clearTimer();
    this.active.set(null);
  }

  dismiss(): void {
    this.clear();
  }

  isMode(mode: TooltipOverlayMode): boolean {
    return this.active()?.mode === mode;
  }

  private clearTimer(): void {
    if (this.autoDismissTimer !== null) {
      clearTimeout(this.autoDismissTimer);
      this.autoDismissTimer = null;
    }
  }
}
