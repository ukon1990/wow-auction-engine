import { pointerOffsetFromEvent } from './tooltip-position';

describe('tooltip position', () => {
  it('offsets mouse events in fixed viewport coordinates', () => {
    const event = new MouseEvent('mousemove', { clientX: 10, clientY: 20 });

    expect(pointerOffsetFromEvent(event)).toEqual({
      leftPx: 40,
      topPx: 20,
      mode: 'fixed',
    });
  });

  it('offsets mouse events relative to an absolute anchor', () => {
    const event = new MouseEvent('mousemove', { clientX: 50, clientY: 80 });
    const anchor = new DOMRect(10, 20, 100, 100);

    expect(
      pointerOffsetFromEvent(event, {
        mode: 'absolute',
        anchor,
        mouseOffsetX: 5,
        mouseOffsetY: 7,
      }),
    ).toEqual({
      leftPx: 45,
      topPx: 67,
      mode: 'absolute',
    });
  });
});
