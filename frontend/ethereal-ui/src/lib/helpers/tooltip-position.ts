export type TooltipOverlayMode = 'fixed' | 'absolute';

export interface TooltipPointerOffsetOptions {
  readonly mode?: TooltipOverlayMode;
  readonly anchor?: HTMLElement | DOMRect | null;
  readonly mouseOffsetX?: number;
  readonly mouseOffsetY?: number;
  readonly focusOffsetX?: number;
  readonly focusOffsetY?: number;
}

export interface TooltipPointerPosition {
  readonly leftPx: number;
  readonly topPx: number;
  readonly mode: TooltipOverlayMode;
}

export function pointerOffsetFromEvent(
  event: MouseEvent | PointerEvent | FocusEvent,
  options: TooltipPointerOffsetOptions = {},
): TooltipPointerPosition {
  const mode = options.mode ?? 'fixed';
  const mouseOffsetX = options.mouseOffsetX ?? 30;
  const mouseOffsetY = options.mouseOffsetY ?? 0;
  const focusOffsetX = options.focusOffsetX ?? 12;
  const focusOffsetY = options.focusOffsetY ?? 0;
  const anchorRect = resolveAnchorRect(options.anchor);

  if (event instanceof MouseEvent) {
    return relativeToMode(
      event.clientX + mouseOffsetX,
      event.clientY + mouseOffsetY,
      mode,
      anchorRect,
    );
  }

  const el = event.target;
  if (el instanceof HTMLElement) {
    const r = el.getBoundingClientRect();
    return relativeToMode(r.left + r.width + focusOffsetX, r.top + focusOffsetY, mode, anchorRect);
  }

  return relativeToMode(0, 0, mode, anchorRect);
}

function resolveAnchorRect(anchor: HTMLElement | DOMRect | null | undefined): DOMRect | null {
  if (!anchor) {
    return null;
  }
  return anchor instanceof HTMLElement ? anchor.getBoundingClientRect() : anchor;
}

function relativeToMode(
  viewportLeftPx: number,
  viewportTopPx: number,
  mode: TooltipOverlayMode,
  anchorRect: DOMRect | null,
): TooltipPointerPosition {
  if (mode === 'absolute' && anchorRect) {
    return {
      leftPx: viewportLeftPx - anchorRect.left,
      topPx: viewportTopPx - anchorRect.top,
      mode,
    };
  }
  return { leftPx: viewportLeftPx, topPx: viewportTopPx, mode };
}
