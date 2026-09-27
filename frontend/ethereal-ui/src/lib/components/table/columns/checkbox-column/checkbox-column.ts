import { Component, computed } from '@angular/core';
import { CheckboxInputComponent } from '@ui';
import { injectFlexRenderContext } from '@tanstack/angular-table';
import type { CellContext } from '@tanstack/table-core';

@Component({
  selector: 'ee-checkbox-column',
  imports: [CheckboxInputComponent],
  template: ` <input type="checkbox" [value]="value()" readonly /> `,
})
export class CheckboxColumn {
  protected readonly ctx = injectFlexRenderContext<CellContext<unknown, unknown>>();
  readonly value = computed(() => {
    return this.ctx.getValue() as string;
  });
}
