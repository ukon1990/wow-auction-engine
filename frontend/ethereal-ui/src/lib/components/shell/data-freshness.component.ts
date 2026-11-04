import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type DataFreshnessState = 'fresh' | 'stale';

/** Small chip that says how old the price data is. */
@Component({
  selector: 'ee-data-freshness',
  template: `
    <span
      class="inline-flex h-10 items-center gap-2 rounded-md bg-surface-container px-3 text-[13px] text-on-surface-variant"
      [attr.title]="details() || null"
    >
      <span
        class="h-2 w-2 shrink-0 rounded-full"
        [class.bg-status-ok]="state() === 'fresh'"
        [class.bg-primary-container]="state() === 'stale'"
        aria-hidden="true"
      ></span>
      <span class="whitespace-nowrap">
        @if (label()) {
          <span [class.hidden]="compact()" [class.sm:inline]="compact()">{{ label() }} </span>
        }
        <strong class="font-semibold text-on-surface">{{ age() }}</strong>
      </span>
      @if (details()) {
        <span class="sr-only">{{ details() }}</span>
      }
    </span>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DataFreshnessComponent {
  /** e.g. "12 min ago". */
  readonly age = input.required<string>();
  /** e.g. "Updated". Hidden on phones when `compact` is set. */
  readonly label = input('');
  readonly details = input('');
  readonly state = input<DataFreshnessState>('fresh');
  readonly compact = input(true);
}
