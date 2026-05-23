import { Component, computed } from '@angular/core';
import { injectFlexRenderContext } from '@tanstack/angular-table';
import type { CellContext } from '@tanstack/table-core';
import { DatePipe } from '@angular/common';

@Component({
  selector: 'ee-date-column',
  imports: [DatePipe],
  template: `{{ value() | date }}`,
})
export class DateColumnComponent {
  protected readonly ctx = injectFlexRenderContext<CellContext<unknown, unknown>>();
  readonly value = computed(() => {
    return this.ctx.getValue() as string;
  });
}

@Component({
  selector: 'ee-date-time-column',
  imports: [DatePipe],
  template: `{{ value() | date: 'short' }}`,
})
export class DateTimeColumnComponent {
  protected readonly ctx = injectFlexRenderContext<CellContext<unknown, unknown>>();
  readonly value = computed(() => {
    return this.ctx.getValue() as string;
  });
}
